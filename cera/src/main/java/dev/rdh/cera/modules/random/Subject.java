package dev.rdh.cera.modules.random;

import dev.rdh.cera.Cera;
import dev.rdh.cera.entity.SpawnSnapshot;
import dev.rdh.cera.mixin.VillagerEntityAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.InventoryBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public abstract sealed class Subject {
    private NbtCompound nbt;

    public static Subject current() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getEntityRenderDispatcher().cera$getEntityContext().current();
        if (entity != null) return new Subject.OfEntity(entity);
        BlockEntity blockEntity = BlockEntityRenderDispatcher.INSTANCE.cera$getBlockEntityContext().current();
        return blockEntity == null ? null : new Subject.OfBlockEntity(blockEntity);
    }

    public abstract Object key();

    public abstract int seed();

    public abstract World world();

    public abstract BlockPos spawnPos();

    public abstract Biome spawnBiome();

    public abstract String name();

    public abstract BlockState blockState();

    protected abstract void writeNbt(NbtCompound nbt);

    public NbtCompound nbt() {
        if (this.nbt == null) {
            this.nbt = new NbtCompound();
            this.writeNbt(this.nbt);
        }
        return this.nbt;
    }

    public boolean isLiving() {
        return false;
    }

    public int health() {
        return -1;
    }

    public int maxHealth() {
        return -1;
    }

    public Boolean baby() {
        return null;
    }

    public DyeColor color() {
        return null;
    }

    public int slimeSize() {
        return -1;
    }

    public int profession() {
        return -1;
    }

    public int career() {
        return -1;
    }

    private static final class OfEntity extends Subject {
        private final Entity entity;

        private OfEntity(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Object key() {
            return this.entity.getUuid();
        }

        @Override
        public int seed() {
            return (int) (this.entity.getUuid().getLeastSignificantBits() & 0x7FFFFFFFL);
        }

        @Override
        public World world() {
            return this.entity.world;
        }

        @Override
        public BlockPos spawnPos() {
            SpawnSnapshot snapshot = this.snapshot();
            return snapshot == null ? null : snapshot.pos();
        }

        @Override
        public Biome spawnBiome() {
            SpawnSnapshot snapshot = this.snapshot();
            return snapshot == null ? null : snapshot.biome();
        }

        @Override
        public String name() {
            return this.entity.hasCustomName() ? this.entity.getCustomName() : null;
        }

        @Override
        public BlockState blockState() {
            World world = this.entity.world;
            if (world == null) return null;
            return world.getBlockState(new BlockPos(MathHelper.floor(this.entity.x), MathHelper.floor(this.entity.y - 0.5),
                    MathHelper.floor(this.entity.z)));
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            this.entity.writeNbt(nbt);
        }

        @Override
        public boolean isLiving() {
            return this.entity instanceof LivingEntity;
        }

        @Override
        public int health() {
            return this.entity instanceof LivingEntity living ? (int) living.getHealth() : -1;
        }

        @Override
        public int maxHealth() {
            return this.entity instanceof LivingEntity living ? (int) living.getMaxHealth() : -1;
        }

        @Override
        public Boolean baby() {
            return this.entity instanceof LivingEntity living ? living.isBaby() : null;
        }

        @Override
        public DyeColor color() {
            if (this.entity instanceof WolfEntity wolf) return wolf.getCollarColor();
            if (this.entity instanceof SheepEntity sheep) return sheep.getColor();
            return null;
        }

        @Override
        public int slimeSize() {
            return this.entity instanceof SlimeEntity slime ? slime.getSize() : -1;
        }

        @Override
        public int profession() {
            return this.entity instanceof VillagerEntity villager ? villager.getProfession() : -1;
        }

        @Override
        public int career() {
            return this.entity instanceof VillagerEntity villager ? ((VillagerEntityAccessor) villager).cera$getCareer() : -1;
        }

        private SpawnSnapshot snapshot() {
            return this.entity.world instanceof ClientWorld client
                    ? client.cera$getSpawnSnapshots().get(this.entity.getUuid()) : null;
        }
    }

    private static final class OfBlockEntity extends Subject {
        private final BlockEntity blockEntity;
        private int metadata = -1;

        private OfBlockEntity(BlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        @Override
        public Object key() {
            return this.blockEntity.getPos();
        }

        @Override
        public int seed() {
            return Cera.random(this.blockEntity.getPos(), this.metadata());
        }

        @Override
        public World world() {
            return this.blockEntity.getWorld();
        }

        @Override
        public BlockPos spawnPos() {
            return this.blockEntity.getPos();
        }

        @Override
        public Biome spawnBiome() {
            World world = this.blockEntity.getWorld();
            return world == null ? null : world.getBiome(this.blockEntity.getPos());
        }

        @Override
        public String name() {
            return this.blockEntity instanceof InventoryBlockEntity inventory && inventory.hasCustomName()
                    ? inventory.getName() : null;
        }

        @Override
        public BlockState blockState() {
            World world = this.blockEntity.getWorld();
            return world == null ? null : world.getBlockState(this.blockEntity.getPos());
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            this.blockEntity.writeNbt(nbt);
        }

        private int metadata() {
            if (this.metadata < 0) {
                this.metadata = this.blockEntity.getBlockMetadata();
            }
            return this.metadata;
        }
    }
}
