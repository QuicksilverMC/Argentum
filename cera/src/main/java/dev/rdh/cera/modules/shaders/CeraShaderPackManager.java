package dev.rdh.cera.modules.shaders;

import dev.rdh.cera.Cera;

import com.ventooth.swansong.resources.ShaderPackManager;
import com.ventooth.swansong.resources.pack.ShaderPack;
import com.ventooth.swansong.shader.loader.config.PackLocalizer;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;

public final class CeraShaderPackManager extends ShaderPackManager {
    public CeraShaderPackManager(Path gameDir) {
        super(gameDir);
    }

    @Override
    protected boolean referencePackEnabled() {
        return Cera.CONFIG.referenceShaderPack;
    }

    @Override
    public PackLocalizer localizer(ShaderPack pack) {
        return new CeraPackLocalizer(pack);
    }

    @Override
    protected void onSettingsChanged() {
        Cera.CONFIG.shaderPack = getCurrentShaderPackName();
        Cera.CONFIG_STORAGE.save();
        Minecraft.getInstance().cera$getShaderEngine().scheduleShaderPackReload();
    }
}
