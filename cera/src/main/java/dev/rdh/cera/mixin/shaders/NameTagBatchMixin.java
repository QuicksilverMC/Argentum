package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ventooth.swansong.shader.StateGraph.Stack;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;
import dev.rdh.argentum.mixin.features.model.BufferBuilderAccessor;
import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.IntBuffer;

@Mixin(value = NameTagBatch.class, remap = false)
abstract class NameTagBatchMixin {
    @ModifyExpressionValue(method = "text*", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/entity/NameTagInstancing;available()Z"))
    private boolean cera$noInstancingUnderShaders(boolean available) {
        return available && !Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl();
    }

    @WrapOperation(method = "flush", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferUploader;end(Lnet/minecraft/client/render/vertex/BufferBuilder;)V", remap = true))
    private void cera$drawWithPackPrograms(BufferUploader uploader, BufferBuilder buffer, Operation<Void> original) {
        if (!ShaderRender.isManaged()) {
            original.call(uploader, buffer);
            return;
        }
        ShaderRender.resetEntityColor();
        cera$flipYZ(buffer);
        GL11.glPushMatrix();
        GL11.glScalef(1.0F, -1.0F, -1.0F);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        boolean textured = GlStateManager.TEXTURES[0].state.enabled;
        if (textured) {
            ShaderRender.push(Stack.Text);
        } else {
            ShaderRender.bindWhiteTexture();
        }
        try {
            original.call(uploader, buffer);
        } finally {
            if (textured) ShaderRender.pop(Stack.Text);
            GL11.glPopMatrix();
        }
    }

    private static void cera$flipYZ(BufferBuilder buffer) {
        IntBuffer vertices = ((BufferBuilderAccessor) buffer).argentum$getIntBuffer();
        int stride = buffer.getFormat().getIntSize();
        for (int i = 0, end = buffer.getVertexCount() * stride; i < end; i += stride) {
            vertices.put(i + 1, vertices.get(i + 1) ^ 0x80000000);
            vertices.put(i + 2, vertices.get(i + 2) ^ 0x80000000);
        }
    }
}
