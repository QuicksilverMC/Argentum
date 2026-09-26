package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.modules.EmissiveTextures;
import dev.rdh.cera.modules.cit.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.item.ItemStack;

import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow @Final
    private TextureManager textureManager;

    @Unique
    private boolean cera$hasEmissive;
    @Unique
    private boolean cera$emissive;

    @Shadow
	protected abstract void render(BakedModel model, int color);

    @ModifyExpressionValue(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/model/ModelManager;getModel(Lnet/minecraft/client/resource/ModelIdentifier;)Lnet/minecraft/client/resource/model/BakedModel;"))
    private BakedModel cera$resolveCustomItemVariant(BakedModel original, @Local(argsOnly = true) ItemStack stack, @Local ModelIdentifier location) {
        return Minecraft.getInstance().cera$getCustomItems().resolve(stack, original, location);
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;renderEnchantmentGlint(Lnet/minecraft/client/resource/model/BakedModel;)V"))
    private void cera$renderCustomGlint(ItemRenderer instance, BakedModel model, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        var effects = Minecraft.getInstance().cera$getCustomItems().effects(stack);
        if (effects.isEmpty()) {
            original.call(instance, model);
            return;
        }
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(5890);
        for (CustomItems.Effect effect : effects) {
            this.textureManager.bind(effect.texture());
            effect.blend().apply(1.0F);
            float scale = Minecraft.getInstance().cera$getCustomItems().glintWidth(effect.texture()) / 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.scalef(scale, scale, scale);
            GlStateManager.translatef(effect.speed() * (Minecraft.getTime() % 3000L) / 24000.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(effect.rotation(), 0.0F, 0.0F, 1.0F);
            this.render(model, -1);
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(5888);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;render(Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/item/ItemStack;)V"))
    private void cera$itemEmissivePass(ItemRenderer self, BakedModel model, ItemStack stack, Operation<Void> original) {
        EmissiveTextures emissive = Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures();
        if (!emissive.active()) {
            original.call(self, model, stack);
            return;
        }
        this.cera$hasEmissive = false;
        original.call(self, model, stack);
        if (this.cera$hasEmissive) {
            emissive.forceFullbright();
            this.cera$emissive = true;
            try {
                original.call(self, model, stack);
            } finally {
                this.cera$emissive = false;
                emissive.restoreBrightness();
            }
        }
    }

    @WrapOperation(method = "renderQuads(Lnet/minecraft/client/render/vertex/BufferBuilder;Ljava/util/List;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;renderQuad(Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/client/resource/model/BakedQuad;I)V"))
    private void cera$emissiveQuad(ItemRenderer self, BufferBuilder buffer, BakedQuad quad, int color, Operation<Void> original) {
        var base = (TextureAtlasSprite) BakedQuadView.of(quad).celeritas$getSprite();
        TextureAtlasSprite twin = Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().emissiveSprite(base);
        if (this.cera$emissive) {
            if (twin != null) original.call(self, buffer, EmissiveTextures.resprite(quad, base, twin), color);
            return;
        }
        if (twin != null) this.cera$hasEmissive = true;
        original.call(self, buffer, quad, color);
    }
}
