package dev.rdh.argentum.extras;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.lwjgl.system.Platform;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class ArgentumExtrasOptionPage {
    private static final String MOD_ID = "argentum-extras";

    private ArgentumExtrasOptionPage() {
    }

    static List<OptionPage> create() {
        OptionGroup particles = OptionGroup.createBuilder()
                .setId(id("particle_controls"))
                .add(toggle("explosion_particles", (c, v) -> c.explosionParticles = v, c -> c.explosionParticles))
                .add(toggle("spell_particles", (c, v) -> c.spellParticles = v, c -> c.spellParticles))
                .add(toggle("portal_particles", (c, v) -> c.portalParticles = v, c -> c.portalParticles))
                .add(toggle("smoke_and_flame_particles", (c, v) -> c.smokeAndFlameParticles = v,
                        c -> c.smokeAndFlameParticles))
                .add(toggle("redstone_particles", (c, v) -> c.redstoneParticles = v, c -> c.redstoneParticles))
                .add(toggle("water_particles", (c, v) -> c.waterParticles = v, c -> c.waterParticles))
                .add(toggle("miscellaneous_particles", (c, v) -> c.miscellaneousParticles = v,
                        c -> c.miscellaneousParticles))
                .build();

        OptionGroup debug = OptionGroup.createBuilder()
                .setId(id("debug_hud"))
                .add(toggle("steady_debug_hud", (c, v) -> c.steadyDebugHud = v, c -> c.steadyDebugHud))
                .add(slider("debug_hud_refresh_interval", 0, 1000, 50,
                        (c, v) -> c.debugHudRefreshIntervalMs = v, c -> c.debugHudRefreshIntervalMs,
                        value -> value == 0 ? text("value.every_frame") : text("value.milliseconds", value))
                        .setEnabledPredicate(() -> ArgentumExtras.CONFIG.steadyDebugHud)
                        .build())
                .add(slider("debug_hud_scale", 0, 4, 1,
                        (c, v) -> c.debugHudScale = v, c -> c.debugHudScale,
                        value -> value == 0 ? text("value.gui_scale") : text("value.scale", value)).build())
                .add(toggle("fps_hud", (c, v) -> c.fpsHud = v, c -> c.fpsHud))
                .build();

        OptionGroup camera = OptionGroup.createBuilder()
                .setId(id("camera_effects"))
                .add(percentSlider("dynamic_fov_strength",
                        (c, v) -> c.dynamicFovStrength = v, c -> c.dynamicFovStrength))
                .add(percentSlider("portal_distortion_strength",
                        (c, v) -> c.portalDistortionStrength = v, c -> c.portalDistortionStrength))
                .add(percentSlider("view_bobbing_strength",
                        (c, v) -> c.viewBobbingStrength = v, c -> c.viewBobbingStrength))
                .add(percentSlider("hurt_camera_strength",
                        (c, v) -> c.hurtCameraStrength = v, c -> c.hurtCameraStrength))
                .add(percentSlider("vignette_strength",
                        (c, v) -> c.vignetteStrength = v, c -> c.vignetteStrength))
                .build();

        OptionGroup clouds = OptionGroup.createBuilder()
                .setId(id("clouds"))
                .add(slider("cloud_render_distance", 0, 1536, 96,
                        (c, v) -> c.cloudRenderDistance = v, c -> c.cloudRenderDistance,
                        value -> value == 0 ? text("value.vanilla") : text("value.blocks", value)).build())
                .add(slider("cloud_height_offset", -128, 128, 4,
                        (c, v) -> c.cloudHeightOffset = v, c -> c.cloudHeightOffset,
                        value -> value == 0 ? text("value.vanilla") : text("value.signed_blocks", value)).build())
                .add(slider("cloud_speed", 0, 200, 5,
                        (c, v) -> c.cloudSpeed = v, c -> c.cloudSpeed,
                        ControlValueFormatter.percentage()).build())
                .add(toggle("cloud_fog", (c, v) -> c.cloudFog = v, c -> c.cloudFog))
                .build();

        OptionGroup fog = OptionGroup.createBuilder()
                .setId(id("fog"))
                .add(percentSlider("terrain_fog_density",
                        (c, v) -> c.terrainFogDensity = v, c -> c.terrainFogDensity))
                .add(percentSlider("fluid_fog_density",
                        (c, v) -> c.fluidFogDensity = v, c -> c.fluidFogDensity))
                .build();

        OptionGroup sky = OptionGroup.createBuilder()
                .setId(id("sky"))
                .add(toggle("sky", (c, v) -> c.sky = v, c -> c.sky))
                .add(toggle("lower_sky", (c, v) -> c.lowerSky = v, c -> c.lowerSky))
                .add(toggle("sun_and_moon", (c, v) -> c.sunAndMoon = v, c -> c.sunAndMoon))
                .add(toggle("stars", (c, v) -> c.stars = v, c -> c.stars))
                .build();

        OptionGroup weather = OptionGroup.createBuilder()
                .setId(id("weather"))
                .add(slider("weather_render_distance", 0, 15, 1,
                        (c, v) -> c.weatherRenderDistance = v, c -> c.weatherRenderDistance,
                        value -> value == 0 ? text("value.vanilla") : text("value.blocks", value)).build())
                .add(percentSlider("weather_density",
                        (c, v) -> c.weatherDensity = v, c -> c.weatherDensity))
                .build();

        OptionGroup leaves = OptionGroup.createBuilder()
                .setId(id("leaves"))
                .add(slider("leaf_quality", 0, LeafQuality.values().length - 1, 1,
                        (c, v) -> c.leafQuality = LeafQuality.values()[v], c -> c.leafQuality.ordinal(),
                        v -> text(LeafQuality.key(v)))
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build();

        OptionGroup quality = OptionGroup.createBuilder()
                .setId(id("quality"))
                .add(toggle("fxaa", (c, v) -> c.fxaa = v, c -> c.fxaa))
                .add(toggle("smooth_block_outlines", (c, v) -> c.smoothBlockOutlines = v, c -> c.smoothBlockOutlines))
                .add(toggle("scaled_block_outline", (c, v) -> c.scaledBlockOutlineWidth = v, c -> c.scaledBlockOutlineWidth))
                .add(slider("block_outline_width", 10, 50, 1,
                        (c, v) -> c.blockOutlineWidth = v / 10.0f, c -> (int) (c.blockOutlineWidth * 10.0f),
                        value -> text(value == 10 ? "value.pixel" : "value.pixels", value / 10.0f)).build())
                .build();

        OptionGroup misc = OptionGroup.createBuilder()
                .setId(id("misc"))
                .add(toggle("disable_realms", (c, v) -> c.disableRealms = v, c -> c.disableRealms))
                .add(toggle("disable_text_shadows", (c, v) -> c.disableTextShadows = v, c -> c.disableTextShadows))
                .add(toggle("high_dpi_screen", (c, v) -> c.highDpiScreen = v, c -> c.highDpiScreen, OptionFlag.REQUIRES_GAME_RESTART))
                .addConditionally(
                        Platform.get() == Platform.MACOSX,
                        () -> toggle("macos_smooth_scrolling", (c, v) -> c.macosSmoothScrolling = v, c -> c.macosSmoothScrolling, OptionFlag.REQUIRES_GAME_RESTART)
                )
                .addConditionally(
                        Platform.get() == Platform.MACOSX,
                        () -> toggle("macos_right_click_emulation", (c, v) -> c.macosRightClickEmulation = v, c -> c.macosRightClickEmulation, OptionFlag.REQUIRES_GAME_RESTART)
                )
                .build();

        OptionGroup entities = OptionGroup.createBuilder()
                .setId(id("entities"))
                .add(slider("entity_shadow_distance", 8, 128, 8,
                        (c, v) -> c.entityShadowDistance = v, c -> c.entityShadowDistance,
                        value -> text("value.blocks", value)).build())
                .build();

        OptionGroup equipment = OptionGroup.createBuilder()
                .setId(id("equipment"))
                .add(toggle("armor", (c, v) -> c.armor = v, c -> c.armor))
                .add(toggle("armor_glint", (c, v) -> c.armorGlint = v, c -> c.armorGlint))
                .add(toggle("held_items", (c, v) -> c.heldItems = v, c -> c.heldItems))
                .add(toggle("worn_heads", (c, v) -> c.wornHeads = v, c -> c.wornHeads))
                .build();

        OptionGroup playerCosmetics = OptionGroup.createBuilder()
                .setId(id("player_cosmetics"))
                .add(toggle("capes", (c, v) -> c.capes = v, c -> c.capes))
                .add(toggle("player_ears", (c, v) -> c.playerEars = v, c -> c.playerEars))
                .build();

        OptionGroup attachments = OptionGroup.createBuilder()
                .setId(id("attachments"))
                .add(toggle("stuck_arrows", (c, v) -> c.stuckArrows = v, c -> c.stuckArrows))
                .add(toggle("leashes", (c, v) -> c.leashes = v, c -> c.leashes))
                .add(toggle("name_tags", (c, v) -> c.nameTags = v, c -> c.nameTags))
                .add(toggle("second_name_tag_layer", (c, v) -> c.secondNameTagLayer = v, c -> c.secondNameTagLayer))
                .build();

        return List.of(
                new OptionPage(id("environment"), text("pages.environment"), List.of(clouds, fog, sky, weather, leaves)),
                new OptionPage(id("camera_effects"), text("pages.camera_effects"), List.of(camera)),
                new OptionPage(id("particles"), text("pages.particles"), List.of(particles)),
                new OptionPage(id("debug_hud"), text("pages.debug_hud"), List.of(debug)),
                new OptionPage(id("entities"), text("pages.entities"), List.of(entities, equipment, playerCosmetics, attachments)),
                new OptionPage(id("quality"), text("pages.quality"), List.of(quality)),
                new OptionPage(id("misc"), text("pages.misc"), List.of(misc))
        );
    }

    private static OptionImpl<ArgentumExtrasConfig, Boolean> toggle(String name,
            BiConsumer<ArgentumExtrasConfig, Boolean> setter, Function<ArgentumExtrasConfig, Boolean> getter, OptionFlag... flags) {
        return OptionImpl.createBuilder(boolean.class, ArgentumExtras.CONFIG_STORAGE)
                .setId(id(name))
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter)
                .setFlags(flags)
                .build();
    }

    private static OptionImpl<ArgentumExtrasConfig, Integer> percentSlider(String name,
            BiConsumer<ArgentumExtrasConfig, Integer> setter, Function<ArgentumExtrasConfig, Integer> getter) {
        return slider(name, 0, 100, 5, setter, getter, ControlValueFormatter.percentage()).build();
    }

    private static OptionImpl.Builder<ArgentumExtrasConfig, Integer> slider(String name, int min, int max, int step,
            BiConsumer<ArgentumExtrasConfig, Integer> setter, Function<ArgentumExtrasConfig, Integer> getter,
            ControlValueFormatter formatter) {
        return OptionImpl.createBuilder(int.class, ArgentumExtras.CONFIG_STORAGE)
                .setId(id(name))
                .setControl(option -> new SliderControl(option, min, max, step, formatter))
                .setBinding(setter, getter);
    }

    private static <T> OptionIdentifier<T> id(String path) {
        return OptionIdentifier.create(MOD_ID, path).cast();
    }

    private static TextComponent text(String path, Object... args) {
        return TextComponent.translatable(MOD_ID + ".options." + path, args);
    }
}
