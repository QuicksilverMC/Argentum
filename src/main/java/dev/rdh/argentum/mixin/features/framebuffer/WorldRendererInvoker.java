package dev.rdh.argentum.mixin.features.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.world.WorldRenderer;

@Mixin(WorldRenderer.class)
public interface WorldRendererInvoker {
    @Invoker("shouldRenderEntityOutlines")
    boolean invokeShouldRenderEntityOutlines();
}
