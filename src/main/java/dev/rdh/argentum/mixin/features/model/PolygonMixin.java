package dev.rdh.argentum.mixin.features.model;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Polygon.class)
public abstract class PolygonMixin {
    @Unique
    private boolean celeritas$drawOnSelf;

    @WrapWithCondition(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;begin(ILnet/minecraft/client/render/vertex/VertexFormat;)V"))
    private boolean celeritas$beginIfNeeded(BufferBuilder buffer, int mode, VertexFormat format) {
        return this.celeritas$drawOnSelf = !((BufferBuilderAccessor) buffer).celeritas$isBuilding();
    }

    @WrapWithCondition(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private boolean celeritas$endIfNeeded(Tesselator tesselator) {
        return this.celeritas$drawOnSelf;
    }
}
