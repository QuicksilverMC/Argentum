package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.render.block.entity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeaconRenderer.class)
public abstract class BeaconRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/block/entity/BeaconBlockEntity;DDDFI)V")
    private void cera$beacon(BeaconBlockEntity beacon, double x, double y, double z, float tickDelta, int destroyProgress,
                             Operation<Void> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed) ShaderRender.push(Stack.Beacon);
        try {
            original.call(beacon, x, y, z, tickDelta, destroyProgress);
        } finally {
            if (pushed) ShaderRender.pop(Stack.Beacon);
        }
    }
}
