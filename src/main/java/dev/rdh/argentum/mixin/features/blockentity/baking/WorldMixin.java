package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow
    @Final
    public boolean isClient;

    @Inject(method = {"notifyEntityAdded", "notifyEntityRemoved"}, at = @At("HEAD"))
    private void argentum$rebuildItemFrame(Entity entity, CallbackInfo ci) {
        if (this.isClient && entity instanceof ItemFrameEntity frame) BakedItemFrames.rebuild(frame);
    }
}
