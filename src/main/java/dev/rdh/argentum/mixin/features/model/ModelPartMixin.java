package dev.rdh.argentum.mixin.features.model;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.ModelPartExtension;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ModelPartExtension {
    @Shadow
    private boolean compiled;

    @Shadow
    private int glList;

    @Shadow
    protected abstract void compile(float scale);

    @Unique
    private final Vector3f argentum$scale = new Vector3f(1.0F);

    @Unique
    private Identifier argentum$texture;

    @Unique
    private boolean argentum$boxesVisible = true;

    @WrapMethod(method = "render")
    private void celeritas$recordModelPart(float scale, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.record((ModelPart)(Object)this, scale)) {
            return;
        }
        if (this.argentum$hasCustomState()) {
            this.argentum$renderCustom(scale);
        } else {
            original.call(scale);
        }
    }

    @Unique
    private void argentum$renderCustom(float scale) {
        ModelPart self = (ModelPart)(Object)this;
        if (self.invisible || !self.visible) {
            return;
        }
        if (!this.compiled) {
            this.compile(scale);
        }
        int previousTexture = GlStateManager.TEXTURES[GlStateManager.texture].texture;
        if (this.argentum$texture != null) {
            Minecraft.getInstance().getTextureManager().bind(this.argentum$texture);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translatef(self.translateX, self.translateY, self.translateZ);
        GlStateManager.translatef(self.x * scale, self.y * scale, self.z * scale);
        if (self.rotationZ != 0.0F) {
            GlStateManager.rotatef(self.rotationZ * (180.0F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
        }
        if (self.rotationY != 0.0F) {
            GlStateManager.rotatef(self.rotationY * (180.0F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
        }
        if (self.rotationX != 0.0F) {
            GlStateManager.rotatef(self.rotationX * (180.0F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
        }
        GlStateManager.scalef(this.argentum$scale.x, this.argentum$scale.y, this.argentum$scale.z);
        if (this.argentum$boxesVisible) {
            GlStateManager.callList(this.glList);
        }
        if (self.children != null) {
            for (int i = 0; i < self.children.size(); i++) {
                self.children.get(i).render(scale);
            }
        }
        GlStateManager.popMatrix();
        if (this.argentum$texture != null) {
            GlStateManager.bindTexture(previousTexture);
        }
    }

    @Override
    public Vector3f argentum$getScale() {
        return this.argentum$scale;
    }

    @Override
    public Identifier argentum$getTexture() {
        return this.argentum$texture;
    }

    @Override
    public void argentum$setTexture(Identifier texture) {
        this.argentum$texture = texture;
    }

    @Override
    public boolean argentum$areBoxesVisible() {
        return this.argentum$boxesVisible;
    }

    @Override
    public void argentum$setBoxesVisible(boolean visible) {
        this.argentum$boxesVisible = visible;
    }

    @Override
    public boolean argentum$hasCustomState() {
        return this.argentum$texture != null || !this.argentum$boxesVisible
                || this.argentum$scale.x != 1.0F || this.argentum$scale.y != 1.0F || this.argentum$scale.z != 1.0F;
    }

    @Inject(
            method = "compile",
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/render/vertex/Tesselator;getBuffer()Lnet/minecraft/client/render/vertex/BufferBuilder;")
    )
    private void celeritas$beginModel(float scale, CallbackInfo ci) {
        Tesselator.getInstance().getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormat.ENTITY);
    }

    @Inject(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEndList()V", remap = false)
    )
    private void celeritas$endModel(float scale, CallbackInfo ci) {
        Tesselator.getInstance().end();
    }
}
