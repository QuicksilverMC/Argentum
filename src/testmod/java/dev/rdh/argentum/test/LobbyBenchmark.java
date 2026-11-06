package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.test.mixin.ParticleAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class LobbyBenchmark {
    private static final String SAVE_NAME = "Argentum Lobby Benchmark";
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = 9.0;
    private static final double CAMERA_Z = -6.0;
    private static final float CAMERA_PITCH = 12.0F;
    private static final int GROUND_Y = 4;
    private static final int HOLOGRAM_STACKS = 160;
    private static final int INVISIBLE_MOBS = 256;
    private static final int VISIBLE_MOBS = 64;
    private static final int PARTICLES = 3000;
    private static final String[][] HOLOGRAM_LINES = {
            {"§e§lCLICK TO PLAY", "§bBed Wars", "§7Solo §8| §7Doubles", "§e%,d Playing"},
            {"§6§lNEW!", "§aSkyWars", "§7Insane Mode", "§e%,d Playing"},
            {"§d§lDAILY REWARD", "§fCollect your reward!", "§7Streak: §a%d days", "§8Right click"},
            {"§c§lMURDER MYSTERY", "§7Classic", "§k||§r §eHoliday Event §k||", "§e%,d Playing"},
    };
    private static final int[] PARTICLE_TYPES = {
            ParticleType.REDSTONE.getId(), ParticleType.FLAME.getId(), ParticleType.VILLAGER_HAPPY.getId(), ParticleType.HEART.getId(),
            ParticleType.CRIT.getId(), ParticleType.NOTE.getId(), ParticleType.SPELL_MOB.getId(), ParticleType.ENCHANTMENT_TABLE.getId()
    };

    private static LobbyBenchmark instance;

    private final Minecraft minecraft;
    private final Set<String> scene;
    private final List<String> variants;
    private final Path output;
    private final long warmupNanos;
    private final long settleNanos;
    private final long phaseNanos;
    private final int rounds;
    private final Random random = new Random(1789L);
    private final List<Particle> particles = new ArrayList<>();
    private final List<Phase> phases = new ArrayList<>();
    private boolean sceneQueued;
    private volatile boolean worldReady;
    private boolean spawned;
    private boolean ready;
    private boolean done;
    private long frameStart;
    private long frameInterval;
    private long frameEntityNanos;
    private long frameParticleNanos;
    private long warmupStart;
    private int phaseIndex = -1;
    private long phaseStart;
    private String variant = "baseline";

    private LobbyBenchmark(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.scene = Set.of(System.getProperty("argentum.lobbyBenchmarkScene", "holograms").split(","));
        this.variants = List.of(System.getProperty("argentum.lobbyBenchmarkVariants", "baseline").split(","));
        this.output = Path.of(System.getProperty("argentum.lobbyBenchmarkOutput", "/tmp/argentum-lobby-benchmark"));
        this.warmupNanos = Long.getLong("argentum.lobbyBenchmarkWarmupSeconds", 15L) * 1_000_000_000L;
        this.settleNanos = Long.getLong("argentum.lobbyBenchmarkSettleSeconds", 3L) * 1_000_000_000L;
        this.phaseNanos = Long.getLong("argentum.lobbyBenchmarkPhaseSeconds", 8L) * 1_000_000_000L;
        this.rounds = Integer.getInteger("argentum.lobbyBenchmarkRounds", 3);
    }

    public static boolean enabled() {
        return System.getProperty("argentum.lobbyBenchmarkScene") != null;
    }

    public static void start(Minecraft minecraft) {
        instance = new LobbyBenchmark(minecraft);
        instance.configure();
        instance.prepareOutput();
        minecraft.startGame(SAVE_NAME, SAVE_NAME, new WorldSettings(31100L, WorldSettings.GameMode.CREATIVE, false, false,
                WorldGeneratorType.FLAT).setGeneratorOptions("3;minecraft:bedrock,2*minecraft:stone,minecraft:grass;0;"));
    }

    public static boolean variant(String name) {
        return instance != null && instance.variant.equals(name);
    }

    public static void frameStart() {
        if (instance != null) instance.startFrame();
    }

    public static void frameEnd() {
        if (instance != null) instance.endFrame();
    }

    public static void recordEntities(long nanos) {
        if (instance != null) instance.frameEntityNanos += nanos;
    }

    public static void recordParticles(long nanos) {
        if (instance != null) instance.frameParticleNanos += nanos;
    }

    public static void tick() {
        if (instance != null && instance.spawned) instance.holdParticles();
    }

    private void configure() {
        GameOptions options = this.minecraft.options;
        options.vsync = false;
        options.fpsLimit = (int) GameOptions.Option.FRAMERATE_LIMIT.getMax();
        options.viewDistance = Integer.getInteger("argentum.lobbyBenchmarkViewDistance", 6);
        options.viewBobbing = false;
        options.cloudRenderMode = 0;
        options.hideGui = true;
        options.pauseOnUnfocus = false;
        options.particles = 0;
        Display.setVSyncEnabled(false);
    }

    private void prepareOutput() {
        try {
            Files.createDirectories(this.output);
            for (String marker : new String[]{"ready", "go", "done", "stop", "phases.csv", "summary.txt"}) {
                Files.deleteIfExists(this.output.resolve(marker));
            }
            Files.writeString(this.output.resolve("pid"), ProcessHandle.current().pid() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare " + this.output, e);
        }
    }

    private void startFrame() {
        long now = System.nanoTime();
        this.frameInterval = now - this.frameStart;
        this.frameStart = now;
        this.frameEntityNanos = 0;
        this.frameParticleNanos = 0;
        this.holdCamera();
    }

    private void endFrame() {
        this.queueScene();
        if (!this.worldReady) return;
        if (!this.spawned) {
            this.spawned = this.spawn();
            return;
        }

        long now = System.nanoTime();
        if (!this.ready) {
            if (this.warmupStart == 0L) this.warmupStart = now;
            if (now - this.warmupStart >= this.warmupNanos) {
                this.captureScene("scene.png");
                this.touch("ready");
                this.ready = true;
            }
            return;
        }

        if (this.phaseIndex < 0) {
            if (Files.exists(this.output.resolve("go"))) this.nextPhase(now);
            return;
        }

        if (!this.done) {
            Phase phase = this.phases.get(this.phaseIndex);
            if (now - this.phaseStart >= this.settleNanos) {
                phase.frames.add(this.frameInterval);
                phase.entities.add(this.frameEntityNanos);
                phase.particles.add(this.frameParticleNanos);
            }
            if (now - this.phaseStart >= this.settleNanos + this.phaseNanos) {
                phase.liveParticles = this.liveParticles();
                if (this.phaseIndex + 1 < this.rounds * this.variants.size()) {
                    this.nextPhase(now);
                } else {
                    this.variant = "baseline";
                    this.writeResults();
                    this.touch("done");
                    this.done = true;
                }
            }
            return;
        }

        if (Files.exists(this.output.resolve("stop"))) this.minecraft.stop();
    }

    private void nextPhase(long now) {
        if (this.phaseIndex >= 0 && this.phaseIndex < 2 * this.variants.size()) {
            Phase finished = this.phases.get(this.phaseIndex);
            this.captureScene(finished.variant + "-" + finished.round + ".png");
        }
        this.phaseIndex++;
        this.variant = this.variants.get(this.phaseIndex % this.variants.size());
        Argentum.CONFIG.fastParticles = !this.variant.equals("cpuParticles");
        this.phases.add(new Phase(this.variant, this.phaseIndex / this.variants.size()));
        this.phaseStart = now;
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
                player.networkHandler.teleport(CAMERA_X, CAMERA_Y, CAMERA_Z, 0.0F, CAMERA_PITCH);
            }
            var world = server.getWorld(0);
            world.getGameRules().set("doDaylightCycle", "false");
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("randomTickSpeed", "0");
            world.setTimeOfDay(Long.getLong("argentum.lobbyBenchmarkTime", 6000L));
            world.getData().setRaining(false);
            world.getData().setThundering(false);
            this.worldReady = true;
        });
    }

    private boolean spawn() {
        ClientWorld world = this.minecraft.world;
        for (int x = -32; x <= 32; x += 16) {
            for (int z = -16; z <= 48; z += 16) {
                if (world.getChunk(new BlockPos(x, GROUND_Y, z)).isEmpty()) return false;
            }
        }

        int id = -1000;
        if (this.scene.contains("holograms")) {
            for (int stack = 0; stack < HOLOGRAM_STACKS; stack++) {
                double x = (stack % 16) * 3.0 - 22.5;
                double z = (stack / 16) * 5.0 + 4.0;
                String[] lines = HOLOGRAM_LINES[stack % HOLOGRAM_LINES.length];
                for (int line = 0; line < lines.length; line++) {
                    ArmorStandEntity stand = new ArmorStandEntity(world, x, GROUND_Y + 2.5 - line * 0.25, z);
                    NbtCompound nbt = new NbtCompound();
                    nbt.putBoolean("Invisible", true);
                    nbt.putBoolean("Marker", true);
                    nbt.putBoolean("NoGravity", true);
                    stand.readCustomNbt(nbt);
                    stand.setInvisible(true);
                    String name = String.format(Locale.ROOT, lines[line], 1000 + stack * 37);
                    if (Boolean.getBoolean("argentum.lobbyBenchmarkPlainText")) name = name.replace("§k", "§l");
                    stand.setCustomName(name);
                    stand.setCustomNameVisible(true);
                    this.place(world, id--, stand, x, GROUND_Y + 2.5 - line * 0.25, z, 0.0F);
                }
            }
        }
        if (this.scene.contains("invisible")) {
            for (int i = 0; i < INVISIBLE_MOBS; i++) {
                ZombieEntity zombie = new ZombieEntity(world);
                zombie.setInvisible(true);
                this.place(world, id--, zombie, (i % 16) * 2.5 - 18.75, GROUND_Y + 1, (i / 16) * 2.5 + 6.0, 180.0F + i * 7);
            }
        }
        if (this.scene.contains("visible")) {
            for (int i = 0; i < VISIBLE_MOBS; i++) {
                ZombieEntity zombie = new ZombieEntity(world);
                zombie.setEquipment(0, new ItemStack(Items.IRON_SWORD));
                zombie.setEquipment(4, new ItemStack(Items.IRON_HELMET));
                this.place(world, id--, zombie, (i % 8) * 2.0 - 7.0, GROUND_Y + 1, (i / 8) * 2.0 + 3.0, 180.0F + i * 11);
            }
        }
        this.holdParticles();
        return true;
    }

    private void place(ClientWorld world, int id, LivingEntity entity, double x, double y, double z, float yaw) {
        entity.setPositionAndAngles(x, y, z, yaw, 0.0F);
        entity.bodyYaw = entity.lastBodyYaw = entity.headYaw = entity.lastHeadYaw = yaw;
        world.forceEntity(id, entity);
    }

    private void holdParticles() {
        if (!this.scene.contains("particles") && !this.scene.contains("mixedParticles")) return;
        this.particles.removeIf(particle -> particle.removed);
        while (this.particles.size() < PARTICLES) {
            double x = this.random.nextDouble() * 40.0 - 20.0;
            double y = GROUND_Y + 1.0 + this.random.nextDouble() * 6.0;
            double z = this.random.nextDouble() * 36.0 + 2.0;
            int type = PARTICLE_TYPES[this.particles.size() % PARTICLE_TYPES.length];
            Particle particle;
            if (this.scene.contains("mixedParticles") && this.particles.size() % 8 == 0) {
                particle = new LobbyCustomParticle(this.minecraft.world, x, y, z);
                this.minecraft.particleManager.add(particle);
            } else {
                particle = this.minecraft.particleManager.addParticle(type, x, y, z, 0.0, 0.0, 0.0);
            }
            if (particle == null) throw new IllegalStateException("Particle type " + type + " was not created");
            // past the size ramp most particles grow through over the first 1/32 of their life
            ((ParticleAccessor) particle).lobbyBenchmark$setLifetime(Integer.MAX_VALUE / 2);
            ((ParticleAccessor) particle).lobbyBenchmark$setAge(Integer.MAX_VALUE / 64 + 1);
            this.particles.add(particle);
        }
        for (Particle particle : this.particles) {
            ((ParticleAccessor) particle).lobbyBenchmark$setGravity(0.0F);
            particle.velocityX = 0.0;
            particle.velocityY = 0.0;
            particle.velocityZ = 0.0;
        }
    }

    private int liveParticles() {
        int live = 0;
        for (Particle particle : this.particles) {
            if (!particle.removed) live++;
        }
        return live;
    }

    private void holdCamera() {
        PlayerEntity player = this.minecraft.player;
        if (player == null) return;
        player.setPositionAndAngles(CAMERA_X, CAMERA_Y, CAMERA_Z, 0.0F, CAMERA_PITCH);
        player.prevX = player.lastX = CAMERA_X;
        player.prevY = player.lastY = CAMERA_Y;
        player.prevZ = player.lastZ = CAMERA_Z;
        player.lastYaw = 0.0F;
        player.lastPitch = CAMERA_PITCH;
        player.velocityX = 0.0;
        player.velocityY = 0.0;
        player.velocityZ = 0.0;
        player.noClip = true;
        player.abilities.canFly = true;
        player.abilities.flying = true;
        this.minecraft.setCamera(player);
    }

    private void captureScene(String file) {
        String name = "lobby-benchmark-scene.png";
        Screenshot.take(this.minecraft.gameDir, name, this.minecraft.width, this.minecraft.height, this.minecraft.getRenderTarget());
        try {
            Files.move(this.minecraft.gameDir.toPath().resolve("screenshots").resolve(name), this.output.resolve(file),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save lobby benchmark screenshot", e);
        }
    }

    private void writeResults() {
        StringBuilder csv = new StringBuilder("variant,round,frames,frameMedianMs,frameMeanMs,frameP95Ms,entitiesMedianMs,particlesMedianMs,liveParticles\n");
        for (Phase phase : this.phases) {
            csv.append(phase.variant).append(',').append(phase.round).append(',').append(phase.frames.size()).append(',')
                    .append(ms(percentile(phase.frames, 0.50))).append(',').append(ms(mean(phase.frames))).append(',')
                    .append(ms(percentile(phase.frames, 0.95))).append(',').append(ms(percentile(phase.entities, 0.50))).append(',')
                    .append(ms(percentile(phase.particles, 0.50))).append(',').append(phase.liveParticles).append('\n');
        }

        StringBuilder summary = new StringBuilder();
        summary.append("scene=").append(String.join(",", this.scene)).append('\n')
                .append("resolution=").append(this.minecraft.width).append('x').append(this.minecraft.height).append('\n')
                .append("glRenderer=").append(GL11.glGetString(GL11.GL_RENDERER)).append('\n')
                .append("entities=").append(this.minecraft.world.entities.size()).append('\n');
        for (String name : this.variants) {
            List<Long> frames = new ArrayList<>();
            List<Long> entities = new ArrayList<>();
            List<Long> particles = new ArrayList<>();
            for (Phase phase : this.phases) {
                if (!phase.variant.equals(name)) continue;
                frames.add(percentile(phase.frames, 0.50));
                entities.add(percentile(phase.entities, 0.50));
                particles.add(percentile(phase.particles, 0.50));
            }
            summary.append(String.format(Locale.ROOT, "%s frame=%s entities=%s particles=%s (median of round medians, ms)%n",
                    name, ms(percentile(frames, 0.50)), ms(percentile(entities, 0.50)), ms(percentile(particles, 0.50))));
        }

        try {
            Files.writeString(this.output.resolve("phases.csv"), csv, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(this.output.resolve("summary.txt"), summary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write lobby benchmark results", e);
        }
        System.out.print("Argentum lobby benchmark\n" + summary);
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static long mean(List<Long> samples) {
        return samples.isEmpty() ? 0 : samples.stream().mapToLong(Long::longValue).sum() / samples.size();
    }

    private static long percentile(List<Long> samples, double percentile) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1)));
    }

    private void touch(String name) {
        try {
            Files.writeString(this.output.resolve(name), "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + name, e);
        }
    }

    private static final class Phase {
        final String variant;
        final int round;
        final List<Long> frames = new ArrayList<>();
        final List<Long> entities = new ArrayList<>();
        final List<Long> particles = new ArrayList<>();
        int liveParticles;

        Phase(String variant, int round) {
            this.variant = variant;
            this.round = round;
        }
    }
}
