package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.entity.EntityContext;
import dev.rdh.cera.ext.CeraEntityRenderDispatcherExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin implements CeraEntityRenderDispatcherExtension {
    @Unique
    private final EntityContext cera$entityContext = new EntityContext();

    @Override
    public EntityContext cera$getEntityContext() {
        return this.cera$entityContext;
    }

    @WrapMethod(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z")
    private boolean cera$entityRender(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                                     boolean hitbox, Operation<Boolean> original) {
        var models = Minecraft.getInstance().cera$getCustomEntityModels();
        this.cera$entityContext.begin(entity);
        try {
            models.beginEntity(entity, tickDelta);
            return original.call(entity, x, y, z, yaw, tickDelta, hitbox);
        } finally {
            this.cera$entityContext.end(entity);
            models.endRender();
        }
    }
}
