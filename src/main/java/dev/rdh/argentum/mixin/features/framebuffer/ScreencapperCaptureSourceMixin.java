package dev.rdh.argentum.mixin.features.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "dev.deftu.screencapper.screenshot.capture.CaptureSource", remap = false)
public abstract class ScreencapperCaptureSourceMixin {
    @Inject(method = "readMain", at = @At("HEAD"), cancellable = true, require = 0)
    private static void argentum$readFramebufferFrame(@Coerce Object sink, CallbackInfo ci) {
        MinecraftExtension minecraft = (MinecraftExtension) Minecraft.getInstance();
        if (!minecraft.argentum$isRenderingDirect()) return;
        ci.cancel();
        minecraft.argentum$runOnFramebufferFrame(() -> {
            try {
                Class<?> source = Class.forName("dev.deftu.screencapper.screenshot.capture.CaptureSource");
                Method readMain = source.getMethod("readMain", Class.forName("dev.deftu.screencapper.screenshot.capture.CaptureSource$Sink"));
                readMain.invoke(null, sink);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
