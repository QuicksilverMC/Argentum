package dev.rdh.cera.mixin;

import dev.rdh.argentum.impl.render.entity.instancing.InstanceGeometry;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;
import dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer;
import dev.rdh.argentum.impl.render.instancing.BoxTemplate;

import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;

import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelInstancer.class, remap = false)
public abstract class ModelInstancerMixin {
    @Shadow
    public abstract boolean submit(InstanceGeometry geometry, Identifier texture, InstanceRenderPass pass, Matrix4f matrix, int packedLight, Vector4fc color, float effectTime, Vector4fc overlayColor, BoxTemplate box);

    @Inject(method = "submit", at = @At("TAIL"))
    private void cera$renderEmissives(InstanceGeometry geometry, Identifier texture, InstanceRenderPass pass, Matrix4f matrix, int packedLight, Vector4fc color, float effectTime, Vector4fc overlayColor, BoxTemplate box, CallbackInfoReturnable<Boolean> cir) {
		if(!pass.isBase()) return;
		Identifier overlay = Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().emissiveTexture(texture);
		if(overlay == null) return;
		this.submit(geometry, overlay, InstanceRenderPass.EMISSIVE_REPLACE, matrix,
				packedLight, color, effectTime, overlayColor, box);
	}
}
