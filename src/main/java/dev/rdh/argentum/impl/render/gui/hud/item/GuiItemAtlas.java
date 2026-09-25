package dev.rdh.argentum.impl.render.gui.hud.item;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class GuiItemAtlas {
    private static final int SLOT_SIZE = 64;
    private static final int SLOTS_PER_AXIS = 16;
    private static final int ATLAS_SIZE = SLOT_SIZE * SLOTS_PER_AXIS;
    private static final int CAPACITY = SLOTS_PER_AXIS * SLOTS_PER_AXIS;

    private static final int NO_SLOT = -1;

    private boolean supported;
    private boolean core;
    private int texture;
    private int framebuffer;
    private int depthBuffer;

    private final Object2IntLinkedOpenHashMap<Key> slots = new Object2IntLinkedOpenHashMap<>();
    { this.slots.defaultReturnValue(NO_SLOT); }

    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);

    private int bakedPixels = -1;

    private final int[] bakedAtVersion = new int[CAPACITY];
    private int used;

    public boolean initialize() {
        var capabilities = GL.getCapabilities();
        this.core = capabilities.OpenGL30;
        if (!this.core && !capabilities.GL_EXT_framebuffer_object) {
            return false;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        this.texture = GL11.glGenTextures();
        GlStateManager.bindTexture(this.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        // 16 bit, not 8: the bake stores premultiplied color
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, ATLAS_SIZE, ATLAS_SIZE, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GlStateManager.bindTexture(previousTexture);

        this.framebuffer = this.core ? GL30C.glGenFramebuffers() : EXTFramebufferObject.glGenFramebuffersEXT();
        this.depthBuffer = this.core ? GL30C.glGenRenderbuffers() : EXTFramebufferObject.glGenRenderbuffersEXT();

        this.bindRenderbuffer(this.depthBuffer);
        if (this.core) {
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL30C.GL_DEPTH_COMPONENT24, ATLAS_SIZE, ATLAS_SIZE);
        } else {
            EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    GL30C.GL_DEPTH_COMPONENT24, ATLAS_SIZE, ATLAS_SIZE);
        }
        this.bindRenderbuffer(0);

        this.supported = true;
        return true;
    }

    public boolean isSupported() {
        return this.supported;
    }

    public int getTexture() {
        return this.texture;
    }

    public static float u0(int slot) {
        return (slot % SLOTS_PER_AXIS) * (float) SLOT_SIZE / ATLAS_SIZE;
    }

    public static float v0(int slot) {
        return (slot / SLOTS_PER_AXIS) * (float) SLOT_SIZE / ATLAS_SIZE;
    }

    public float uvExtent() {
        return (float) this.bakedPixels / ATLAS_SIZE;
    }

    public static int maxPixels() {
        return SLOT_SIZE;
    }

    public int acquire(Key key, int version, int pixels, Runnable render) {
        if (!this.supported) return NO_SLOT;
        if (pixels != this.bakedPixels) {
            this.invalidate();
            this.bakedPixels = pixels;
        }

        int slot = this.slots.getAndMoveToLast(key);

        if (slot == NO_SLOT) {
            if (this.used < CAPACITY) {
                slot = this.used++;
            } else {
                slot = this.slots.removeFirstInt();
            }
            this.slots.putAndMoveToLast(key.withCopiedNbt(), slot);
            this.bakedAtVersion[slot] = Integer.MIN_VALUE;
        }

        if (this.bakedAtVersion[slot] != version) {
            if (!this.bake(slot, pixels, render)) return NO_SLOT;
            this.bakedAtVersion[slot] = version;
        }

        return slot;
    }

    private boolean bake(int slot, int pixels, Runnable render) {
        int previousFramebuffer = this.core
                ? GL11.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);

        this.viewport.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);

        this.bindFramebuffer(this.framebuffer);
        this.attach();

        if (!this.isComplete()) {
            this.bindFramebuffer(previousFramebuffer);
            this.supported = false;
            return false;
        }

        int x = (slot % SLOTS_PER_AXIS) * SLOT_SIZE;
        int y = (slot / SLOTS_PER_AXIS) * SLOT_SIZE;

        boolean scissored = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.scissor.clear();
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.scissor);
        this.clearColor.clear();
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, this.clearColor);

        GL11.glViewport(x, y, pixels, pixels);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, pixels, pixels);
        GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        try {
            render.run();
            return true;
        } finally {
            this.bindFramebuffer(previousFramebuffer);
            GL11.glViewport(this.viewport.get(0), this.viewport.get(1), this.viewport.get(2), this.viewport.get(3));
            GL11.glScissor(this.scissor.get(0), this.scissor.get(1), this.scissor.get(2), this.scissor.get(3));
            if (!scissored) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.clearColor(this.clearColor.get(0), this.clearColor.get(1), this.clearColor.get(2), this.clearColor.get(3));
        }
    }

    public void invalidate() {
        this.slots.clear();
        this.used = 0;
    }

    public void delete() {
        if (this.texture != 0) {
            GL11.glDeleteTextures(this.texture);
            this.texture = 0;
        }
        if (this.depthBuffer != 0) {
            if (this.core) {
                GL30C.glDeleteRenderbuffers(this.depthBuffer);
            } else {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(this.depthBuffer);
            }
            this.depthBuffer = 0;
        }
        if (this.framebuffer != 0) {
            if (this.core) {
                GL30C.glDeleteFramebuffers(this.framebuffer);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(this.framebuffer);
            }
            this.framebuffer = 0;
        }
        this.invalidate();
        this.supported = false;
    }

    private void bindFramebuffer(int framebuffer) {
        if (this.core) {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
        }
    }

    private void bindRenderbuffer(int renderbuffer) {
        if (this.core) {
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, renderbuffer);
        } else {
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, renderbuffer);
        }
    }

    private void attach() {
        if (this.core) {
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, this.texture, 0);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                    GL30C.GL_RENDERBUFFER, this.depthBuffer);
        } else {
            EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, this.texture, 0);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    this.depthBuffer);
        }
    }

    private boolean isComplete() {
        return this.core
                ? GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE
                : EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                        == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT;
    }

    public record Key(BakedModel model, Item item, int damage, NbtElement nbt) {
        public Key withCopiedNbt() {
            if (this.nbt == null) return this;
            return new Key(this.model, this.item, this.damage, this.nbt.copy());
        }
    }

    public static Key keyFor(BakedModel model, ItemStack stack) {
        return new Key(model, stack.getItem(), stack.getDamage(), stack.getNbt());
    }
}
