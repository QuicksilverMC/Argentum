package dev.rdh.argentum.mixin.features.cloud;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "setupCamera", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0), index = 3)
    private float argentum$extendTerrainFarPlane(float far) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer == null ? far : renderer.getCloudRenderer().farPlane(far);
    }

    @ModifyArg(method = {"render(IFJ)V", "renderClouds"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 1), index = 3)
    private float argentum$extendSkyCloudsFarPlane(float far) {
        return argentum$extendTerrainFarPlane(far);
    }
}
