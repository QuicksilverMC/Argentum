package dev.rdh.cera.modules.shaders;

import com.ventooth.swansong.resources.pack.ShaderPack;
import com.ventooth.swansong.shader.loader.config.LangPackLocalizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;

import java.util.List;

public final class CeraPackLocalizer extends LangPackLocalizer {
    public CeraPackLocalizer(ShaderPack pack) {
        super(pack, Minecraft.getInstance().getLanguageManager().getLanguage().getCode());
    }

    @Override
    public String modText(String key, Object... args) {
        return I18n.translate(key, args);
    }

    @Override
    public List<String> wrapToWidth(String line, int maxWidthPx) {
        return Minecraft.getInstance().textRenderer.split(line, maxWidthPx);
    }
}
