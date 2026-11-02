package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

import dev.rdh.cera.modules.CustomPanorama.Panorama;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Shadow
    @Final
    private static Identifier[] PANORAMA_LOCATIONS;

    @ModifyArg(method = "drawBackgroundBase", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V"), index = 0)
    private Identifier cera$panoramaFace(Identifier original) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        if (panorama == null) return original;
        for (int face = 0; face < PANORAMA_LOCATIONS.length; face++) {
            if (PANORAMA_LOCATIONS[face] == original) return panorama.textures()[face];
        }
        return original;
    }

    @Definition(id = "i", local = @Local(type = int.class, ordinal = 2))
    @Expression("? < @(i * i)")
    @ModifyExpressionValue(method = "drawBackgroundBase", at = @At("MIXINEXTRAS:EXPRESSION"))
    private int cera$blur1(int original) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        return panorama == null ? original : panorama.blur1();
    }

    @Definition(id = "i", local = @Local(type = int.class, ordinal = 0))
    @Expression("? < @(i)")
    @ModifyExpressionValue(method = "drawBackgroundImage", at = @At("MIXINEXTRAS:EXPRESSION"))
    private int cera$blur2(int i) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        return panorama == null ? i : panorama.blur2();
    }

    @WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 1))
    private void cera$blur3(TitleScreen screen, float tickDelta, Operation<Void> original) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        if (panorama == null) {
            original.call(screen, tickDelta);
            return;
        }
        for (int i = 0; i < panorama.blur3() * 2; i++) {
            original.call(screen, tickDelta);
        }
    }

    @WrapWithCondition(method = "drawBackground", at = { @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 2), @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 3), @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 4), @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 5), @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawBackgroundImage(F)V", ordinal = 6) })
    private boolean cera$skipVanillaBlur(TitleScreen screen, float tickDelta) {
        return Minecraft.getInstance().cera$getCustomPanorama().active() == null;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;fillGradient(IIIIII)V", ordinal = 0))
    private void cera$overlay1(TitleScreen screen, int x1, int y1, int x2, int y2, int top, int bottom, Operation<Void> original) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        if (panorama == null) {
            original.call(screen, x1, y1, x2, y2, top, bottom);
        } else if (panorama.overlay1Top() != 0 || panorama.overlay1Bottom() != 0) {
            original.call(screen, x1, y1, x2, y2, panorama.overlay1Top(), panorama.overlay1Bottom());
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;fillGradient(IIIIII)V", ordinal = 1))
    private void cera$overlay2(TitleScreen screen, int x1, int y1, int x2, int y2, int top, int bottom, Operation<Void> original) {
        Panorama panorama = Minecraft.getInstance().cera$getCustomPanorama().active();
        if (panorama == null) {
            original.call(screen, x1, y1, x2, y2, top, bottom);
        } else if (panorama.overlay2Top() != 0 || panorama.overlay2Bottom() != 0) {
            original.call(screen, x1, y1, x2, y2, panorama.overlay2Top(), panorama.overlay2Bottom());
        }
    }
}
