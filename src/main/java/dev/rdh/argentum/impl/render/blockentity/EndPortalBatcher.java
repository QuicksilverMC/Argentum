package dev.rdh.argentum.impl.render.blockentity;

import dev.rdh.argentum.impl.ext.BlockEntityRenderState;
import dev.rdh.argentum.impl.ext.WorldExtension;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.EndPortalRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class EndPortalBatcher {
    private static final Identifier END_SKY_LOCATION = new Identifier("textures/environment/end_sky.png");
    private static final Identifier END_PORTAL_LOCATION = new Identifier("textures/entity/end_portal.png");
    private static final Random RANDOM = new Random(31100L);
    private static final FloatBuffer PLANE = MemoryTracker.createFloatBuffer(16);

    private final List<BlockEntity> portals = new ArrayList<>();
    private long[] keys = new long[64];

    public boolean add(BlockEntity blockEntity) {
        if (!(blockEntity instanceof EndPortalBlockEntity)) return false;
        BlockEntityRenderDispatcher dispatcher = BlockEntityRenderDispatcher.INSTANCE;
        BlockEntityRenderer<?> renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null || renderer.getClass() != EndPortalRenderer.class) return false;
        if (blockEntity.squaredDistanceTo(dispatcher.cameraX, dispatcher.cameraY, dispatcher.cameraZ) < blockEntity.getSquaredViewDistance()) {
            this.portals.add(blockEntity);
        }
        return true;
    }

    public void flush() {
        int count = this.portals.size();
        if (count == 0) return;
        try {
            if (this.keys.length < count) this.keys = new long[Math.max(count, this.keys.length * 2)];
            World world = BlockEntityRenderDispatcher.INSTANCE.world;
            for (int i = 0; i < count; i++) {
                BlockEntity portal = this.portals.get(i);
                // y (8 bits) | packed light (24 bits) | index: sorting only needs equal (y, light) runs to end up adjacent
                this.keys[i] = (long)portal.getPos().getY() << 56 | (long)light(world, portal) << 32 | i;
            }
            Arrays.sort(this.keys, 0, count);
            int start = 0;
            for (int i = 1; i <= count; i++) {
                if (i == count || this.keys[i] >>> 32 != this.keys[start] >>> 32) {
                    this.renderGroup(start, i);
                    start = i;
                }
            }
        } finally {
            this.portals.clear();
        }
    }

    private static int light(World world, BlockEntity portal) {
        BlockPos pos = portal.getPos();
        int generation = ((WorldExtension)world).argentum$getBlockEntityLight().generation(pos);
        BlockEntityRenderState state = (BlockEntityRenderState)portal;
        int light = state.argentum$getCachedLight(generation);
        if (light == -1) {
            light = world.getLightColor(pos, 0);
            state.argentum$cacheLight(light, generation);
        }
        return light;
    }

    private void renderGroup(int from, int to) {
        BlockEntityRenderDispatcher dispatcher = BlockEntityRenderDispatcher.INSTANCE;
        BlockEntity first = this.portals.get((int)this.keys[from]);
        int light = (int)(this.keys[from] >>> 32) & 0xFFFFFF;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, light % 65536, light / 65536);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        float cameraX = (float)dispatcher.cameraX;
        float cameraY = (float)dispatcher.cameraY;
        float cameraZ = (float)dispatcher.cameraZ;
        double e = first.getPos().getY() - BlockEntityRenderDispatcher.offsetY;
        GlStateManager.disableLighting();
        RANDOM.setSeed(31100L);
        float l = 0.75F;

        for (int m = 0; m < 16; m++) {
            GlStateManager.pushMatrix();
            float n = 16 - m;
            float o = 0.0625F;
            float p = 1.0F / (n + 1.0F);
            if (m == 0) {
                dispatcher.textureManager.bind(END_SKY_LOCATION);
                p = 0.1F;
                n = 65.0F;
                o = 0.125F;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(770, 771);
            }
            if (m >= 1) {
                dispatcher.textureManager.bind(END_PORTAL_LOCATION);
            }
            if (m == 1) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(1, 1);
                o = 0.5F;
            }

            float q = (float)(-(e + l));
            float r = q + (float)Camera.offset().y;
            float s = q + n + (float)Camera.offset().y;
            float t = (float)(e + l) + r / s;
            GlStateManager.translatef(cameraX, t, cameraZ);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.S, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.T, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.R, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.Q, 9216);
            GlStateManager.texGenParam(GlStateManager.TexGenMode.S, 9473, plane(1.0F, 0.0F, 0.0F, 0.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.T, 9473, plane(0.0F, 0.0F, 1.0F, 0.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.R, 9473, plane(0.0F, 0.0F, 0.0F, 1.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.Q, 9474, plane(0.0F, 1.0F, 0.0F, 0.0F));
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.S);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.T);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.R);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.Q);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translatef(0.0F, (float)(Minecraft.getTime() % 700000L) / 700000.0F, 0.0F);
            GlStateManager.scalef(o, o, o);
            GlStateManager.translatef(0.5F, 0.5F, 0.0F);
            GlStateManager.rotatef((m * m * 4321 + m * 9) * 2.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translatef(-0.5F, -0.5F, 0.0F);
            GlStateManager.translatef(-cameraX, -cameraZ, -cameraY);
            GlStateManager.translatef((float)Camera.offset().x * n / r, (float)Camera.offset().z * n / r, -cameraY);

            float red = (RANDOM.nextFloat() * 0.5F + 0.1F) * p;
            float green = (RANDOM.nextFloat() * 0.5F + 0.4F) * p;
            float blue = (RANDOM.nextFloat() * 0.5F + 0.5F) * p;
            if (m == 0) {
                red = green = blue = p;
            }
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuffer();
            buffer.begin(7, DefaultVertexFormat.POSITION_COLOR);
            double y = e + l;
            for (int i = from; i < to; i++) {
                BlockPos pos = this.portals.get((int)this.keys[i]).getPos();
                double x = pos.getX() - BlockEntityRenderDispatcher.offsetX;
                double z = pos.getZ() - BlockEntityRenderDispatcher.offsetZ;
                buffer.vertex(x, y, z).color(red, green, blue, 1.0F).nextVertex();
                buffer.vertex(x, y, z + 1.0).color(red, green, blue, 1.0F).nextVertex();
                buffer.vertex(x + 1.0, y, z + 1.0).color(red, green, blue, 1.0F).nextVertex();
                buffer.vertex(x + 1.0, y, z).color(red, green, blue, 1.0F).nextVertex();
            }
            tesselator.end();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            dispatcher.textureManager.bind(END_SKY_LOCATION);
        }

        GlStateManager.disableBlend();
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.S);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.T);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.R);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.Q);
        GlStateManager.enableLighting();
    }

    private static FloatBuffer plane(float a, float b, float c, float d) {
        PLANE.clear();
        PLANE.put(a).put(b).put(c).put(d);
        PLANE.flip();
        return PLANE;
    }
}
