package dev.rdh.argentum.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldRenderer.class)
abstract class LobbyEntityTimingMixin {
    @Unique
    private final int[] lobbyBenchmark$queries = new int[4];
    @Unique
    private final int[] lobbyBenchmark$samples = new int[4];
    @Unique
    private int lobbyBenchmark$frame;

    @WrapMethod(method = "renderEntities")
    private void lobbyBenchmark$timeEntities(Entity camera, Culler culler, float tickDelta, Operation<Void> original) {
        boolean gpu = GL.getCapabilities().GL_ARB_timer_query;
        if (gpu) this.lobbyBenchmark$beginQueries();
        long start = System.nanoTime();
        original.call(camera, culler, tickDelta);
        LobbyBenchmark.recordEntities(System.nanoTime() - start);
        if (gpu) {
            GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);
            GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        }
    }

    @Unique
    private void lobbyBenchmark$beginQueries() {
        int slot = this.lobbyBenchmark$frame++ & 3;
        int query = this.lobbyBenchmark$queries[slot];
        if (query == 0) {
            query = this.lobbyBenchmark$queries[slot] = GL15C.glGenQueries();
        } else if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
            LobbyBenchmark.recordGpuEntities(GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT));
        }
        int samples = this.lobbyBenchmark$samples[slot];
        if (samples == 0) {
            samples = this.lobbyBenchmark$samples[slot] = GL15C.glGenQueries();
        } else if (GL15C.glGetQueryObjecti(samples, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
            LobbyBenchmark.recordEntitySamples(GL15C.glGetQueryObjecti(samples, GL15C.GL_QUERY_RESULT));
        }
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED, samples);
    }
}
