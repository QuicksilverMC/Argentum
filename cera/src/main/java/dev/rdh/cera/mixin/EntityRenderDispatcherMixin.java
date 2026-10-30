package dev.rdh.cera.mixin;

import dev.rdh.cera.entity.EntityContext;
import dev.rdh.cera.ext.CeraEntityRenderDispatcherExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin implements CeraEntityRenderDispatcherExtension {
    @Unique
    private final EntityContext cera$entityContext = new EntityContext();

    @Override
    public EntityContext cera$getEntityContext() {
        return this.cera$entityContext;
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("HEAD"))
    private void cera$beginEntityRender(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hitbox, CallbackInfoReturnable<Boolean> cir) {
        this.cera$entityContext.begin(entity);
        Minecraft.getInstance().cera$getCustomEntityModels().animations().beginEntity(entity, tickDelta);
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("RETURN"))
    private void cera$endEntityRender(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hitbox, CallbackInfoReturnable<Boolean> ci) {
        this.cera$entityContext.end(entity);
        Minecraft.getInstance().cera$getCustomEntityModels().animations().endRender();
    }
}
