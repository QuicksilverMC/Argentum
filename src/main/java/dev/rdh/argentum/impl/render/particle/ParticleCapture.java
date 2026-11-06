package dev.rdh.argentum.impl.render.particle;

import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.entity.Entity;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.lwjgl.opengl.GL11;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.mixin.features.model.BufferBuilderAccessor;

import java.util.Set;

public final class ParticleCapture {
    private final ParticleCaptureBuffer capture = new ParticleCaptureBuffer();
    private final Set<Class<?>> uncapturable = new ReferenceOpenHashSet<>();
    private BufferBuilder batch;

    public void begin(BufferBuilder buffer) {
        this.batch = Argentum.CONFIG.fastParticles ? buffer : null;
    }

    public void end() {
        this.batch = null;
    }

    public boolean render(Particle particle, BufferBuilder buffer, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        if (buffer != this.batch || !matchesCapture(buffer) || this.uncapturable.contains(particle.getClass())) return false;

        ParticleCaptureBuffer capture = this.capture;
        capture.reset();
        int before = buffer.getVertexCount();
        particle.render(capture, camera, tickDelta, dx, dy, dz, forwards, sideways);
        if (capture.unsupported() || buffer.getVertexCount() != before || !((BufferBuilderAccessor) buffer).celeritas$isBuilding()) {
            this.uncapturable.add(particle.getClass());
            Argentum.LOGGER.info("Particle {} builds its quad in a way that can't be captured, drawing it directly", particle.getClass().getName());
            if (capture.unsupported()) {
                particle.render(buffer, camera, tickDelta, dx, dy, dz, forwards, sideways);
                return true;
            }
        }
        if (capture.ints() > 0) buffer.argentum$appendVertices(capture.vertices(), capture.ints());
        return true;
    }

    // the capture stores what an untranslated, colored particle-format buffer would
    private static boolean matchesCapture(BufferBuilder buffer) {
        BufferBuilderAccessor accessor = (BufferBuilderAccessor) buffer;
        return buffer.getDrawMode() == GL11.GL_QUADS && DefaultVertexFormat.PARTICLE.equals(buffer.getFormat())
                && !accessor.argentum$isUncolored() && accessor.argentum$getOffsetX() == 0.0
                && accessor.argentum$getOffsetY() == 0.0 && accessor.argentum$getOffsetZ() == 0.0;
    }
}
