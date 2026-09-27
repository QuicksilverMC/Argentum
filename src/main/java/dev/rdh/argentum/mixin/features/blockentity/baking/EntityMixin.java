package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public World world;

    @Inject(method = "onDataValueChanged", at = @At("HEAD"))
    private void argentum$rebuildItemFrame(int id, CallbackInfo ci) {
        if ((Object)this instanceof ItemFrameEntity frame && this.world != null && this.world.isClient) {
            BakedItemFrames.rebuild(frame);
        }
    }
}
