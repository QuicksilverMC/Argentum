package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.PortalBenchmark;
import net.minecraft.client.render.block.entity.EndPortalRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalRenderer.class)
abstract class PortalEffectsMixin {
    @Unique private int portalBenchmark$draw;
    @Unique private static int portalBenchmark$program;

    @Inject(method = "render(Lnet/minecraft/block/entity/EndPortalBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void portalBenchmark$begin(CallbackInfo ci) {
        this.portalBenchmark$draw = 0;
        if (PortalBenchmark.effect("program")) {
            if (portalBenchmark$program == 0) {
                int vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
                GL20.glShaderSource(vertex, "#version 120\nvoid main() { gl_Position = ftransform(); }");
                GL20.glCompileShader(vertex);
                int fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
                GL20.glShaderSource(fragment, "#version 120\nvoid main() { gl_FragColor = vec4(0.1, 0.2, 0.3, 1.0); }");
                GL20.glCompileShader(fragment);
                portalBenchmark$program = GL20.glCreateProgram();
                GL20.glAttachShader(portalBenchmark$program, vertex);
                GL20.glAttachShader(portalBenchmark$program, fragment);
                GL20.glLinkProgram(portalBenchmark$program);
                if (GL20.glGetProgrami(portalBenchmark$program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("Fixture shader failed");
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(fragment);
            }
            GL20.glUseProgram(portalBenchmark$program);
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/EndPortalBlockEntity;DDDFI)V", at = @At("RETURN"))
    private void portalBenchmark$return(CallbackInfo ci) {
        if (PortalBenchmark.effect("program")) GL20.glUseProgram(0);
        PortalBenchmark.recordPortalReturn();
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;texGenParam(Lnet/minecraft/client/render/platform/GlStateManager$TexGenMode;ILjava/nio/FloatBuffer;)V"))
    private void portalBenchmark$texGen(Args args) {
        if (PortalBenchmark.effect("texgen") && args.get(0) == GlStateManager.TexGenMode.S) {
            java.nio.FloatBuffer plane = args.get(2);
            plane.put(plane.position(), plane.get(plane.position()) * 1.25F);
        }
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 16))
    private int portalBenchmark$layers(int count) { return PortalBenchmark.effect("layers") ? 8 : count; }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;vertex(DDD)Lnet/minecraft/client/render/vertex/BufferBuilder;"), index = 1)
    private double portalBenchmark$height(double y) { return PortalBenchmark.effect("geometry") ? y + 0.125 : y; }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"), index = 0)
    private float portalBenchmark$tint(float red) { return PortalBenchmark.effect("tint") ? red * 1.5F : red; }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V"), index = 0)
    private float portalBenchmark$rotation(float angle) { return PortalBenchmark.effect("uv") ? angle + 13.0F : angle; }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/EndPortalRenderer;bindTexture(Lnet/minecraft/resource/Identifier;)V"), index = 0)
    private Identifier portalBenchmark$texture(Identifier texture) {
        return PortalBenchmark.effect("texture") && texture.toString().contains("end_portal")
                ? new Identifier("textures/environment/end_sky.png") : texture;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private void portalBenchmark$blend(CallbackInfo ci) {
        int draw = this.portalBenchmark$draw++;
        if (PortalBenchmark.effect("fog") || PortalBenchmark.effect("fog_exp") || PortalBenchmark.effect("fog_linear")) {
            GL11.glEnable(GL11.GL_FOG);
            GL11.glFogi(GL11.GL_FOG_MODE, PortalBenchmark.effect("fog_exp") ? GL11.GL_EXP
                    : PortalBenchmark.effect("fog_linear") ? GL11.GL_LINEAR : GL11.GL_EXP2);
            GL11.glFogf(GL11.GL_FOG_START, 20.0F);
            GL11.glFogf(GL11.GL_FOG_END, 100.0F);
            GL11.glFogf(GL11.GL_FOG_DENSITY, 0.02F);
            java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
            color.put(new float[]{draw % 3 * 0.2F, 0.1F, 0.3F, 1.0F}).flip();
            GL11.glFogfv(GL11.GL_FOG_COLOR, color);
        }
        if (PortalBenchmark.effect("blend") && draw > 0) GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        if (PortalBenchmark.effect("split") && draw > 0) GlStateManager.blendFunc(GL11.GL_ONE, draw == 7 ? GL11.GL_ZERO : GL11.GL_ONE);
    }
}
