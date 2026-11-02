package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;
import dev.rdh.argentum.impl.render.environment.EndPortalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.Minecraft;

@Mixin(value = {BakedBlockEntities.class, NameTagBatch.class, EndPortalRenderer.class}, remap = false)
abstract class ArgentumRenderersMixin {
    @ModifyExpressionValue(method = "*", at = {
            @At(value = "FIELD", target = "Ldev/rdh/argentum/impl/config/ArgentumConfig;bakeBlockEntities:Z"),
            @At(value = "FIELD", target = "Ldev/rdh/argentum/impl/config/ArgentumConfig;nameTagBatching:Z"),
            @At(value = "FIELD", target = "Ldev/rdh/argentum/impl/config/ArgentumConfig;fasterEndPortals:Z")
    })
    private static boolean cera$vanillaUnderShaders(boolean enabled) {
        return enabled && !Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl();
    }
}
