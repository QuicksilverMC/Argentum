package dev.rdh.argentum.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.lwjgl.opengl.Display;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.api.options.structure.StandardOptions;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.config.ArgentumConfig;
import dev.rdh.argentum.impl.config.JsonOptionStorage;
import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService.FogShape;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ArgentumOptionPages {
    private static final GameOptionsStorage VANILLA = new GameOptionsStorage();
    private static final ArgentumConfig CONFIG = Argentum.CONFIG;
    private static final JsonOptionStorage<ArgentumConfig> CONFIG_STORAGE = Argentum.CONFIG_STORAGE;

    public static List<OptionPage> create() {
        return List.of(
                general(),
                quality(),
                performance(),
                advanced()
        );
    }

    private static final class Group {
        private static final OptionIdentifier<Void> RENDERING = OptionIdentifier.create(Argentum.ID, "rendering");
        private static final OptionIdentifier<Void> WINDOW = OptionIdentifier.create(Argentum.ID, "window");
        private static final OptionIdentifier<Void> INDICATORS = OptionIdentifier.create(Argentum.ID, "indicators");
        private static final OptionIdentifier<Void> GRAPHICS = OptionIdentifier.create(Argentum.ID, "graphics");
        private static final OptionIdentifier<Void> DETAILS = OptionIdentifier.create(Argentum.ID, "details");
        private static final OptionIdentifier<Void> CHUNK_UPDATES = OptionIdentifier.create(Argentum.ID, "chunk_updates");
        private static final OptionIdentifier<Void> RENDERING_CULLING = OptionIdentifier.create(Argentum.ID, "rendering_culling");
        private static final OptionIdentifier<Void> CPU_SAVING = OptionIdentifier.create(Argentum.ID, "cpu_saving");
        private static final OptionIdentifier<Void> DIAGNOSTICS = OptionIdentifier.create(Argentum.ID, "diagnostics");
    }

    private static final class Page {
        private static final OptionIdentifier<Void> GENERAL = OptionIdentifier.create(Argentum.ID, "general");
        private static final OptionIdentifier<Void> QUALITY = OptionIdentifier.create(Argentum.ID, "quality");
        private static final OptionIdentifier<Void> PERFORMANCE = OptionIdentifier.create(Argentum.ID, "performance");
        private static final OptionIdentifier<Void> ADVANCED = OptionIdentifier.create(Argentum.ID, "advanced");
    }

    private static final class Option {
        private static final OptionIdentifier<Void> ANAGLYPH = OptionIdentifier.create("minecraft", "anaglyph");
        private static final OptionIdentifier<Void> ENTITY_OCCLUSION_INTERVAL = OptionIdentifier.create(Argentum.ID, "entity_occlusion_interval");
        private static final OptionIdentifier<Void> PARTICLE_CULLING = OptionIdentifier.create(Argentum.ID, "particle_culling");
        private static final OptionIdentifier<Void> ENTITY_INSTANCING = OptionIdentifier.create(Argentum.ID, "entity_instancing");
        private static final OptionIdentifier<Void> FONT_BATCHING = OptionIdentifier.create(Argentum.ID, "font_batching");
        private static final OptionIdentifier<Void> GUI_ITEM_ATLAS = OptionIdentifier.create(Argentum.ID, "gui_item_atlas");
        private static final OptionIdentifier<Void> SAFE_CHUNK_EDGES = OptionIdentifier.create(Argentum.ID, "safe_chunk_edges");
        private static final OptionIdentifier<Void> CHECK_GL_ERRORS = OptionIdentifier.create(Argentum.ID, "check_gl_errors");
        private static final OptionIdentifier<Void> GREEDY_RENDER_THREAD = OptionIdentifier.create(Argentum.ID, "greedy_render_thread");
        private static final OptionIdentifier<Void> FOG_SHAPE = OptionIdentifier.create(Argentum.ID, "fog_shape");
    }

    private ArgentumOptionPages() {
    }

    static OptionPage general() {
        OptionGroup rendering = OptionGroup.createBuilder()
                .setId(Group.RENDERING)
                .add(option(int.class, VANILLA, StandardOptions.Option.RENDER_DISTANCE)
                        .setName(vanilla("options.renderDistance"))
                        .setControl(option -> new SliderControl(option, 2, 32, 1, value -> vanilla("options.chunks", value)))
                        .setBinding((options, value) -> options.viewDistance = value, options -> options.viewDistance)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(int.class, VANILLA, StandardOptions.Option.BRIGHTNESS)
                        .setName(vanilla("options.gamma"))
                        .setControl(option -> new SliderControl(option, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((options, value) -> options.gamma = value / 100.0F,
                                options -> Math.round(options.gamma * 100.0F))
                        .build())
                .build();

        OptionGroup window = OptionGroup.createBuilder()
                .setId(Group.WINDOW)
                .add(option(int.class, VANILLA, StandardOptions.Option.GUI_SCALE)
                        .setName(vanilla("options.guiScale"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.guiScale()))
                        .setBinding((options, value) -> {
                            options.guiScale = value;
                            Minecraft minecraft = Minecraft.getInstance();
                            Window scaledWindow = new Window(minecraft);
                            minecraft.screen.resize(minecraft, scaledWindow.getWidth(), scaledWindow.getHeight());
                        }, options -> options.guiScale)
                        .build())
                .add(option(boolean.class, VANILLA, StandardOptions.Option.FULLSCREEN)
                        .setName(vanilla("options.fullscreen"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> {
                            options.fullscreen = value;
                            Minecraft minecraft = Minecraft.getInstance();
                            if (minecraft.isFullscreen() != value) {
                                minecraft.toggleFullscreen();
                                options.fullscreen = minecraft.isFullscreen();
                            }
                        }, options -> options.fullscreen)
                        .build())
                .add(option(int.class, VANILLA, StandardOptions.Option.MAX_FRAMERATE)
                        .setName(vanilla("options.framerateLimit"))
                        .setControl(option -> new SliderControl(option, Argentum.CONFIG.explicitVsyncOption ? 5 : 0, 260, 5,
                                value -> {
                                    if (value == 0 || (Argentum.CONFIG.explicitVsyncOption && Minecraft.getInstance().options.vsync)) {
                                        return text("value.vsync");
                                    } else if (value == 260) {
                                        return vanilla("options.framerateLimit.max");
                                    } else {
                                        return text("value.fps", value);
                                    }
								}))
                        .setBinding((options, value) -> {
                            if (!Argentum.CONFIG.explicitVsyncOption) {
                                options.vsync = value == 0;
                                Display.setVSyncEnabled(options.vsync);
                                if (options.vsync) {
                                    options.fpsLimit = 260;
                                    return;
                                }
                            }
                            options.fpsLimit = value;
                        }, options -> options.vsync && !Argentum.CONFIG.explicitVsyncOption ? 0
                                : (options.fpsLimit <= 0 ? 260 : options.fpsLimit))
                        .setImpact(OptionImpact.VARIES)
                        .setEnabledPredicate(() -> !Argentum.CONFIG.explicitVsyncOption || !Minecraft.getInstance().options.vsync)
                        .build())
                .addConditionally(Argentum.CONFIG.explicitVsyncOption, () -> option(boolean.class, VANILLA, StandardOptions.Option.VSYNC)
                        .setName(vanilla("options.vsync"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> {
                            options.vsync = value;
                            Display.setVSyncEnabled(value);
                        }, options -> options.vsync)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .build();

        OptionGroup indicators = OptionGroup.createBuilder()
                .setId(Group.INDICATORS)
                .add(option(boolean.class, VANILLA, StandardOptions.Option.VIEW_BOBBING)
                        .setName(vanilla("options.viewBobbing"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.viewBobbing = value, options -> options.viewBobbing)
                        .build())
                .build();

        return page(Page.GENERAL, rendering, window, indicators);
    }

    static OptionPage quality() {
        OptionGroup graphics = OptionGroup.createBuilder()
                .setId(Group.GRAPHICS)
                .add(option(boolean.class, VANILLA, StandardOptions.Option.GRAPHICS_MODE)
                        .setName(vanilla("options.graphics"))
                        .setControl(option -> new CyclingControl<>(option, new Boolean[]{false, true}, vanillaValues("options.graphics.fast", "options.graphics.fancy")))
                        .setBinding((options, value) -> options.fancyGraphics = value, options -> options.fancyGraphics)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(boolean.class, VANILLA, Option.ANAGLYPH)
                        .setName(vanilla("options.anaglyph"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.anaglyph = value, options -> options.anaglyph)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD, OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build();

        OptionGroup details = OptionGroup.createBuilder()
                .setId(Group.DETAILS)
                .add(option(int.class, VANILLA, StandardOptions.Option.CLOUDS)
                        .setName(vanilla("options.renderClouds"))
                        .setControl(option -> new CyclingControl<>(option,
                                new Integer[]{0, 1, 2}, vanillaValues("options.off", "options.graphics.fast", "options.graphics.fancy")))
                        .setBinding((options, value) -> options.cloudRenderMode = value,
                                options -> options.cloudRenderMode)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(option(int.class, VANILLA, StandardOptions.Option.PARTICLES)
                        .setName(vanilla("options.particles"))
                        .setControl(option -> new CyclingControl<>(option,
                                new Integer[]{0, 1, 2}, vanillaValues("options.particles.all", "options.particles.decreased", "options.particles.minimal")))
                        .setBinding((options, value) -> options.particles = value, options -> options.particles)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(option(int.class, VANILLA, StandardOptions.Option.SMOOTH_LIGHT)
                        .setName(vanilla("options.ao"))
                        .setControl(option -> new CyclingControl<>(option,
                                new Integer[]{0, 1, 2}, vanillaValues("options.ao.off", "options.ao.min", "options.ao.max")))
                        .setBinding((options, value) -> options.ambientOcclusion = value,
                                options -> options.ambientOcclusion)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(FogShape.class, CONFIG_STORAGE, Option.FOG_SHAPE)
                        .setControl(option -> new CyclingControl<>(option, FogShape.class,
								new TextComponent[]{
										text("value.spherical"),
										text("value.cylindrical"),
                                        text("value.planar")
								}))
                        .setBinding((config, value) -> config.fogShape = value, config -> config.fogShape)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(int.class, CONFIG_STORAGE, StandardOptions.Option.BIOME_BLEND)
                        .setControl(option -> new SliderControl(option, 0, 14, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((config, value) -> config.biomeBlendRadius = value, config -> config.biomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(int.class, CONFIG_STORAGE, StandardOptions.Option.CHUNK_FADE_IN_DURATION)
                        .setControl(option -> new SliderControl(option, 0, 2000, 100,
                                value -> optionText(StandardOptions.Option.CHUNK_FADE_IN_DURATION, "value", value)))
                        .setBinding((config, value) -> config.chunkFadeInDuration = value,
                                config -> config.chunkFadeInDuration)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(option(boolean.class, VANILLA, StandardOptions.Option.ENTITY_SHADOWS)
                        .setName(vanilla("options.entityShadows"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.entityShadows = value, options -> options.entityShadows)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(option(int.class, VANILLA, StandardOptions.Option.MIPMAP_LEVEL)
                        .setName(vanilla("options.mipmapLevels"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.number()))
                        .setBinding((options, value) -> options.mipmapLevels = value, options -> options.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build();

        return page(Page.QUALITY, graphics, details);
    }

    static OptionPage performance() {
        OptionGroup chunkUpdates = OptionGroup.createBuilder()
                .setId(Group.CHUNK_UPDATES)
                .add(option(int.class, CONFIG_STORAGE, StandardOptions.Option.CHUNK_UPDATE_THREADS)
                        .setControl(option -> new SliderControl(option, 0, ChunkBuilder.getMaxThreadCount(), 1,
                                value -> value == 0 ? text("value.auto") : text("value.threads", value)))
                        .setBinding((config, value) -> config.chunkBuilderThreads = value,
                                config -> config.chunkBuilderThreads)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(toggle(StandardOptions.Option.DEFFER_CHUNK_UPDATES,
                        OptionImpact.HIGH,
                        (config, value) -> config.deferChunkUpdates = value, config -> config.deferChunkUpdates,
                        OptionFlag.REQUIRES_RENDERER_UPDATE))
                .add(option(AsyncOcclusionMode.class, CONFIG_STORAGE, StandardOptions.Option.ASYNC_GRAPH_SEARCH)
                        .setControl(option -> new CyclingControl<>(option, AsyncOcclusionMode.class, new TextComponent[] {
                                text("value.off"), text("value.shadows_only"), text("value.everything")
                        }))
                        .setBinding((config, value) -> config.asyncOcclusion = value, config -> config.asyncOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build();

        OptionGroup culling = OptionGroup.createBuilder()
                .setId(Group.RENDERING_CULLING)
                .add(toggle(StandardOptions.Option.BLOCK_FACE_CULLING,
                        OptionImpact.MEDIUM,
                        (config, value) -> config.blockFaceCulling = value, config -> config.blockFaceCulling,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(StandardOptions.Option.RASTER_OCCLUSION_CULLING,
                        OptionImpact.VARIES,
                        (config, value) -> config.rasterOcclusionCulling = value, config -> config.rasterOcclusionCulling,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(StandardOptions.Option.FOG_OCCLUSION,
                        OptionImpact.MEDIUM,
                        (config, value) -> config.fogCulling = value, config -> config.fogCulling,
                        OptionFlag.REQUIRES_RENDERER_UPDATE))
                .add(toggle(StandardOptions.Option.ENTITY_CULLING,
                        OptionImpact.MEDIUM,
                        (config, value) -> config.entityCulling = value, config -> config.entityCulling))
                .add(option(int.class, CONFIG_STORAGE, Option.ENTITY_OCCLUSION_INTERVAL)
                        .setControl(option -> new SliderControl(option, 0, 500, 10,
                                value -> text("value.milliseconds", value)))
                        .setBinding((config, value) -> config.entityOcclusionIntervalMs = value,
                                config -> config.entityOcclusionIntervalMs)
                        .setEnabledPredicate(() -> CONFIG.entityCulling)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(toggle(Option.PARTICLE_CULLING, OptionImpact.LOW, (config7, value4) -> config7.particleCulling = value4, config8 -> config8.particleCulling))
                .build();

        OptionGroup rendering = OptionGroup.createBuilder()
                .setId(Group.RENDERING)
                .add(toggle(Option.ENTITY_INSTANCING, OptionImpact.HIGH, (config5, value3) -> config5.entityInstancing = value3, config6 -> config6.entityInstancing))
                .add(toggle(StandardOptions.Option.ANIMATE_VISIBLE_TEXTURES,
                        OptionImpact.HIGH,
                        (config, value) -> config.animateOnlyVisibleTextures = value,
                        config -> config.animateOnlyVisibleTextures, OptionFlag.REQUIRES_RENDERER_UPDATE))
                .add(toggle(Option.FONT_BATCHING, OptionImpact.MEDIUM, (config3, value2) -> config3.fontBatching = value2, config4 -> config4.fontBatching))
                .add(toggle(Option.GUI_ITEM_ATLAS, OptionImpact.MEDIUM,
                        (config, value) -> config.guiItemAtlas = value, config -> config.guiItemAtlas))
                .add(toggle(StandardOptions.Option.TRANSLUCENT_FACE_SORTING,
                        OptionImpact.VARIES,
                        (config, value) -> config.translucencySorting = value,
                        config -> config.translucencySorting, OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(StandardOptions.Option.USE_FASTER_CLOUDS,
                        OptionImpact.LOW,
                        (config, value) -> config.fasterClouds = value, config -> config.fasterClouds))
                .add(toggle(Option.SAFE_CHUNK_EDGES, OptionImpact.LOW, (config1, value1) -> config1.safeChunkEdges = value1, config2 -> config2.safeChunkEdges, OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(StandardOptions.Option.RENDER_PASS_OPTIMIZATION,
                        OptionImpact.LOW,
                        (config, value) -> config.renderPassOptimization = value,
                        config -> config.renderPassOptimization, OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(StandardOptions.Option.COMPACT_VERTEX_FORMAT,
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactVertexFormat = value, config -> config.compactVertexFormat,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(toggle(Option.GREEDY_RENDER_THREAD,
                        OptionImpact.MEDIUM,
                        (config, value) -> config.greedyRenderThread = value, config -> config.greedyRenderThread))
                .build();

        return page(Page.PERFORMANCE, chunkUpdates, culling, rendering);
    }

    static OptionPage advanced() {
        OptionGroup cpuSaving = OptionGroup.createBuilder()
                .setId(Group.CPU_SAVING)
                .add(option(int.class, CONFIG_STORAGE, StandardOptions.Option.CPU_FRAMES_AHEAD)
                        .setControl(option -> new SliderControl(option, 0, 9, 1,
                                value -> optionText(StandardOptions.Option.CPU_FRAMES_AHEAD, "value", value)))
                        .setBinding((config, value) -> config.cpuRenderAheadLimit = value,
                                config -> config.cpuRenderAheadLimit)
                        .build())
                .build();

        OptionGroup diagnostics = OptionGroup.createBuilder()
                .setId(Group.DIAGNOSTICS)
                .add(toggle(Option.CHECK_GL_ERRORS, OptionImpact.LOW, (config, value) -> config.checkGlErrors = value, config1 -> config1.checkGlErrors, OptionFlag.REQUIRES_GAME_RESTART))
                .build();
        return page(Page.ADVANCED, cpuSaving, diagnostics);
    }

    private static OptionImpl<ArgentumConfig, Boolean> toggle(OptionIdentifier<?> id,
                                                              OptionImpact impact, BiConsumer<ArgentumConfig, Boolean> setter,
                                                              Function<ArgentumConfig, Boolean> getter, OptionFlag... flags) {
        return option(boolean.class, CONFIG_STORAGE, id)
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter)
                .setImpact(impact)
                .setFlags(flags)
                .build();
    }

    private static <S, T> OptionImpl.Builder<S, T> option(Class<T> type, OptionStorage<S> storage, OptionIdentifier<?> id) {
        return OptionImpl.createBuilder(type, storage)
                .setId(id.cast())
                .setName(optionText(id, "name"))
                .setTooltip(optionText(id, "tooltip"));
    }

    private static OptionPage page(OptionIdentifier<Void> id, OptionGroup... groups) {
        return new OptionPage(id, TextComponent.translatable("argentum.options.pages." + id.getPath()), List.of(groups));
    }

    private static TextComponent optionText(OptionIdentifier<?> id, String suffix, Object... args) {
        return TextComponent.translatable(id.getModId() + "." + id.getPath() + "." + suffix, args);
    }

    private static TextComponent text(String path, Object... args) {
        return TextComponent.translatable("argentum.options." + path, args);
    }

    private static TextComponent vanilla(String key, Object... args) {
        return TextComponent.translatable(key, args);
    }

    private static TextComponent[] vanillaValues(String... keys) {
        TextComponent[] components = new TextComponent[keys.length];
        for (int i = 0; i < keys.length; i++) {
            components[i] = vanilla(keys[i]);
        }
        return components;
    }
}
