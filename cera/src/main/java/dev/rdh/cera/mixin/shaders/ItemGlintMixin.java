package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.resource.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemRenderer.class)
public abstract class ItemGlintMixin {
    @WrapMethod(method = "renderEnchantmentGlint")
    private void cera$glint(BakedModel model, Operation<Void> original) {
        if (ShaderRender.isManaged() && ShaderRender.isShadowPass()) return;
        boolean pushed = ShaderRender.isManaged() && !ShaderRender.isGlintCaptured();
        if (pushed) ShaderRender.push(Stack.ArmorGlint);
        try {
            original.call(model);
        } finally {
            if (pushed) ShaderRender.pop(Stack.ArmorGlint);
        }
    }
}
