package dev.rdh.argentum.test;

import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

final class LobbyCustomParticle extends Particle {
    private static final boolean DIRECT = Boolean.getBoolean("argentum.lobbyBenchmarkDirectParticles");

    LobbyCustomParticle(World world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.setTextureCoordinates(65);
        this.setColor(0.2F, 0.6F, 1.0F);
        this.lifetime = Integer.MAX_VALUE / 2;
    }

    @Override
    public void render(BufferBuilder target, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        BufferBuilder buffer = DIRECT ? Tesselator.getInstance().getBuffer() : target;
        float u0 = this.spriteRow / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = this.spriteColumn / 16.0F;
        float v1 = v0 + 0.0624375F;
        float size = 0.3F * this.size;
        float x = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
        float y = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
        float z = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
        buffer.vertex(x - dx * size, y - dy * size, z - dz * size).texture(u0, v1).color(this.red, this.green, this.blue, this.alpha).texture(240, 240).nextVertex();
        buffer.vertex(x - dx * size, y + dy * size, z - dz * size).texture(u0, v0).color(this.red, this.green, this.blue, this.alpha).texture(240, 240).nextVertex();
        buffer.vertex(x + dx * size, y + dy * size, z + dz * size).texture(u1, v0).color(this.red, this.green, this.blue, this.alpha).texture(240, 240).nextVertex();
        buffer.vertex(x + dx * size, y - dy * size, z + dz * size).texture(u1, v1).color(this.red, this.green, this.blue, this.alpha).texture(240, 240).nextVertex();
    }
}
