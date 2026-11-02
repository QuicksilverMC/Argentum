package dev.rdh.argentum.mixin.core.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.WorldChunkExtension;

import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements WorldChunkExtension {
    @Unique
    private int argentum$entityCount;

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void argentum$countAddedEntity(Entity entity, CallbackInfo ci) {
        this.argentum$entityCount++;
    }

    @ModifyExpressionValue(method = "removeEntity(Lnet/minecraft/entity/Entity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/TypeInstanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private boolean argentum$countRemovedEntity(boolean removed) {
        if (removed) {
            this.argentum$entityCount--;
        }
        return removed;
    }

    @Override
    public boolean argentum$hasEntities() {
        return this.argentum$entityCount > 0;
    }
}
