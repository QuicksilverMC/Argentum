package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 1100)
public abstract class TextRendererMixin {
    @WrapMethod(method = "drawWithShadow(Ljava/lang/String;FFI)I")
    private int cera$drawWithShadow(String text, float x, float y, int color, Operation<Integer> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed) ShaderRender.push(Stack.Text);
        try {
            return original.call(text, x, y, color);
        } finally {
            if (pushed) ShaderRender.pop(Stack.Text);
        }
    }

    @WrapMethod(method = "draw(Ljava/lang/String;III)I")
    private int cera$draw(String text, int x, int y, int color, Operation<Integer> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed) ShaderRender.push(Stack.Text);
        try {
            return original.call(text, x, y, color);
        } finally {
            if (pushed) ShaderRender.pop(Stack.Text);
        }
    }

    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int cera$draw(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed) ShaderRender.push(Stack.Text);
        try {
            return original.call(text, x, y, color, shadow);
        } finally {
            if (pushed) ShaderRender.pop(Stack.Text);
        }
    }
}
