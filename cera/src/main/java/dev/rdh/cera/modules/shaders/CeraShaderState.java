package dev.rdh.cera.modules.shaders;

import com.ventooth.swansong.shader.ShaderState;
import com.ventooth.swansong.shader.WorldSample;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Map;

public final class CeraShaderState extends ShaderState {
    @Override
    public double cameraX() {
        var e = Minecraft.getInstance().getCamera();
        return e == null ? 0 : e.prevX + (e.x - e.prevX) * getSubTick();
    }

    @Override
    public double cameraY() {
        var e = Minecraft.getInstance().getCamera();
        return e == null ? 0 : e.prevY + (e.y - e.prevY) * getSubTick();
    }

    @Override
    public double cameraZ() {
        var e = Minecraft.getInstance().getCamera();
        return e == null ? 0 : e.prevZ + (e.z - e.prevZ) * getSubTick();
    }

    @Override
    public double celestialAngle() {
        var world = Minecraft.getInstance().world;
        return world == null ? 0 : world.getTimeOfDay(getSubTick());
    }

    @Override
    public double farPlane() {
        return Minecraft.getInstance().options.viewDistance * 16;
    }

    @Override
    public boolean isGuiHidden() {
        return Minecraft.getInstance().options.hideGui;
    }

    @Override
    public double screenBrightness() {
        return Minecraft.getInstance().options.gamma;
    }

    @Override
    public int displayWidth() {
        return Minecraft.getInstance().width;
    }

    @Override
    public int displayHeight() {
        return Minecraft.getInstance().height;
    }

    @Override
    public int heldItemId() {
        var player = Minecraft.getInstance().player;
        var stack = player != null ? player.getItemInHand() : null;
        var item = stack != null ? stack.getItem() : null;
        return item == null ? -1 : Item.getId(item);
    }

    @Override
    public int heldBlockLightValue() {
        var player = Minecraft.getInstance().player;
        var stack = player != null ? player.getItemInHand() : null;
        var block = stack != null ? Block.byItem(stack.getItem()) : null;
        return block != null ? block.getLight() : 0;
    }

    @Override
    public WorldSample sampleWorld() {
        var mc = Minecraft.getInstance();
        var world = mc.world;
        if (world == null) {
            return WorldSample.EMPTY;
        }
        var partialTick = getSubTick();
        var camera = mc.getCamera();
        var player = mc.player;

        var eyeInWater = 0;
        if (mc.options.perspective == 0 && player != null && !player.isSleeping()) {
            if (camera.isSubmergedIn(Material.WATER)) {
                eyeInWater = 1;
            } else if (camera.isSubmergedIn(Material.LAVA)) {
                eyeInWater = 2;
            }
        }

        var nightVision = 0D;
        var blindnessTicks = 0;
        if (player != null) {
            if (player.hasStatusEffect(StatusEffect.NIGHTVISION)) {
                nightVision = mc.gameRenderer.getNightVisionScale(player, partialTick);
            }
            if (player.hasStatusEffect(StatusEffect.BLINDNESS)) {
                blindnessTicks = player.getEffectInstance(StatusEffect.BLINDNESS)
                                       .getDuration();
            }
        }

        var skyColor = world.getSkyColor(camera, partialTick);
        var camPos = camPosInt();
        var pos = new BlockPos(camPos.x(), camPos.y(), camPos.z());
        var biome = world.getBiome(pos);

        return new WorldSample(
                eyeInWater,
                nightVision,
                blindnessTicks,
                camera.getLightLevel(partialTick),
                world.getRain(partialTick),
                world.getTimeOfDay(),
                world.getTime(),
                world.getMoonPhase(),
                skyColor.x,
                skyColor.y,
                skyColor.z,
                biome.id,
                biome.getTemperature(pos),
                biome.getDownfall()
        );
    }

    @Override
    public Map<String, Integer> biomeConstants() {
        return legacyBiomeConstants(Biome.getAll(), biome -> biome.name, biome -> biome.id);
    }
}
