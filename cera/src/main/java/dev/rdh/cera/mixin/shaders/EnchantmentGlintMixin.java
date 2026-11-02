package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractArmorLayer.class)
public abstract class EnchantmentGlintMixin {
    @WrapMethod(method = "renderEnchantmentGlint")
    private void cera$glint(LivingEntity entity, Model model, float walk, float speed, float tickDelta, float bob, float yaw, float pitch, float scale, Operation<Void> original) {
        if (ShaderRender.isManaged() && ShaderRender.isShadowPass()) return;
        boolean pushed = ShaderRender.isManaged() && !ShaderRender.isGlintCaptured();
        if (pushed) ShaderRender.push(Stack.ArmorGlint);
        try {
            original.call(entity, model, walk, speed, tickDelta, bob, yaw, pitch, scale);
        } finally {
            if (pushed) ShaderRender.pop(Stack.ArmorGlint);
        }
    }
}
