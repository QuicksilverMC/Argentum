package dev.rdh.cera.modules.cem;

import dev.rdh.cera.Cera;
import dev.rdh.cera.ext.CeraEntityExtension;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AnimationContext {
    private Entity entity;
    private LivingEntity living;
    private float tickDelta;
    private float limbSwing;
    private float limbSpeed;
    private float age;
    private float headYaw;
    private float headPitch;
    private float scale;
    long sequence;

    private long lastFrame;
    private float frameTime;
    private int frameCounter;

    private final Object2IntOpenHashMap<String> varSlots = new Object2IntOpenHashMap<>();
    private float[] scratchVars = new float[0];
    float[] vars;

    private final List<Animation> dirty = new ArrayList<>();
    private final Int2ObjectOpenHashMap<Smoothed> smoothed = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap printed = new Int2IntOpenHashMap();

    public void beginFrame() {
        long now = System.nanoTime();
        this.frameTime = this.lastFrame == 0 ? 0.0F : (now - this.lastFrame) / 1.0E9F;
        this.lastFrame = now;
        this.frameCounter = (this.frameCounter + 1) % 27720;
        this.restore();
    }

    public void beginEntity(Entity entity, float tickDelta) {
        this.begin(entity, tickDelta);
    }

    public void beginBlockEntity(float tickDelta) {
        this.begin(null, tickDelta);
    }

    private void begin(Entity entity, float tickDelta) {
        this.sequence++;
        this.entity = entity;
        this.living = entity instanceof LivingEntity living ? living : null;
        this.tickDelta = tickDelta;
        this.limbSwing = this.limbSpeed = this.age = this.headYaw = this.headPitch = 0.0F;
        this.scale = 0.0625F;
    }

    public void setModelParameters(float limbSwing, float limbSpeed, float age, float headYaw, float headPitch, float scale) {
        this.limbSwing = limbSwing;
        this.limbSpeed = limbSpeed;
        this.age = age;
        this.headYaw = headYaw;
        this.headPitch = headPitch;
        this.scale = scale;
    }

    public void endRender() {
        this.restore();
    }

    void markDirty(Animation animation) {
        if (!animation.dirty) {
            animation.dirty = true;
            this.dirty.add(animation);
        }
    }

    private void restore() {
        for (Animation animation : this.dirty) animation.restore();
        this.dirty.clear();
    }

    int varSlot(String name) {
        return this.varSlots.computeIfAbsent(name, _ -> this.varSlots.size());
    }

    void loadVars() {
        int size = this.varSlots.size();
        if (this.entity instanceof CeraEntityExtension extension) {
            float[] vars = extension.cera$getAnimationVars();
            if (vars.length < size) extension.cera$setAnimationVars(vars = Arrays.copyOf(vars, size));
            this.vars = vars;
        } else {
            if (this.scratchVars.length < size) this.scratchVars = new float[size];
            Arrays.fill(this.scratchVars, 0.0F);
            this.vars = this.scratchVars;
        }
    }

    float time() {
        World world = Minecraft.getInstance().world;
        return world == null ? 0.0F : world.getTime() % 24000L + this.tickDelta;
    }

    float smooth(int id, float value, float fadeUp, float fadeDown) {
        long now = System.currentTimeMillis();
        Smoothed state = this.smoothed.get(id);
        if (state == null) {
            this.smoothed.put(id, new Smoothed(value, now));
            return value;
        }
        float previous = state.value;
        float seconds = (now - state.time) / 1000.0F;
        state.time = now;
        if (seconds <= 0.0F) return previous;
        float fade = value >= previous ? fadeUp : fadeDown;
        float result = value;
        if (fade > 0.0F && seconds < fade && Math.abs(value - previous) > 1.0E-6F) {
            float correction = 4.61F - 1.0F / (0.13F + fade / seconds / 10.0F);
            result = previous + (value - previous) * Math.clamp(seconds / fade * correction, 0.0F, 1.0F);
        }
        state.value = result;
        return result;
    }

    void print(int id, int every, String value) {
        int count = this.printed.addTo(id, 1);
        if (every <= 1 || count % every == 0) Cera.LOGGER.info("[CustomEntityModels] {}: {}", id, value);
    }

    Object parameter(String name) {
        return switch (name) {
            case "limb_swing" -> (ExpressionParser.FloatExpr)() -> this.limbSwing;
            case "limb_speed" -> (ExpressionParser.FloatExpr)() -> this.limbSpeed;
            case "age" -> (ExpressionParser.FloatExpr)() -> this.age;
            case "head_yaw" -> (ExpressionParser.FloatExpr)() -> this.headYaw;
            case "head_pitch" -> (ExpressionParser.FloatExpr)() -> this.headPitch;
            case "scale" -> (ExpressionParser.FloatExpr)() -> this.scale;
            case "partial_ticks" -> (ExpressionParser.FloatExpr)() -> this.tickDelta;
            case "frame_time" -> (ExpressionParser.FloatExpr)() -> this.frameTime;
            case "frame_counter" -> (ExpressionParser.FloatExpr)() -> this.frameCounter;
            case "rule_index" -> (ExpressionParser.FloatExpr)() -> 0.0F;
            case "day_time" -> (ExpressionParser.FloatExpr)() -> {
                World world = Minecraft.getInstance().world;
                return world == null ? 0.0F : world.getTimeOfDay() % 24000L;
            };
            case "day_count" -> (ExpressionParser.FloatExpr)() -> {
                World world = Minecraft.getInstance().world;
                return world == null ? 0.0F : world.getTimeOfDay() / 24000L;
            };
            case "dimension" -> (ExpressionParser.FloatExpr)() -> {
                World world = Minecraft.getInstance().world;
                return world == null ? 0.0F : world.dimension.getId();
            };
            case "pos_x" -> (ExpressionParser.FloatExpr)() -> this.entity == null ? 0.0F : (float)this.entity.x;
            case "pos_y" -> (ExpressionParser.FloatExpr)() -> this.entity == null ? 0.0F : (float)this.entity.y;
            case "pos_z" -> (ExpressionParser.FloatExpr)() -> this.entity == null ? 0.0F : (float)this.entity.z;
            case "id" -> (ExpressionParser.FloatExpr)() -> this.entity == null ? 0.0F : this.entity.getNetworkId();
            case "health" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.getHealth();
            case "max_health" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.getMaxHealth();
            case "hurt_time" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.damagedTimer;
            case "death_time" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.deathTicks;
            case "idle_time" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.getDespawnTimer();
            case "revenge_time" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.getLastAttackedTime();
            case "move_forward" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.forwardSpeed;
            case "move_strafing" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.sidewaysSpeed;
            case "swing_progress" -> (ExpressionParser.FloatExpr)() -> this.living == null ? 0.0F : this.living.getAttackAnimationProgress(this.tickDelta);
            case "is_alive" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isAlive();
            case "is_burning" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isOnFire();
            case "is_child" -> (ExpressionParser.BoolExpr)() -> this.living != null && this.living.isBaby();
            case "is_glowing" -> (ExpressionParser.BoolExpr)() -> false;
            case "is_hurt" -> (ExpressionParser.BoolExpr)() -> this.living != null && this.living.damagedTimer > 0;
            case "is_in_lava" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isInLava();
            case "is_in_water" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isInWater();
            case "is_invisible" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isInvisible();
            case "is_on_ground" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.onGround;
            case "is_ridden" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.rider != null;
            case "is_riding" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isRiding();
            case "is_sneaking" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isSneaking();
            case "is_sprinting" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isSprinting();
            case "is_wet" -> (ExpressionParser.BoolExpr)() -> this.entity != null && this.entity.isInWaterOrRain();
            case "is_sitting" -> (ExpressionParser.BoolExpr)() -> this.entity instanceof TameableEntity tameable && tameable.isSitting();
            case "is_tamed" -> (ExpressionParser.BoolExpr)() -> this.entity instanceof TameableEntity tameable && tameable.isTamed();
            default -> null;
        };
    }

    private static final class Smoothed {
        private float value;
        private long time;

        private Smoothed(float value, long time) {
            this.value = value;
            this.time = time;
        }
    }
}
