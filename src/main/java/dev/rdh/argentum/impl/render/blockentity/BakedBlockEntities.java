package dev.rdh.argentum.impl.render.blockentity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.HttpTextureExtension;
import dev.rdh.argentum.impl.ext.SignBlockEntityExtension;
import dev.rdh.argentum.impl.ext.SkullBlockEntityExtension;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.resource.Identifier;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BakedBlockEntities {
    static final String CHEST = "entity/chest/normal";
    static final String TRAPPED_CHEST = "entity/chest/trapped";
    static final String CHRISTMAS_CHEST = "entity/chest/christmas";
    static final String ENDER_CHEST = "entity/chest/ender";
    static final String DOUBLE_CHEST = "entity/chest/normal_double";
    static final String TRAPPED_DOUBLE_CHEST = "entity/chest/trapped_double";
    static final String CHRISTMAS_DOUBLE_CHEST = "entity/chest/christmas_double";
    static final String SKELETON_SKULL = "entity/skeleton/skeleton";
    static final String WITHER_SKULL = "entity/skeleton/wither_skeleton";
    static final String ZOMBIE_SKULL = "entity/zombie/zombie";
    static final String CREEPER_SKULL = "entity/creeper/creeper";
    static final String SIGN = "entity/sign";
    static final String BANNER = "entity/banner_base";
    static final String PLAYER_HEADS = "argentum:entity/player_heads";
    static final String FONT_PAGES = "argentum:entity/font_pages";
    private static final int SKIN_WIDTH = 64;
    private static final int HEAD_HEIGHT = 16;
    private static final int FONT_PAGE_SIZE = 256;
    private static final List<String> TEXTURES = List.of(
            CHEST, TRAPPED_CHEST, CHRISTMAS_CHEST, ENDER_CHEST, DOUBLE_CHEST, TRAPPED_DOUBLE_CHEST, CHRISTMAS_DOUBLE_CHEST,
            SKELETON_SKULL, WITHER_SKULL, ZOMBIE_SKULL, CREEPER_SKULL, SIGN, BANNER
    );

    private BakedBlockEntities() {
    }

    public static boolean enabled() {
        return Argentum.CONFIG.bakeBlockEntities;
    }

    public static void registerSprites(Map<String, TextureAtlasSprite> sprites) {
        for (String texture : TEXTURES) {
            String name = new Identifier(texture).toString();
            sprites.put(name, new EntityTextureSprite(name));
        }
        String font = fontSprite();
        if (font != null) sprites.put(font, new EntityTextureSprite(font));
    }

    public static Map<String, Region> findRegions(TextureAtlas atlas) {
        Map<String, Region> regions = new HashMap<>();
        for (String texture : TEXTURES) {
            TextureAtlasSprite sprite = atlas.getSprite(new Identifier(texture).toString());
            if (sprite instanceof EntityTextureSprite) {
                regions.put(texture, Region.of(sprite));
            }
        }
        String font = fontSprite();
        if (font != null && atlas.getSprite(font) instanceof EntityTextureSprite sprite) {
            regions.put(font, Region.of(sprite));
        }
        return Map.copyOf(regions);
    }

    private static boolean isBaked(String texture) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer != null && renderer.isRenderingBlockEntities() && enabled()
                && Minecraft.getInstance().getBlocksAtlas().argentum$getEntityTextureRegion(texture) != null;
    }

    public static boolean bakesSigns() {
        return isBaked(SIGN);
    }

    public static boolean bakesBanners() {
        return isBaked(BANNER);
    }

    public static boolean isOpen(float animationProgress, float lastAnimationProgress, int viewerCount) {
        return viewerCount > 0 || animationProgress > 0.0F || lastAnimationProgress > 0.0F;
    }

    public static boolean hasText(SignBlockEntity sign) {
        for (Text line : sign.lines) {
            if (line != null && !line.getString().isEmpty()) return true;
        }
        return false;
    }

    public static void prepare(List<BlockEntity> blockEntities) {
        if (!enabled()) return;
        TextureAtlas atlas = Minecraft.getInstance().getBlocksAtlas();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof SkullBlockEntity skull && skull.getType() == 3) {
                prepareHead(atlas, skull);
            } else if (blockEntity instanceof SignBlockEntity sign) {
                prepareText(atlas, sign);
            }
        }
    }

    private static void prepareHead(TextureAtlas atlas, SkullBlockEntity skull) {
        SlotSheet heads = atlas.argentum$getPlayerHeads();
        SlotSheet.Entry entry = heads == null ? null : heads.upload(skin(skull.getProfile()));
        SkullBlockEntityExtension state = (SkullBlockEntityExtension)skull;
        if (entry != null && state.argentum$getBakedHead() != entry) {
            state.argentum$setBakedHead(entry);
            rebuild(skull.getWorld(), skull.getPos());
        }
    }

    private static void prepareText(TextureAtlas atlas, SignBlockEntity sign) {
        String font = fontSprite();
        Region region = font == null ? null : atlas.argentum$getEntityTextureRegion(font);
        SignBlockEntityExtension state = (SignBlockEntityExtension)sign;
        TextRenderer textRenderer = Minecraft.getInstance().textRenderer;
        SignText text = state.argentum$getBakedText();
        if (text != null && text.current(sign.lines, region, textRenderer.getUnicode())) return;
        text = SignText.of(sign, textRenderer, font, region, atlas.argentum$getFontPages());
        state.argentum$setBakedText(text);
        if (text.bakeable()) rebuild(sign.getWorld(), sign.getPos());
    }

    static String fontSprite() {
        TextRenderer textRenderer = Minecraft.getInstance().textRenderer;
        if (textRenderer == null) return null;
        Identifier location = textRenderer.argentum$getFontLocation();
        String path = location.getPath();
        if (!path.endsWith(".png")) return null;
        path = path.substring(0, path.length() - ".png".length());
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        } else if (!path.startsWith("mcpatcher/") && !path.startsWith("optifine/")) {
            return null;
        }
        return new Identifier(location.getNamespace(), path).toString();
    }

    public static List<TextureAtlasSprite> slotSheets(int mipLevels) {
        return List.of(EntityTextureSprite.blank(PLAYER_HEADS, 512, 256, mipLevels),
                EntityTextureSprite.blank(FONT_PAGES, 1024, 512, mipLevels));
    }

    public static SlotSheet playerHeads(TextureAtlas atlas, int mipLevels) {
        return SlotSheet.of(atlas, PLAYER_HEADS, mipLevels, SKIN_WIDTH, HEAD_HEIGHT, SKIN_WIDTH, BakedBlockEntities::headPixels);
    }

    public static SlotSheet fontPages(TextureAtlas atlas, int mipLevels) {
        return SlotSheet.of(atlas, FONT_PAGES, mipLevels, FONT_PAGE_SIZE, FONT_PAGE_SIZE, FONT_PAGE_SIZE, BakedBlockEntities::fontPagePixels);
    }

    public static void clearSlotSheets() {
        TextureAtlas atlas = Minecraft.getInstance().getBlocksAtlas();
        for (SlotSheet sheet : new SlotSheet[]{atlas.argentum$getPlayerHeads(), atlas.argentum$getFontPages()}) {
            if (sheet != null) sheet.clear();
        }
    }

    private static int[] headPixels(Identifier skin) throws IOException {
        Texture texture = Minecraft.getInstance().getTextureManager().get(skin);
        if (texture instanceof HttpTexture http) return ((HttpTextureExtension)http).argentum$getHeadPixels();
        if (!(texture instanceof SimpleTexture)) return null;
        int[] pixels = headPixels(TextureUtil.readImage(Minecraft.getInstance().getResourceManager().getResource(skin).asStream()));
        if (pixels == null) throw new IOException("Unsupported skin size for " + skin);
        return pixels;
    }

    public static int[] headPixels(BufferedImage image) {
        if (image == null || image.getWidth() != SKIN_WIDTH || image.getHeight() < HEAD_HEIGHT) return null;
        int[] pixels = new int[SKIN_WIDTH * HEAD_HEIGHT];
        image.getRGB(0, 0, SKIN_WIDTH, HEAD_HEIGHT, pixels, 0, SKIN_WIDTH);
        return pixels;
    }

    private static int[] fontPagePixels(Identifier page) throws IOException {
        BufferedImage image = TextureUtil.readImage(Minecraft.getInstance().getResourceManager().getResource(page).asStream());
        if (image == null || image.getWidth() != FONT_PAGE_SIZE || image.getHeight() != FONT_PAGE_SIZE) {
            throw new IOException("Unsupported font page size for " + page);
        }
        int[] pixels = new int[FONT_PAGE_SIZE * FONT_PAGE_SIZE];
        image.getRGB(0, 0, FONT_PAGE_SIZE, FONT_PAGE_SIZE, pixels, 0, FONT_PAGE_SIZE);
        return pixels;
    }

    static Identifier skin(GameProfile profile) {
        if (profile == null) return DefaultSkinUtils.getDefaultSkin();
        MinecraftProfileTexture skin = Minecraft.getInstance().getSkinManager().getTextures(profile).get(MinecraftProfileTexture.Type.SKIN);
        return skin != null ? new Identifier("skins/" + skin.getHash()) : DefaultSkinUtils.getDefaultSkin(PlayerEntity.getUuid(profile));
    }

    public static void rebuild(World world, BlockPos pos) {
        if (world != null && world.isClient && enabled()) {
            world.notifyBlockChanged(pos);
        }
    }

    public record Region(float u, float v, float width, float height, int[] texels, int texelWidth, int texelHeight) {
        static Region of(TextureAtlasSprite sprite) {
            float atlasWidth = atlasSize(sprite.getWidth(), sprite.getUMin(), sprite.getUMax());
            float atlasHeight = atlasSize(sprite.getHeight(), sprite.getVMin(), sprite.getVMax());
            return new Region(sprite.getX() / atlasWidth, sprite.getY() / atlasHeight,
                    sprite.getWidth() / atlasWidth, sprite.getHeight() / atlasHeight,
                    sprite.getFrameCount() > 0 ? sprite.getFrame(0)[0] : null, sprite.getWidth(), sprite.getHeight());
        }

        static float atlasSize(int spriteSize, float min, float max) {
            return Math.round((spriteSize - 0.02F) / (max - min));
        }
    }
}
