package dev.rdh.argentum.mixin.features.terrain;

import org.embeddedt.embeddium.impl.gl.arena.staging.FallbackStagingBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.APPLEFlushBufferRange.GL_BUFFER_FLUSHING_UNMAP_APPLE;
import static org.lwjgl.opengl.APPLEFlushBufferRange.GL_BUFFER_SERIALIZED_MODIFY_APPLE;
import static org.lwjgl.opengl.APPLEFlushBufferRange.glBufferParameteriAPPLE;
import static org.lwjgl.opengl.APPLEFlushBufferRange.glFlushMappedBufferRangeAPPLE;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL15.glUnmapBuffer;
import static org.lwjgl.opengl.GL15.nglMapBuffer;

@Mixin(value = FallbackStagingBuffer.class, remap = false)
public class FallbackStagingBufferMixin {
    @Unique
    private static Boolean argentum$unsyncedWrites;

    @Inject(method = "enqueueCopy", at = @At("HEAD"), cancellable = true)
    private void argentum$writeUnsynchronized(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset, CallbackInfo ci) {
        if (argentum$unsyncedWrites == null) {
            GLCapabilities caps = GL.getCapabilities();
            argentum$unsyncedWrites = caps.GL_APPLE_flush_buffer_range && !caps.OpenGL31 && !caps.GL_ARB_copy_buffer;
        }
        if (!argentum$unsyncedWrites || dst.getActiveMapping() != null) return;

        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, dst);
        glBufferParameteriAPPLE(GL_ARRAY_BUFFER, GL_BUFFER_SERIALIZED_MODIFY_APPLE, GL_FALSE);
        glBufferParameteriAPPLE(GL_ARRAY_BUFFER, GL_BUFFER_FLUSHING_UNMAP_APPLE, GL_FALSE);
        long ptr = nglMapBuffer(GL_ARRAY_BUFFER, GL_WRITE_ONLY);
        if (ptr == 0L) {
            glBufferParameteriAPPLE(GL_ARRAY_BUFFER, GL_BUFFER_FLUSHING_UNMAP_APPLE, GL_TRUE);
            return;
        }

        int bytes = data.remaining();
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), ptr + writeOffset, bytes);
        glFlushMappedBufferRangeAPPLE(GL_ARRAY_BUFFER, writeOffset, bytes);
        glUnmapBuffer(GL_ARRAY_BUFFER);
        ci.cancel();
    }
}
