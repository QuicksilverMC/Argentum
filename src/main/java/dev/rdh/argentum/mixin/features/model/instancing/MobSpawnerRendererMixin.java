package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.spawner.MobSpawner;
import net.minecraft.client.render.block.entity.MobSpawnerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;

@Mixin(MobSpawnerRenderer.class)
public abstract class MobSpawnerRendererMixin {
    // also reached from SpawnerMinecartRenderer
    @WrapMethod(method = "renderDisplayEntity")
    private static void argentum$drawDisplayEntityDirectly(MobSpawner spawner, double dx, double dy, double dz, float tickDelta, Operation<Void> original) {
        EntityInstancing.beginDisplayEntity();
        try {
            original.call(spawner, dx, dy, dz, tickDelta);
        } finally {
            EntityInstancing.endDisplayEntity();
        }
    }
}
