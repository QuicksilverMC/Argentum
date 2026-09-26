package dev.rdh.argentum.mixin.features.blockentity.baking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @WrapOperation(
            method = "handleSignBlockEntityUpdate",
            at = @At(value = "INVOKE", target = "Ljava/lang/System;arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V")
    )
    private void argentum$rebuildSign(Object source, int sourceIndex, Object destination, int destinationIndex, int length,
            Operation<Void> original, @Local SignBlockEntity sign) {
        original.call(source, sourceIndex, destination, destinationIndex, length);
        BakedBlockEntities.rebuild(sign.getWorld(), sign.getPos());
    }
}
