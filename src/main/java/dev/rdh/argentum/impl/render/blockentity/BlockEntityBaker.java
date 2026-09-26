package dev.rdh.argentum.impl.render.blockentity;

import dev.rdh.argentum.impl.ext.SignBlockEntityExtension;
import dev.rdh.argentum.impl.render.terrain.RenderPassConfigurationBuilder;
import dev.rdh.argentum.impl.render.terrain.compile.PrimitiveBuiltRenderSectionData;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import dev.rdh.argentum.mixin.features.model.instancing.BoxAccessor;
import org.embeddedt.embeddium.api.util.ColorABGR;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.model.Vertex;
import net.minecraft.client.render.model.block.entity.BannerModel;
import net.minecraft.client.render.model.block.entity.ChestModel;
import net.minecraft.client.render.model.block.entity.HumanoidSkullModel;
import net.minecraft.client.render.model.block.entity.LargeChestModel;
import net.minecraft.client.render.model.block.entity.SignModel;
import net.minecraft.client.render.model.block.entity.SkullModel;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Calendar;

import static dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities.*;

public final class BlockEntityBaker {
    private static final float MODEL_SCALE = 0.0625F;
    private static final float SIGN_SCALE = 0.6666667F;
    private static final float TEXT_SCALE = 0.015625F * SIGN_SCALE;
    private static final Vector3fc LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3fc LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final boolean CHRISTMAS = isChristmas();

    private final ChestModel singleChestModel = new ChestModel();
    private final ChestModel doubleChestModel = new LargeChestModel();
    private final SkullModel skullModel = new SkullModel(0, 0, 64, 32);
    private final SkullModel humanoidSkullModel = new HumanoidSkullModel();
    private final SignModel signModel = new SignModel();
    private final BannerModel bannerModel = new BannerModel();

    private final TextureAtlas atlas;
    private final ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final ChunkVertexEncoder.Vertex[] backQuad = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final Matrix4f transform = new Matrix4f();
    private final Matrix4f partTransform = new Matrix4f();
    private final Matrix4f textTransform = new Matrix4f();
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private ChunkBuildBuffers buffers;
    private Material material;
    private Material solidMaterial;
    private Region region;
    private boolean doubleSided;
    private int light;
    private int localX;
    private int localY;
    private int localZ;

    public BlockEntityBaker(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public boolean bake(BlockEntity blockEntity, BlockState state, BlockPos pos, ChunkRenderContext world, ChunkBuildBuffers buffers) {
        if (!enabled()) return false;
        this.buffers = buffers;
        this.material = buffers.getRenderPassConfiguration().getMaterialForRenderType(BlockLayer.CUTOUT);
        this.solidMaterial = buffers.getRenderPassConfiguration().getMaterialForRenderType(RenderPassConfigurationBuilder.UNMIPPED_SOLID);
        this.light = world.getLightColor(pos, 0);
        this.localX = pos.getX() & 15;
        this.localY = pos.getY() & 15;
        this.localZ = pos.getZ() & 15;
        this.doubleSided = false;
        this.transform.identity();
        return switch (blockEntity) {
            case ChestBlockEntity chest -> this.chest(chest, state, pos, world);
            case EnderChestBlockEntity chest -> this.enderChest(chest, state);
            case SkullBlockEntity skull -> this.skull(skull, state);
            case SignBlockEntity sign -> this.sign(sign, state);
            case BannerBlockEntity _ -> this.banner(state);
            default -> false;
        };
    }

    private boolean chest(ChestBlockEntity chest, BlockState state, BlockPos pos, ChunkRenderContext world) {
        if (!(state.getBlock() instanceof ChestBlock block)) return false;
        if (neighbor(world, pos, Direction.NORTH, block) != null || neighbor(world, pos, Direction.WEST, block) != null) {
            return true;
        }
        ChestBlockEntity east = neighbor(world, pos, Direction.EAST, block);
        ChestBlockEntity south = neighbor(world, pos, Direction.SOUTH, block);
        if (isOpen(chest) || east != null && isOpen(east) || south != null && isOpen(south)) return false;

        boolean large = east != null || south != null;
        String texture = CHRISTMAS ? (large ? CHRISTMAS_DOUBLE_CHEST : CHRISTMAS_CHEST)
                : block.type == 1 ? (large ? TRAPPED_DOUBLE_CHEST : TRAPPED_CHEST)
                : large ? DOUBLE_CHEST : CHEST;
        if (!this.useTexture(texture)) return false;

        int meta = block.getMetadataFromState(state);
        this.transform.translate(0.0F, 1.0F, 1.0F).scale(1.0F, -1.0F, -1.0F).translate(0.5F, 0.5F, 0.5F);
        if (meta == 2 && east != null) this.transform.translate(1.0F, 0.0F, 0.0F);
        if (meta == 5 && south != null) this.transform.translate(0.0F, 0.0F, -1.0F);
        this.transform.rotateY(horizontalAngle(meta)).translate(-0.5F, -0.5F, -0.5F);
        this.chestParts(large ? this.doubleChestModel : this.singleChestModel);
        return true;
    }

    private boolean enderChest(EnderChestBlockEntity chest, BlockState state) {
        if (BakedBlockEntities.isOpen(chest.animationProgress, chest.lastAnimationProgress, chest.viewerCount)
                || !this.useTexture(ENDER_CHEST)) {
            return false;
        }
        int meta = state.getBlock().getMetadataFromState(state);
        this.transform.translate(0.0F, 1.0F, 1.0F).scale(1.0F, -1.0F, -1.0F).translate(0.5F, 0.5F, 0.5F)
                .rotateY(horizontalAngle(meta)).translate(-0.5F, -0.5F, -0.5F);
        this.chestParts(this.singleChestModel);
        return true;
    }

    private void chestParts(ChestModel model) {
        this.part(model.lid);
        this.part(model.lock);
        this.part(model.base);
    }

    private boolean skull(SkullBlockEntity skull, BlockState state) {
        int type = skull.getType();
        String texture = switch (type) {
            case 1 -> WITHER_SKULL;
            case 2 -> ZOMBIE_SKULL;
            case 4 -> CREEPER_SKULL;
            default -> SKELETON_SKULL;
        };
        if (type == 3) {
            SlotSheet heads = this.atlas.argentum$getPlayerHeads();
            SlotSheet.Entry entry = heads == null ? null : heads.entry(skin(skull.getProfile()));
            if (entry == null || !(this.buffers.getSectionContextBundle() instanceof PrimitiveBuiltRenderSectionData data)) return false;
            data.slots.add(entry);
            this.region = entry.region();
        } else if (!this.useTexture(texture)) {
            return false;
        }

        float rotation = skull.getRotation() * 360 / 16.0F;
        switch (state.get(SkullBlock.FACING)) {
            case UP -> this.transform.translate(0.5F, 0.0F, 0.5F);
            case NORTH -> this.transform.translate(0.5F, 0.25F, 0.74F);
            case SOUTH -> {
                this.transform.translate(0.5F, 0.25F, 0.26F);
                rotation = 180.0F;
            }
            case WEST -> {
                this.transform.translate(0.74F, 0.25F, 0.5F);
                rotation = 270.0F;
            }
            default -> {
                this.transform.translate(0.26F, 0.25F, 0.5F);
                rotation = 90.0F;
            }
        }
        this.transform.scale(-1.0F, -1.0F, 1.0F);
        this.doubleSided = true;
        float yaw = rotation / (180.0F / (float)Math.PI);
        for (ModelPart part : (type == 2 || type == 3 ? this.humanoidSkullModel : this.skullModel).parts) {
            this.part(part, yaw);
        }
        return true;
    }

    private boolean sign(SignBlockEntity sign, BlockState state) {
        if (!this.useTexture(SIGN)) return false;
        boolean standing = state.getBlock() == Blocks.STANDING_SIGN;
        this.standingOrWall(state, standing, 0.75F);
        this.textTransform.set(this.transform).translate(0.0F, 0.5F * SIGN_SCALE, 0.07F * SIGN_SCALE)
                .scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        this.transform.scale(SIGN_SCALE, -SIGN_SCALE, -SIGN_SCALE);
        this.part(this.signModel.board);
        if (standing) this.part(this.signModel.pole);
        return !hasText(sign) || this.signText(sign);
    }

    private boolean signText(SignBlockEntity sign) {
        SignText text = ((SignBlockEntityExtension)sign).argentum$getBakedText();
        if (text == null || !text.bakeable() || !text.current(sign.lines, text.region(), Minecraft.getInstance().textRenderer.getUnicode())
                || this.atlas.argentum$getEntityTextureRegion(text.font()) != text.region()
                || !(this.buffers.getSectionContextBundle() instanceof PrimitiveBuiltRenderSectionData data)) {
            return false;
        }
        data.slots.addAll(text.pages());

        Material material = this.buffers.getRenderPassConfiguration().getMaterialForRenderType(RenderPassConfigurationBuilder.DECAL);
        float shade = shade(this.textTransform.transformDirection(0.0F, 0.0F, -1.0F, this.normal));
        float[] vertices = text.vertices();
        int[] colors = text.colors();
        for (int glyph = 0; glyph < colors.length; glyph++) {
            Region texture = text.texture(glyph);
            int rgb = colors[glyph];
            int color = ColorABGR.pack(Math.round((rgb >> 16 & 0xFF) * shade), Math.round((rgb >> 8 & 0xFF) * shade),
                    Math.round((rgb & 0xFF) * shade), 0xFF);
            for (int corner = 0; corner < 4; corner++) {
                int index = (glyph * 4 + corner) * 4;
                this.textTransform.transformPosition(vertices[index], vertices[index + 1], 0.0F, this.position);
                ChunkVertexEncoder.Vertex vertex = this.quad[corner];
                vertex.x = this.localX + this.position.x;
                vertex.y = this.localY + this.position.y;
                vertex.z = this.localZ + this.position.z;
                vertex.color = color;
                vertex.rdhFactor = 0;
                vertex.u = texture.u() + vertices[index + 2] * texture.width();
                vertex.v = texture.v() + vertices[index + 3] * texture.height();
                vertex.light = this.light;
            }
            this.push(this.quad, material);
        }
        return true;
    }

    private boolean banner(BlockState state) {
        if (!this.useTexture(BANNER)) return false;
        boolean standing = state.getBlock() == Blocks.STANDING_BANNER;
        this.standingOrWall(state, standing, standing ? 0.75F : -0.25F);
        this.transform.scale(SIGN_SCALE, -SIGN_SCALE, -SIGN_SCALE);
        if (standing) this.part(this.bannerModel.pole);
        this.part(this.bannerModel.bar);
        return false;
    }

    private void standingOrWall(BlockState state, boolean standing, float height) {
        int meta = state.getBlock().getMetadataFromState(state);
        this.transform.translate(0.5F, height * SIGN_SCALE, 0.5F);
        if (standing) {
            this.transform.rotateY((float)Math.toRadians(-(meta * 360 / 16.0F)));
        } else {
            this.transform.rotateY(-horizontalAngle(meta)).translate(0.0F, -0.3125F, -0.4375F);
        }
    }

    private boolean useTexture(String texture) {
        this.region = this.atlas.argentum$getEntityTextureRegion(texture);
        return this.region != null;
    }

    private void part(ModelPart part) {
        this.part(part, part.rotationY);
    }

    private void part(ModelPart part, float rotationY) {
        this.partTransform.set(this.transform)
                .translate(part.translateX, part.translateY, part.translateZ)
                .translate(part.x * MODEL_SCALE, part.y * MODEL_SCALE, part.z * MODEL_SCALE)
                .rotateZ(part.rotationZ)
                .rotateY(rotationY)
                .rotateX(part.rotationX);
        for (Box box : part.boxes) {
            for (Polygon polygon : ((BoxAccessor)box).celeritas$getFaces()) {
                this.polygon(polygon);
            }
        }
    }

    private void polygon(Polygon polygon) {
        Vertex[] vertices = polygon.vertices;
        Vec3d a = vertices[0].pos;
        Vec3d b = vertices[1].pos;
        Vec3d c = vertices[2].pos;
        this.normal.set((float)(c.x - b.x), (float)(c.y - b.y), (float)(c.z - b.z))
                .cross((float)(a.x - b.x), (float)(a.y - b.y), (float)(a.z - b.z));
        float shade = shade(this.partTransform.transformDirection(this.normal).normalize());
        int gray = Math.round(shade * 255.0F);
        int color = 0xFF000000 | gray << 16 | gray << 8 | gray;

        for (int i = 0; i < 4; i++) {
            Vertex source = vertices[i];
            this.partTransform.transformPosition((float)source.pos.x * MODEL_SCALE, (float)source.pos.y * MODEL_SCALE,
                    (float)source.pos.z * MODEL_SCALE, this.position);
            ChunkVertexEncoder.Vertex vertex = this.quad[i];
            vertex.x = this.localX + this.position.x;
            vertex.y = this.localY + this.position.y;
            vertex.z = this.localZ + this.position.z;
            vertex.color = color;
            vertex.rdhFactor = 0;
            vertex.u = this.region.u() + source.u * this.region.width();
            vertex.v = this.region.v() + source.v * this.region.height();
            vertex.light = this.light;
        }
        Material material = opaque(this.region, vertices) ? this.solidMaterial : this.material;
        this.push(this.quad, material);

        if (this.doubleSided) {
            for (int i = 0; i < 4; i++) {
                copy(this.quad[3 - i], this.backQuad[i]);
            }
            this.push(this.backQuad, material);
        }
    }

    private static boolean opaque(Region region, Vertex[] vertices) {
        int[] texels = region.texels();
        if (texels == null) return false;
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (Vertex vertex : vertices) {
            minU = Math.min(minU, vertex.u);
            minV = Math.min(minV, vertex.v);
            maxU = Math.max(maxU, vertex.u);
            maxV = Math.max(maxV, vertex.v);
        }
        int width = region.texelWidth();
        int x0 = (int)Math.floor(minU * width + 1.0E-3F);
        int x1 = (int)Math.ceil(maxU * width - 1.0E-3F);
        int y0 = (int)Math.floor(minV * region.texelHeight() + 1.0E-3F);
        int y1 = (int)Math.ceil(maxV * region.texelHeight() - 1.0E-3F);
        if (x0 < 0 || y0 < 0 || x1 > width || y1 > texels.length / width) return false;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (texels[y * width + x] >>> 24 < 26) return false;
            }
        }
        return true;
    }

    private void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        int normal = QuadUtil.calculateNormal(vertices);
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            vertex.trueNormal = normal;
            vertex.vanillaNormal = normal;
        }
        this.buffers.get(material).getVertexBuffer(ModelQuadFacing.UNASSIGNED).push(vertices, material);
    }

    private static float shade(Vector3f normal) {
        return Math.min(1.0F, 0.4F + 0.6F * Math.max(0.0F, normal.dot(LIGHT_0)) + 0.6F * Math.max(0.0F, normal.dot(LIGHT_1)));
    }

    private static void copy(ChunkVertexEncoder.Vertex from, ChunkVertexEncoder.Vertex to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.color = from.color;
        to.rdhFactor = from.rdhFactor;
        to.u = from.u;
        to.v = from.v;
        to.light = from.light;
    }

    private static ChestBlockEntity neighbor(ChunkRenderContext world, BlockPos pos, Direction direction, ChestBlock block) {
        BlockPos neighbor = pos.offset(direction);
        return world.getBlockState(neighbor).getBlock() instanceof ChestBlock other && other.type == block.type
                && world.getBlockEntity(neighbor) instanceof ChestBlockEntity chest ? chest : null;
    }

    private static boolean isOpen(ChestBlockEntity chest) {
        return BakedBlockEntities.isOpen(chest.animationProgress, chest.lastAnimationProgress, chest.viewerCount);
    }

    private static float horizontalAngle(int meta) {
        return switch (meta) {
            case 2 -> (float)Math.PI;
            case 4 -> (float)(Math.PI / 2.0);
            case 5 -> (float)(-Math.PI / 2.0);
            default -> 0.0F;
        };
    }

    private static boolean isChristmas() {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return calendar.get(Calendar.MONTH) + 1 == 12 && day >= 24 && day <= 26;
    }
}
