package dev.rdh.cera.modules.shaders;

import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;

import java.util.ArrayList;
import java.util.List;

public final class ShaderRender {
    private static boolean textBatchPushed;
    private static final List<SignBlockEntity> deferredSigns = new ArrayList<>();
    private static boolean deferringSignText;
    private static boolean signTextPass;

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

    public static boolean isTextBatchPushed() {
        return textBatchPushed;
    }

    public static void pushTextBatch() {
        if (!textBatchPushed && isManaged()) {
            push(Stack.Text);
            textBatchPushed = true;
        }
    }

    public static void popTextBatch() {
        if (textBatchPushed) {
            textBatchPushed = false;
            pop(Stack.Text);
        }
    }

    public static void beginSignTextDeferral() {
        deferredSigns.clear();
        deferringSignText = isManaged();
    }

    public static boolean isDeferringSignText() {
        return deferringSignText && !signTextPass;
    }

    public static boolean isSignTextPass() {
        return signTextPass;
    }

    public static boolean deferSignText(SignBlockEntity sign) {
        if (isDeferringSignText() && (deferredSigns.isEmpty() || deferredSigns.getLast() != sign)) {
            deferredSigns.add(sign);
            return true;
        }
        return false;
    }

    public static void undeferLastSignText() {
        deferredSigns.removeLast();
    }

    public static void discardDeferredSignText() {
        deferringSignText = false;
        deferredSigns.clear();
    }

    public static void renderDeferredSignText(float tickDelta) {
        deferringSignText = false;
        if (deferredSigns.isEmpty()) return;

        BlockEntityRenderDispatcher dispatcher = BlockEntityRenderDispatcher.INSTANCE;
        var engine = Minecraft.getInstance().cera$getShaderEngine();
        signTextPass = true;
        try {
            for (SignBlockEntity sign : deferredSigns) {
                engine.nextBlockEntity(sign);
                var pos = sign.getPos();
                int light = dispatcher.world.getLightColor(pos, 0);
                GLX.multiTexCoord2f(GLX.GL_TEXTURE1, light % 65536, light / 65536);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                BlockEntityRenderer<SignBlockEntity> renderer = dispatcher.getRenderer(sign);
                renderer.render(sign, pos.getX() - BlockEntityRenderDispatcher.offsetX, pos.getY() - BlockEntityRenderDispatcher.offsetY,
                        pos.getZ() - BlockEntityRenderDispatcher.offsetZ, tickDelta, -1);
            }
        } finally {
            signTextPass = false;
            deferredSigns.clear();
            popTextBatch();
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
