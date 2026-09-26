package dev.rdh.cera;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;

import dev.rdh.argentum.impl.Argentum;

import dev.rdh.cera.modules.BetterGrass;
import dev.rdh.cera.modules.ctm.ConnectedTextures;
import dev.rdh.cera.modules.DynamicLights;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.taumc.celeritas.api.options.structure.OptionFlag.REQUIRES_ASSET_RELOAD;
import static org.taumc.celeritas.api.options.structure.OptionFlag.REQUIRES_RENDERER_RELOAD;

final class CeraOptionPages {
    private CeraOptionPages() {
    }

    static List<OptionPage> create() {
        OptionPage world = page("world", OptionGroup.createBuilder()
                .add(mode("better_grass", BetterGrass.Mode.values(), BetterGrass.Mode::key,
                        (config, value) -> config.betterGrass = value, config -> config.betterGrass, REQUIRES_RENDERER_RELOAD))
                .add(mode("connected_textures", ConnectedTextures.Mode.values(), ConnectedTextures.Mode::key,
                        (config, value) -> config.connectedTextures = value, config -> config.connectedTextures, REQUIRES_RENDERER_RELOAD))
                .add(mode("dynamic_lights", DynamicLights.Mode.values(), DynamicLights.Mode::key,
                        (config, value) -> config.dynamicLights = value, config -> config.dynamicLights, REQUIRES_RENDERER_RELOAD))
                .add(toggle("natural_textures", (config, value) -> config.naturalTextures = value, config -> config.naturalTextures, REQUIRES_RENDERER_RELOAD))
                .add(toggle("emissive_textures", (config, value) -> config.emissiveTextures = value, config -> config.emissiveTextures, REQUIRES_RENDERER_RELOAD))
                .add(toggle("custom_block_layers", (config, value) -> config.customBlockLayers = value, config -> config.customBlockLayers, REQUIRES_ASSET_RELOAD, REQUIRES_RENDERER_RELOAD))
                .add(toggle("animated_textures", (config, value) -> {
                    if (config.animatedTextures != value) {
                        config.animatedTextures = value;
                        Minecraft.getInstance().getTextureManager().cera$getAnimatedTextures().setEnabled(value);
                    }
                }, config -> config.animatedTextures)));

        OptionPage colors = page("colors", OptionGroup.createBuilder()
                .add(toggle("custom_colors", (config, value) -> {
                    config.customColors = value;
                    Minecraft.getInstance().cera$getCustomColors().reapplyTextColors();
                }, config -> config.customColors, REQUIRES_RENDERER_RELOAD))
                .add(toggle("custom_lightmaps", (config, value) -> config.customLightmaps = value, config -> config.customLightmaps))
                .add(toggle("custom_sky", (config, value) -> config.customSky = value, config -> config.customSky)));

        OptionPage entities = page("entities", OptionGroup.createBuilder()
                .add(toggle("random_entities", (config, value) -> config.randomEntities = value, config -> config.randomEntities))
                .add(toggle("custom_items", (config, value) -> config.customItems = value, config -> config.customItems))
                .add(toggle("optifine_cosmetics", (config, value) -> config.optifineCosmetics = value, config -> config.optifineCosmetics)));

        OptionPage interfaces = page("interfaces", OptionGroup.createBuilder()
                .add(toggle("custom_guis", (config, value) -> config.customGuis = value, config -> config.customGuis))
                .add(toggle("hd_fonts", (config, value) -> {
                    config.hdFonts = value;
                    if (value) Argentum.CONFIG.fontBatching = true;
                }, config -> config.hdFonts, REQUIRES_ASSET_RELOAD))
                .add(toggle("custom_panorama", (config, value) -> config.customPanorama = value, config -> config.customPanorama, REQUIRES_ASSET_RELOAD))
                .add(toggle("custom_loading_screens", (config, value) -> config.customLoadingScreens = value, config -> config.customLoadingScreens)));

        return List.of(world, colors, entities, interfaces);
    }

    private static OptionPage page(String name, OptionGroup.Builder options) {
        return new OptionPage(id(name), text("pages." + name), List.of(options.setId(id(name + "_group")).build()));
    }

    private static OptionImpl<CeraConfig, Boolean> toggle(String id, BiConsumer<CeraConfig, Boolean> setter,
                                                          Function<CeraConfig, Boolean> getter, OptionFlag... flags) {
        return OptionImpl.createBuilder(boolean.class, Cera.CONFIG_STORAGE)
                .setId(id(id))
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter)
                .setFlags(flags)
                .build();
    }

    private static <E extends Enum<E>> OptionImpl<CeraConfig, Integer> mode(String id, E[] values, Function<E, String> key,
                                                                          BiConsumer<CeraConfig, E> setter, Function<CeraConfig, E> getter,
                                                                          OptionFlag... flags) {
        return OptionImpl.createBuilder(int.class, Cera.CONFIG_STORAGE)
                .setId(id(id))
                .setControl(option -> new SliderControl(option, 0, values.length - 1, 1, value -> text(key.apply(values[value]))))
                .setBinding((config, value) -> setter.accept(config, values[value]), config -> getter.apply(config).ordinal())
                .setFlags(flags)
                .build();
    }

    private static <T> OptionIdentifier<T> id(String path) {
        return OptionIdentifier.create("cera", path).cast();
    }

    private static TextComponent text(String path, Object... args) {
        return TextComponent.translatable("cera.options." + path, args);
    }
}
