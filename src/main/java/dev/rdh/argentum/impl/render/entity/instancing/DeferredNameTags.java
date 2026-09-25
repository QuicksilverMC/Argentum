package dev.rdh.argentum.impl.render.entity.instancing;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.entity.living.LivingEntity;

import java.util.Arrays;

@SuppressWarnings("unchecked")
final class DeferredNameTags {
    private LivingEntityRenderer<LivingEntity>[] renderers = new LivingEntityRenderer[64];
    private LivingEntity[] entities = new LivingEntity[64];
    private double[] positions = new double[64 * 3];
    private int[] lights = new int[64];
    private int count;

    void clear() {
        this.count = 0;
    }

    void add(LivingEntityRenderer<?> renderer, LivingEntity entity, double x, double y, double z, int light) {
        if (this.count == this.entities.length) {
            this.renderers = Arrays.copyOf(this.renderers, this.count * 2);
            this.entities = Arrays.copyOf(this.entities, this.count * 2);
            this.positions = Arrays.copyOf(this.positions, this.count * 6);
            this.lights = Arrays.copyOf(this.lights, this.count * 2);
        }
        this.lights[this.count] = light;
        this.renderers[this.count] = (LivingEntityRenderer<LivingEntity>) renderer;
        this.entities[this.count] = entity;
        int offset = this.count * 3;
        this.positions[offset] = x;
        this.positions[offset + 1] = y;
        this.positions[offset + 2] = z;
        this.count++;
    }

    void render() {
        for (int i = 0; i < this.count; i++) {
            int offset = i * 3;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, this.lights[i] & 0xFFFF, this.lights[i] >>> 16);
            this.renderers[i].renderNameTag(this.entities[i],
                    this.positions[offset], this.positions[offset + 1], this.positions[offset + 2]
            );
            this.renderers[i] = null;
            this.entities[i] = null;
        }
        this.count = 0;
    }
}
