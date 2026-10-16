package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.Argentum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class PortalBenchmark {
    private static final String SAVE_NAME = "Argentum Portal Comparison " + System.getProperty("argentum.portalBenchmarkHeightStep", "0");
    private static final long PORTAL_TIME = 350000L;
    private static final int PORTALS_PER_SIDE = 16;
    private static final int PORTAL_SPACING = 2;
    private static final int PORTAL_Y = 64;
    private static final int HEIGHT_STEP = Integer.getInteger("argentum.portalBenchmarkHeightStep", 0);
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = 91.0;
    private static final double CAMERA_Z = -40.0;
    private static final float CAMERA_YAW = 0.0F;
    private static final float CAMERA_PITCH = 35.0F;

    private static PortalBenchmark instance;

    private final Minecraft minecraft;
    private final String variant;
    private final Path output;
    private final long warmupNanos;
    private final long measureNanos;
    private final List<Long> samples = new ArrayList<>();
    private final List<Integer> portalCalls = new ArrayList<>();
    private boolean sceneQueued;
    private volatile boolean sceneReady;
    private boolean ready;
    private boolean measuring;
    private boolean done;
    private long frameStart;
    private long frameInterval;
    private long warmupStart;
    private long measureStart;
    private int framePortalCalls;
    private int frameShaderCalls;
    private int frameShaderPortals;
    private int frameDispatcherCalls;
    private int framePortalReturns;
    private static final String EFFECT = System.getProperty("argentum.portalBenchmarkEffect", "none");

    private PortalBenchmark(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.variant = System.getProperty("argentum.portalBenchmarkVariant", "vanilla");
        this.output = Path.of(System.getProperty("argentum.portalBenchmarkOutput", "/tmp/argentum-portal-benchmark"), this.variant);
        this.warmupNanos = secondsProperty("argentum.portalBenchmarkWarmupSeconds", 15) * 1_000_000_000L;
        this.measureNanos = secondsProperty("argentum.portalBenchmarkMeasureSeconds", 20) * 1_000_000_000L;
    }

    public static void start(Minecraft minecraft) {
        instance = new PortalBenchmark(minecraft);
        instance.configure();
        instance.prepareOutput();
        minecraft.startGame(SAVE_NAME, SAVE_NAME, new WorldSettings(31100L, WorldSettings.GameMode.CREATIVE, false, false,
                WorldGeneratorType.FLAT).setGeneratorOptions("3;minecraft:air;1;"));
    }

    public static void frameStart() {
        if (instance != null) instance.startFrame();
    }

    public static void frameEnd() {
        if (instance != null) instance.endFrame();
    }

    public static long portalTime() {
        return instance == null ? Minecraft.getTime() : PORTAL_TIME;
    }

    public static boolean effect(String name) {
        return EFFECT.equals(name) || EFFECT.equals("all") && java.util.Set.of("layers", "geometry", "tint", "uv", "texture", "dispatcher", "texgen").contains(name);
    }

    public static void recordPortalDispatch() { if (instance != null) instance.frameDispatcherCalls++; }
    public static void recordPortalReturn() { if (instance != null) instance.framePortalReturns++; }

    public static void recordShaderDraw(int portals) {
        if (instance != null) {
            instance.frameShaderCalls++;
            instance.frameShaderPortals += portals;
        }
    }

    public static void recordPortalRendererCall() {
        if (instance != null) instance.framePortalCalls++;
    }

    private static long secondsProperty(String name, int fallback) {
        return Long.parseLong(System.getProperty(name, Integer.toString(fallback)));
    }

    private void configure() {
        this.minecraft.options.vsync = false;
        this.minecraft.options.fpsLimit = (int)GameOptions.Option.FRAMERATE_LIMIT.getMax();
        this.minecraft.options.viewDistance = 8;
        this.minecraft.options.viewBobbing = false;
        this.minecraft.options.cloudRenderMode = 0;
        this.minecraft.options.hideGui = true;
        this.minecraft.options.pauseOnUnfocus = false;
        Display.setVSyncEnabled(false);
        this.setFastPortals(!"vanilla".equals(this.variant));
    }

    private void setFastPortals(boolean enabled) {
        try {
            Argentum.CONFIG.getClass().getField("fasterEndPortals").setBoolean(Argentum.CONFIG, enabled);
        } catch (NoSuchFieldException ignored) {
            if (enabled) throw new IllegalStateException("fasterEndPortals is unavailable");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not configure fasterEndPortals", e);
        }
    }

    private void prepareOutput() {
        try {
            Files.createDirectories(this.output);
            for (String marker : new String[]{"ready", "go", "done", "stop", "frames.csv", "summary.txt"}) {
                Files.deleteIfExists(this.output.resolve(marker));
            }
            Files.writeString(this.output.resolve("pid"), Long.toString(ProcessHandle.current().pid()) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare " + this.output, e);
        }
    }

    private void startFrame() {
        long now = System.nanoTime();
        this.frameInterval = now - this.frameStart;
        this.frameStart = now;
        this.framePortalCalls = 0;
        this.frameShaderCalls = 0;
        this.frameShaderPortals = 0;
        this.frameDispatcherCalls = 0;
        this.framePortalReturns = 0;
        this.holdCamera();
    }

    private void endFrame() {
        this.queueScene();
        if (!this.sceneReady) return;

        long now = System.nanoTime();
        if (!this.ready) {
            if (this.warmupStart == 0L) this.warmupStart = now;
            if (now - this.warmupStart >= this.warmupNanos) {
                this.verifyScene();
                this.captureScene();
                this.touch("ready");
                this.ready = true;
            }
            return;
        }

        if (!this.measuring) {
            if (Files.exists(this.output.resolve("go"))) {
                this.measuring = true;
                this.measureStart = now;
            }
            return;
        }

        if (!this.done) {
            this.verifyScene();
            this.samples.add(this.frameInterval);
            this.portalCalls.add(this.framePortalCalls);
            if (now - this.measureStart >= this.measureNanos) {
                this.writeResults();
                this.touch("done");
                this.done = true;
            }
            return;
        }

        if (Files.exists(this.output.resolve("stop"))) this.minecraft.stop();
    }

    private void verifyScene() {
        int expected = effect("cancel") ? 240 : PORTALS_PER_SIDE * PORTALS_PER_SIDE;
        if ("vanilla".equals(this.variant)) {
            if (this.framePortalCalls != expected || this.frameShaderCalls != 0) {
                throw new IllegalStateException("Unexpected vanilla portal draws: renderer=" + this.framePortalCalls + ", shader=" + this.frameShaderCalls);
            }
        } else if ("none".equals(EFFECT) && this.frameShaderPortals != 2 * expected) {
            // Every portal should be drawn by exactly two replayed runs: the sky layer and the merged additive layers.
            throw new IllegalStateException("Portals not fully replayed: renderer=" + this.framePortalCalls
                    + ", shaderDraws=" + this.frameShaderCalls + ", shaderPortals=" + this.frameShaderPortals);
        }
    }

    private void queueScene() {
        if (this.sceneQueued || this.minecraft.world == null || this.minecraft.player == null) return;
        IntegratedServer server = this.minecraft.getServer();
        if (server == null || !server.isLoading()) return;
        this.sceneQueued = true;
        this.minecraft.openScreen(null);
        server.executeTask(() -> {
            for (var player : server.getPlayerManager().getAll()) {
                player.abilities.flying = true;
                player.syncAbilities();
                player.networkHandler.teleport(CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
            }
            int start = -(PORTALS_PER_SIDE * PORTAL_SPACING) / 2;
            for (int x = 0; x < PORTALS_PER_SIDE; x++) {
                for (int z = 0; z < PORTALS_PER_SIDE; z++) {
                    server.getWorld(0).setBlockState(new BlockPos(start + x * PORTAL_SPACING, PORTAL_Y + z * HEIGHT_STEP, start + z * PORTAL_SPACING),
                            Blocks.END_PORTAL.defaultState(), 2);
                }
            }
            server.getWorld(0).getGameRules().set("doDaylightCycle", "false");
            server.getWorld(0).getGameRules().set("doMobSpawning", "false");
            server.getWorld(0).getGameRules().set("randomTickSpeed", "0");
            server.getWorld(0).setTimeOfDay(6000L);
            server.getWorld(0).getData().setRaining(false);
            server.getWorld(0).getData().setThundering(false);
            this.sceneReady = true;
        });
    }

    private void holdCamera() {
        PlayerEntity player = this.minecraft.player;
        if (player == null) return;
        player.setPositionAndAngles(CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
        player.prevX = player.lastX = CAMERA_X;
        player.prevY = player.lastY = CAMERA_Y;
        player.prevZ = player.lastZ = CAMERA_Z;
        player.lastYaw = CAMERA_YAW;
        player.lastPitch = CAMERA_PITCH;
        player.velocityX = 0.0;
        player.velocityY = 0.0;
        player.velocityZ = 0.0;
        player.noClip = true;
        player.abilities.canFly = true;
        player.abilities.flying = true;
        this.minecraft.setCamera(player);
    }

    private void captureScene() {
        String name = "portal-benchmark-scene.png";
        Screenshot.take(this.minecraft.gameDir, name, this.minecraft.width, this.minecraft.height, this.minecraft.getRenderTarget());
        Path screenshot = this.minecraft.gameDir.toPath().resolve("screenshots").resolve(name);
        try {
            Files.move(screenshot, this.output.resolve("scene.png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save portal benchmark screenshot", e);
        }
    }

    private void writeResults() {
        if (this.samples.isEmpty()) throw new IllegalStateException("No portal benchmark frames were captured");
        List<Long> sorted = new ArrayList<>(this.samples);
        Collections.sort(sorted);
        long total = 0;
        StringBuilder csv = new StringBuilder("frame,nanoseconds,milliseconds\n");
        for (int index = 0; index < this.samples.size(); index++) {
            long sample = this.samples.get(index);
            total += sample;
            csv.append(index + 1).append(',').append(sample).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", sample / 1_000_000.0)).append('\n');
        }
        List<Integer> sortedPortalCalls = new ArrayList<>(this.portalCalls);
        Collections.sort(sortedPortalCalls);
        String summary = String.format(Locale.ROOT,
                "variant=%s%nresolution=%dx%d%ngrid=%dx%d%ngridSpacing=%d%nportals=%d%nframes=%d%nmeanNanos=%d%nmedianNanos=%d%np95Nanos=%d%np99Nanos=%d%nminNanos=%d%nmaxNanos=%d%nmeanPortalRendererCalls=%.2f%nminPortalRendererCalls=%d%nmaxPortalRendererCalls=%d%n",
                this.variant, this.minecraft.width, this.minecraft.height, PORTALS_PER_SIDE, PORTALS_PER_SIDE, PORTAL_SPACING,
                PORTALS_PER_SIDE * PORTALS_PER_SIDE, sorted.size(), total / sorted.size(), percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 0.99),
                sorted.get(0), sorted.get(sorted.size() - 1), this.portalCalls.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                sortedPortalCalls.get(0), sortedPortalCalls.get(sortedPortalCalls.size() - 1));
        summary += "glRenderer=" + GL11.glGetString(GL11.GL_RENDERER) + "\n"
                + "glVersion=" + GL11.glGetString(GL11.GL_VERSION) + "\n"
                + "shaderDrawsPerFrame=" + this.frameShaderCalls + "\n"
                + "shaderPortalsPerFrame=" + this.frameShaderPortals + "\n"
                + "portalRendererCallsPerFrame=" + this.framePortalCalls + "\n"
                + "heightStep=" + HEIGHT_STEP + "\n"
                + "effect=" + EFFECT + "\n"
                + "dispatcherCallsPerFrame=" + this.frameDispatcherCalls + "\n"
                + "portalReturnsPerFrame=" + this.framePortalReturns + "\n";
        try {
            Files.writeString(this.output.resolve("frames.csv"), csv, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(this.output.resolve("summary.txt"), summary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write portal benchmark results", e);
        }
        System.out.print("Argentum portal benchmark\n" + summary);
    }

    private static long percentile(List<Long> samples, double percentile) {
        return samples.get(Math.min(samples.size() - 1, (int)Math.ceil(samples.size() * percentile) - 1));
    }

    private void touch(String name) {
        try {
            Files.writeString(this.output.resolve(name), "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + name, e);
        }
    }
}
