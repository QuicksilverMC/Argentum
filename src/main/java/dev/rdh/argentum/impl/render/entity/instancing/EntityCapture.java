package dev.rdh.argentum.impl.render.entity.instancing;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import dev.rdh.argentum.impl.render.instancing.BoxTemplate;
import dev.rdh.argentum.mixin.features.model.instancing.ModelPartAccessor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class EntityCapture implements AutoCloseable {
    private static final Identifier ENCHANTMENT_GLINT_TEXTURE =
            new Identifier("textures/misc/enchanted_item_glint.png");

    private final EntityInstancing owner;
    private final Matrix4f arrowMatrix = new Matrix4f();
    private final Matrix4f boxMatrix = new Matrix4f();
    private final ReferenceOpenHashSet<ModelPart> glintParts = new ReferenceOpenHashSet<>();

    private final Matrix4fStack matrices = new Matrix4fStack(64);
    private EntityCapture previous;
    private ModelGeometry model;
    private Identifier entityTexture;
    private Identifier boundTexture;
    private InstanceRenderPass pass;
    private boolean item;
    private boolean modelActive;
    private boolean recorded;
    private boolean player;
    private boolean suppressFixedFunction;
    private boolean armorLayer;
    private boolean finished;
    private boolean modelPassSeen;
    private boolean itemPassSeen;
    private boolean itemInstanced;
    private boolean released;
    private int matrixMode;
    private int packedLight;
    private int itemGlintPass;
    private boolean glintActive;
    private final Vector4f color = new Vector4f();
    private float effectTime;
    private final Vector4f overlayColor = new Vector4f();
    private final Vector4f currentOverlayColor = new Vector4f();

    EntityCapture(EntityInstancing owner) {
        this.owner = owner;
    }

    public static EntityCapture current() {
        EntityInstancing instancing = EntityInstancing.current();
        return instancing == null ? null : instancing.activeCapture();
    }

    void open(EntityCapture previous) {
        this.previous = previous;
        this.finished = false;
        this.released = false;
    }

    void beginEntity(Model model, Identifier texture, boolean player, boolean preserveFixedFunction,
            int packedLight, float effectTime, float overlayRed, float overlayGreen, float overlayBlue,
            float overlayAlpha) {
        this.item = false;
        this.model = this.owner.backend().model(model);
        this.entityTexture = texture;
        this.boundTexture = texture;
        this.pass = player ? InstanceRenderPass.TRANSLUCENT : InstanceRenderPass.NORMAL;
        this.player = player;
        this.suppressFixedFunction = !preserveFixedFunction;
        this.packedLight = packedLight;
        this.effectTime = effectTime;
        this.overlayColor.set(overlayRed, overlayGreen, overlayBlue, overlayAlpha);
        this.resetState();
    }

    void beginBlockEntity(InstanceRenderPass pass, int packedLight) {
        this.item = false;
        this.model = null;
        this.entityTexture = null;
        this.boundTexture = null;
        this.pass = pass;
        this.player = false;
        this.suppressFixedFunction = true;
        this.packedLight = packedLight;
        this.effectTime = 0.0F;
        this.overlayColor.set(0);
        this.resetState();
        this.modelActive = true;
    }

    void beginItem(ItemEntity entity, int packedLight) {
        this.item = true;
        this.model = null;
        this.entityTexture = null;
        this.boundTexture = TextureAtlas.BLOCKS_LOCATION;
        this.pass = InstanceRenderPass.ITEM;
        this.player = false;
        this.suppressFixedFunction = true;
        this.packedLight = packedLight;
        this.effectTime = entity.getAge();
        this.overlayColor.set(0);
        this.resetState();
        this.modelActive = true;
    }

    private void resetState() {
        this.matrices.clear();
        this.matrices.pushMatrix();
        this.matrixMode = GL11.GL_MODELVIEW;
        this.recorded = false;
        this.modelActive = false;
        this.armorLayer = false;
        this.modelPassSeen = false;
        this.itemPassSeen = false;
        this.itemInstanced = false;
        this.itemGlintPass = 0;
        this.glintActive = false;
        this.color.set(1);
        this.currentOverlayColor.set(this.overlayColor);
    }

    public void setOverlayColor(float red, float green, float blue, float alpha) {
        this.overlayColor.set(red, green, blue, alpha);
        this.currentOverlayColor.set(this.overlayColor);
    }

    public boolean firstModelPass() {
        if (this.modelPassSeen) {
            return false;
        }
        this.modelPassSeen = true;
        return true;
    }

    public void beginModel() {
        if (this.finished || this.model == null) {
            return;
        }
        this.boundTexture = this.entityTexture;
        this.model.begin();
        this.modelActive = true;
        this.currentOverlayColor.set(this.overlayColor);
    }

    public void endModel() {
        this.modelActive = false;
    }

    public boolean beginLayer(Object layer, LivingEntity entity) {
        InstanceRenderPass layerPass = EntityLayerSupport.pass(layer, entity);
        if (this.finished || layerPass == null) {
            return false;
        }
        this.pass = layerPass;
        this.armorLayer = layer instanceof AbstractArmorLayer;
        this.currentOverlayColor.set(this.overlayColor);
        if (!(layer instanceof EntityRenderLayer<?> renderLayer) || !renderLayer.colorsWhenDamaged()) {
            this.currentOverlayColor.w = 0.0F;
        }
        this.glintParts.clear();
        this.itemGlintPass = 0;
        this.model.begin();
        this.modelActive = true;
        this.color.set(1);
        return true;
    }

    public void endLayer() {
        this.modelActive = false;
        this.pass = InstanceRenderPass.NORMAL;
        this.armorLayer = false;
        this.color.set(1);
    }

    public boolean deferNameTag(LivingEntityRenderer<?> renderer, LivingEntity entity,
            double x, double y, double z) {
        if (this.finished || !this.recorded) {
            return false;
        }
        this.owner.nameTags().add(renderer, entity, x, y, z);
        return true;
    }

    public boolean record(ModelPart part, float scale) {
        if (!this.modelActive) {
            return false;
        }
        this.recordPart(part, scale);
        return true;
    }

    public boolean recordItem(BakedModel model, ItemStack item, int color) {
        if (!this.modelActive) {
            return false;
        }
        InstanceRenderPass previousPass = this.pass;
        InstanceGeometry geometry;
        if (item != null && color == -1) {
            if (this.itemPassSeen) {
                // a mod is drawing the item again with a different subset of its layers (old animations keeps the
                // glint on the base layer that way). our geometry is the whole model, so both passes draw everything
                EntityInstancing.noteItemLayerPass();
            }
            this.itemPassSeen = true;
            if (EntityInstancing.itemLayerPassDetected() && this.owner.backend().isLayeredItem(model)) {
                this.itemInstanced = false;
                return false;
            }
            this.pass = InstanceRenderPass.ITEM;
            geometry = this.owner.backend().item(model, item);
            // the glint is depth-tested against the item with GL_EQUAL, so it can only be instanced if the item
            // was: a vanilla-drawn item and an instanced glint do not produce bit-identical depth
            this.itemInstanced = geometry != null;
        } else if (item == null && this.glintActive && this.itemInstanced && this.itemGlintPass < 2) {
            int glintPass = this.itemGlintPass++;
            this.pass = glintPass == 0 ? InstanceRenderPass.ITEM_GLINT_0 : InstanceRenderPass.ITEM_GLINT_1;
            this.owner.backend().captureItemGlintMatrix(glintPass);
            geometry = this.owner.backend().fixedItem(model, color);
        } else {
            return false;
        }
        if (geometry == null) {
            this.pass = previousPass;
            return false;
        }
        this.submit(geometry, this.matrices);
        if (item != null) {
            this.pass = previousPass;
        }
        return true;
    }

    public void beginItemRender() {
        this.itemPassSeen = false;
    }

    public void beginGlint() {
        this.glintActive = true;
        this.itemGlintPass = 0;
    }

    public void endGlint() {
        this.glintActive = false;
    }

    public boolean recordBlock(BakedModel model, float brightness, float red, float green, float blue) {
        if (!this.modelActive) {
            return false;
        }
        InstanceGeometry geometry = this.owner.backend().block(model, brightness, red, green, blue);
        return geometry != null && this.submit(geometry, this.matrices);
    }

    boolean recordArrow(ArrowEntity arrow, double x, double y, double z, float tickDelta, Identifier texture) {
        this.boundTexture = texture;
        this.arrowMatrix.set(this.matrices);
        this.owner.transformArrow(this.arrowMatrix, arrow, x, y, z, tickDelta);
        return this.submit(this.owner.backend().arrow(), this.arrowMatrix);
    }

    public boolean pushMatrix() {
        if (!this.tracksModelView()) {
            return false;
        }
        this.matrices.pushMatrix();
        return this.suppressFixedFunction;
    }

    public boolean popMatrix() {
        if (!this.tracksModelView()) {
            return false;
        }
        this.matrices.popMatrix();
        return this.suppressFixedFunction;
    }

    public boolean translate(float x, float y, float z) {
        if (!this.tracksModelView()) {
            return false;
        }
        this.matrices.translate(x, y, z);
        return this.suppressFixedFunction;
    }

    public boolean rotate(float angle, float x, float y, float z) {
        if (!this.tracksModelView()) {
            return false;
        }
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if (length != 0.0F) {
            this.matrices.rotate((float)Math.toRadians(angle), x / length, y / length, z / length);
        }
        return this.suppressFixedFunction;
    }

    public boolean scale(float x, float y, float z) {
        if (!this.tracksModelView()) {
            return false;
        }
        this.matrices.scale(x, y, z);
        return this.suppressFixedFunction;
    }

    public void setMatrixMode(int mode) {
        this.matrixMode = mode;
    }

    public void setColor(float red, float green, float blue, float alpha) {
        this.color.set(red, green, blue, alpha);
    }

    public void setTexture(Identifier texture) {
        this.boundTexture = texture;
        if (this.modelActive && this.armorLayer) {
            this.pass = ENCHANTMENT_GLINT_TEXTURE.equals(texture)
                    ? InstanceRenderPass.GLINT : InstanceRenderPass.NORMAL;
        }
    }

    public void finish() {
        if (!this.finished) {
            this.owner.finish(this);
        }
    }

    @Override
    public void close() {
        if (!this.released) {
            this.owner.release(this);
        }
    }

    EntityCapture previous() {
        return this.previous;
    }

    boolean recorded() {
        return this.recorded;
    }

    boolean isPlayer() {
        return !this.item && this.player;
    }

    boolean isModelActive() {
        return this.modelActive;
    }

    void markFinished() {
        this.finished = true;
        this.modelActive = false;
        this.suppressFixedFunction = false;
    }

    void markReleased() {
        this.markFinished();
        this.released = true;
        this.previous = null;
        this.model = null;
        this.entityTexture = null;
        this.boundTexture = null;
    }

    private boolean submit(InstanceGeometry geometry, Matrix4f matrix) {
        return this.submit(geometry, matrix, null);
    }

    private boolean submit(InstanceGeometry geometry, Matrix4f matrix, BoxTemplate box) {
        boolean submitted = this.owner.backend().submit(geometry, this.boundTexture, this.pass, matrix,
                this.packedLight, this.color, this.effectTime, this.currentOverlayColor, box
        );
        this.recorded |= submitted;
        return submitted;
    }

    private void recordPart(ModelPart part, float scale) {
        if (part.invisible || !part.visible) {
            return;
        }
        if (this.pass == InstanceRenderPass.GLINT && !this.glintParts.add(part)) {
            return;
        }
        this.pushMatrix();
        this.translate(part.translateX, part.translateY, part.translateZ);
        this.translate(part.x * scale, part.y * scale, part.z * scale);
        this.matrices.rotateZ(part.rotationZ).rotateY(part.rotationY).rotateX(part.rotationX);

        if (!part.boxes.isEmpty() && !this.submitBoxes(part, scale)) {
            this.submit(this.modelFor(part).getGeometry(part, scale), this.matrices);
        }
        if (part.children != null) {
            for (int i = 0; i < part.children.size(); i++) {
                this.recordPart(part.children.get(i), scale);
            }
        }
        this.popMatrix();
    }

    /**
      * Draws each of the part's boxes from the shared unit cube, mapped onto the box by its own matrix.
      * {@return false if any box is not a plain cuboid}, in which case the caller draws the part's own geometry.
      */
    private ModelGeometry modelFor(ModelPart part) {
        return this.model != null ? this.model
                : this.owner.backend().model(((ModelPartAccessor)part).argentum$getModel());
    }

    private boolean submitBoxes(ModelPart part, float scale) {
        BoxTemplate[] templates = this.owner.backend().boxTemplates(part);
        if (templates == null) {
            return false;
        }
        for (BoxTemplate template : templates) {
            this.boxMatrix.set(this.matrices)
                    .translate(template.originX() * scale, template.minY() * scale, template.minZ() * scale)
                    .scale(template.spanX() * scale, template.spanY() * scale, template.spanZ() * scale);
            this.submit(this.owner.backend().boxGeometry(template.flipped()), this.boxMatrix, template);
        }
        return true;
    }

    private boolean tracksModelView() {
        return !this.finished && this.matrixMode == GL11.GL_MODELVIEW;
    }
}
