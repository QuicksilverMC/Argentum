package dev.rdh.argentum.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import dev.rdh.argentum.impl.Argentum;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(GameOptions.class)
public class GameOptionsMixin {
    @Shadow
    public boolean useVbo;

    @Shadow
    public boolean fancyGraphics;

    @Shadow
    public int fpsLimit;

    @Shadow
    private File file;

    @ModifyExpressionValue(method = "getCloudRenderMode", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;cloudRenderMode:I", opcode = Opcodes.GETFIELD))
    private int argentum$defaultClouds(int original) {
        return Argentum.CONFIG.defaultClouds ? (this.fancyGraphics ? 2 : 1) : original;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void argentum$raiseFramerateCap(CallbackInfo ci) {
        GameOptions.Option.FRAMERATE_LIMIT.setMax(1500);
    }

    @ModifyExpressionValue(method = "save", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fpsLimit:I", opcode = Opcodes.GETFIELD))
    private int argentum$saveVanillaFramerateLimit(int fpsLimit) {
        return Math.min(fpsLimit, 260);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void argentum$saveFramerateLimit(CallbackInfo ci) {
        Path path = argentum$framerateLimitFile();
        try {
            Files.writeString(path, (this.fpsLimit >= 1500 ? "unlimited" : Integer.toString(this.fpsLimit)) + "\n");
        } catch (IOException e) {
            Argentum.LOGGER.warn("Could not save {}", path, e);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void argentum$loadFramerateLimit(CallbackInfo ci) {
        int stored = argentum$readFramerateLimit();
        if (stored > 0 && (!this.file.exists() || Math.min(stored, 260) == Math.min(this.fpsLimit, 260))) {
            this.fpsLimit = stored;
        } else if (this.fpsLimit >= 260) {
            this.fpsLimit = 1500;
        }
    }

    private static int argentum$readFramerateLimit() {
        Path path = argentum$framerateLimitFile();
        if (!Files.exists(path)) return 0;
        try {
            String limit = Files.readString(path).trim();
            return limit.equals("unlimited") ? 1500 : Math.min(Integer.parseInt(limit), 1500);
        } catch (IOException | NumberFormatException e) {
            Argentum.LOGGER.warn("Could not read {}", path, e);
            return 0;
        }
    }

    private static Path argentum$framerateLimitFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("fpslimit.txt");
    }

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("RETURN"))
    private void argentum$enableVbos(Minecraft minecraft, File directory, CallbackInfo ci) {
        this.useVbo = true;
    }

    @WrapWithCondition(method = "load", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;useVbo:Z", opcode = Opcodes.PUTFIELD))
    private boolean argentum$skipReadingVbo(GameOptions instance, boolean value) {
        return false;
    }

    @WrapWithCondition(method = "save", at = @At(value = "INVOKE", target = "Ljava/io/PrintWriter;println(Ljava/lang/String;)V", ordinal = 0), slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=useVbo:")))
    private boolean argentum$skipWritingVbo(PrintWriter instance, String x) {
        return false;
    }

    @Redirect(method = "set(Lnet/minecraft/client/options/GameOptions$Option;I)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;useVbo:Z", opcode = 181))
    private void argentum$keepVbosEnabled(GameOptions options, boolean value) {
        options.useVbo = true;
    }
}
