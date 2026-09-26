package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.inventory.menu.SignEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(SignEditScreen.class)
public abstract class SignEditScreenMixin {
    @Shadow
    private SignBlockEntity sign;

    @Unique
    private Text[] argentum$lines;

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void argentum$recordText(char chr, int key, CallbackInfo ci) {
        this.argentum$lines = this.sign.lines.clone();
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void argentum$rebuildEditedSign(char chr, int key, CallbackInfo ci) {
        if (!Arrays.equals(this.argentum$lines, this.sign.lines)) {
            BakedBlockEntities.rebuild(this.sign.getWorld(), this.sign.getPos());
        }
    }
}
