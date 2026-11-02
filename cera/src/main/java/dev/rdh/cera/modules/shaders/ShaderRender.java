package dev.rdh.cera.modules.shaders;

import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;

public final class ShaderRender {
    private ShaderRender() {
    }

    public static boolean isManaged() {
        return Minecraft.getInstance().cera$getShaderEngine().graph().isManaged();
    }

    public static boolean isShadowPass() {
        return Minecraft.getInstance().cera$getShaderEngine().graph().isShadowPass();
    }

    public static boolean isLayerCaptured() {
        var capture = EntityCapture.current();
        return capture != null && capture.isModelActive();
    }

    public static boolean isGlintCaptured() {
        var capture = EntityCapture.current();
        return capture != null && capture.capturesGlint();
    }

    public static void push(Stack stack) {
        if (isManaged()) {
            Minecraft.getInstance().cera$getShaderEngine().graph().push(stack);
        }
    }

    public static void pop(Stack stack) {
        if (isManaged()) {
            Minecraft.getInstance().cera$getShaderEngine().graph().pop(stack);
        }
    }

    public static void resetEntityColor() {
        Minecraft.getInstance().cera$getShaderEngine().state().resetEntityColor();
    }

    public static void bindWhiteTexture() {
        Minecraft.getInstance().cera$getShaderEngine().bindWhiteTexture();
    }

    public static void fullBrightLightmap() {
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240F, 240F);
    }
}
