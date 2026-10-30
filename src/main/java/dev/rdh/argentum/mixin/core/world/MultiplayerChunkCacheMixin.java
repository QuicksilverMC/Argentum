package dev.rdh.argentum.mixin.core.world;

import dev.rdh.argentum.impl.ext.WorldExtension;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(ClientChunkCache.class)
public class MultiplayerChunkCacheMixin {
    @Shadow
    private World world;

    @Shadow
    private WorldChunk empty;

    @Unique
    private WorldChunk argentum$lastChunk;

    @Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;", at = @At("HEAD"), cancellable = true)
    private void argentum$memoHit(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk last = this.argentum$lastChunk;
        if (last != null && last.chunkX == x && last.chunkZ == z) {
            cir.setReturnValue(last);
        }
    }

    @Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;", at = @At("RETURN"))
    private void argentum$memoStore(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();
        if (chunk != this.empty) {
            this.argentum$lastChunk = chunk;
        }
    }

    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void argentum$clearMemoOnLoad(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        this.argentum$lastChunk = null;
    }

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void argentum$clearMemoOnUnload(int x, int z, CallbackInfo ci) {
        this.argentum$lastChunk = null;
    }

    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void afterLoadChunkFromPacket(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        ChunkTrackerHolder.get(this.world).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }

    @Inject(method = "unloadChunk", at = @At("RETURN"))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get(this.world).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
        ((WorldExtension) this.world).argentum$getBlockEntityLight().forgetChunk(x, z);
    }
}
