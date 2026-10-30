package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.cera.ext.CeraEntityExtension;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin implements CeraEntityExtension {
    @Shadow
    public World world;

    @Unique
    private float[] cera$animationVars = new float[0];

    @Override
    public float[] cera$getAnimationVars() {
        return this.cera$animationVars;
    }

    @Override
    public void cera$setAnimationVars(float[] vars) {
        this.cera$animationVars = vars;
    }

    @ModifyReturnValue(method = "getLightLevel", at = @At("RETURN"))
    private int cera$applyDynamicLight(int packedLight) {
        return this.world instanceof ClientWorld w
                ? w.cera$getDynamicLights().combine((Entity)(Object)this, packedLight)
                : packedLight;
    }
}
