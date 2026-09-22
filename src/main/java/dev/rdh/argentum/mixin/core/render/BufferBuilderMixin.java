package dev.rdh.argentum.mixin.core.render;

import java.nio.IntBuffer;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.ext.BufferBuilderExtension;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements BufferBuilderExtension {
    @Shadow
    private IntBuffer intBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private VertexFormat format;

    @Shadow
    public abstract void vertices(int[] vertices);

    @Inject(method = "nextVertex", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;grow(I)V"))
    private void celeritas$syncBufferPosition(CallbackInfo ci) {
        this.intBuffer.position(this.vertexCount * this.format.getIntSize());
    }

    @Override
    public IntBuffer argentum$rawIntBuffer() {
        return this.intBuffer;
    }

    @Override
    public void argentum$appendTranslated(int[] vertices, float x, float y) {
        int stride = this.format.getIntSize();
        int start = this.vertexCount * stride;
        this.intBuffer.position(start);
        this.vertices(vertices);
        for (int offset = 0; offset < vertices.length; offset += stride) {
            this.intBuffer.put(start + offset, Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset]) + x));
            this.intBuffer.put(start + offset + 1, Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 1]) + y));
        }
    }
}
