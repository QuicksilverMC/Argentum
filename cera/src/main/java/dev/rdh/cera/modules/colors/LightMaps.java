package dev.rdh.cera.modules.colors;

import dev.rdh.cera.Cera;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.api.util.ColorU8;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LightMaps implements ResourceReloadListener {
    private static final String[] DIRECTORIES = {"optifine/lightmap/", "mcpatcher/lightmap/"};
    private static final Pattern WORLD = Pattern.compile(".*/world(-?\\d+)\\.png$");

    private volatile Int2ObjectMap<Pack> packs = new Int2ObjectOpenHashMap<>();

    private final float[][] sunRgb = new float[16][3];
    private final float[][] torchRgb = new float[16][3];
    private int[] blend1 = new int[0];
    private int[] blend2 = new int[0];

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        this.packs = load(resources);
    }

    public boolean apply(int[] out, World world, float flicker, float tickDelta, boolean nightvision, float gamma) {
        if (!Cera.CONFIG.customLightmaps) return false;
        Pack pack = this.packs.get(world.dimension.getId());
        return pack != null && compute(pack, out, world, flicker, tickDelta, nightvision, gamma);
    }

    private boolean compute(Pack pack, int[] out, World world, float flicker, float tickDelta, boolean nightvision, float gamma) {
        if (pack.rain == null && pack.thunder == null) {
            return computeSingle(pack.base, out, world, flicker, nightvision, gamma);
        }
        int dimension = world.dimension.getId();
        if (dimension == 1 || dimension == -1) {
            return computeSingle(pack.base, out, world, flicker, nightvision, gamma);
        }

        float rain = world.getRain(tickDelta);
        float thunder = world.getThunder(tickDelta);
        if (rain <= 1.0E-4F && thunder <= 1.0E-4F) {
            return computeSingle(pack.base, out, world, flicker, nightvision, gamma);
        }
        if (rain > 0.0F) thunder /= rain;
        float clearWeight = 1.0F - rain, rainWeight = rain - thunder, thunderWeight = thunder;

        if (this.blend1.length != out.length) {
            this.blend1 = new int[out.length];
            this.blend2 = new int[out.length];
        }
        int[][] buffers = {out, this.blend1, this.blend2};
        float[] weights = new float[3];
        int count = 0;
        if (clearWeight > 1.0E-4F && computeSingle(pack.base, buffers[count], world, flicker, nightvision, gamma)) {
            weights[count++] = clearWeight;
        }
        if (rainWeight > 1.0E-4F && pack.rain != null && computeSingle(pack.rain, buffers[count], world, flicker, nightvision, gamma)) {
            weights[count++] = rainWeight;
        }
        if (thunderWeight > 1.0E-4F && pack.thunder != null && computeSingle(pack.thunder, buffers[count], world, flicker, nightvision, gamma)) {
            weights[count++] = thunderWeight;
        }
        if (count == 0) return false;
        for (int i = 0; i < out.length; i++) {
            float r = 0, g = 0, b = 0, total = 0;
            for (int c = 0; c < count; c++) {
                int col = buffers[c][i];
                r += ColorARGB.unpackRed(col) * weights[c];
                g += ColorARGB.unpackGreen(col) * weights[c];
                b += ColorARGB.unpackBlue(col) * weights[c];
                total += weights[c];
            }
            out[i] = ColorARGB.pack((int) (r / total), (int) (g / total), (int) (b / total));
        }
        return true;
    }

    private boolean computeSingle(LightMap map, int[] out, World world, float flicker, boolean nightvision, float gamma) {
        int width = map.width, height = map.height;
        if (width < 16) return false;
        if (nightvision && height < 64) return false;

        int start = nightvision ? width * 16 * 2 : 0;
        float sun = 1.1666666F * (world.calculateAmbientLight(1.0F) - 0.2F);
        if (world.getLightningCooldown() > 0) sun = 1.0F;
        sun = Math.clamp(sun, 0.0F, 1.0F);
        float sunX = sun * (width - 1);
        float torchX = Math.clamp(flicker + 0.5F, 0.0F, 1.0F) * (width - 1);
        boolean hasGamma = gamma > 1.0E-4F;

        readColumn(map, sunX, start, this.sunRgb);
        readColumn(map, torchX, start + 16 * width, this.torchRgb);

        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int r = channel(this.sunRgb[sky][0] + this.torchRgb[block][0], gamma, hasGamma);
                int g = channel(this.sunRgb[sky][1] + this.torchRgb[block][1], gamma, hasGamma);
                int b = channel(this.sunRgb[sky][2] + this.torchRgb[block][2], gamma, hasGamma);
                out[sky * 16 + block] = ColorARGB.pack(r, g, b);
            }
        }
        return true;
    }

    private static void readColumn(LightMap map, float x, int offset, float[][] out) {
        int width = map.width;
        int low = (int) Math.floor(x);
        int high = (int) Math.ceil(x);
        float dLow = 1.0F - (x - low), dHigh = 1.0F - (high - x);
        for (int y = 0; y < 16; y++) {
            int pLow = map.pixels[offset + y * width + low];
            int pHigh = low == high ? pLow : map.pixels[offset + y * width + high];
            float weightLow = low == high ? 1.0F : dLow, weightHigh = low == high ? 0.0F : dHigh;
            out[y][0] = ColorU8.byteToNormalizedFloat(ColorARGB.unpackRed(pLow)) * weightLow + ColorU8.byteToNormalizedFloat(ColorARGB.unpackRed(pHigh)) * weightHigh;
            out[y][1] = ColorU8.byteToNormalizedFloat(ColorARGB.unpackGreen(pLow)) * weightLow + ColorU8.byteToNormalizedFloat(ColorARGB.unpackGreen(pHigh)) * weightHigh;
            out[y][2] = ColorU8.byteToNormalizedFloat(ColorARGB.unpackBlue(pLow)) * weightLow + ColorU8.byteToNormalizedFloat(ColorARGB.unpackBlue(pHigh)) * weightHigh;
        }
    }

    private static int channel(float value, float gamma, boolean hasGamma) {
        value = Math.clamp(value, 0.0F, 1.0F);
        if (hasGamma) {
            float bright = 1.0F - value;
            bright = 1.0F - bright * bright * bright * bright;
            value = gamma * bright + (1.0F - gamma) * value;
        }
        return ColorU8.normalizedFloatToByte(value);
    }

    private static Int2ObjectMap<Pack> load(ResourceManager resources) {
        Int2ObjectMap<Pack> packs = new Int2ObjectOpenHashMap<>();
        for (String directory : DIRECTORIES) {
            resources.findResources("minecraft", directory, id -> WORLD.matcher(id.identifier()).matches())
                    .keySet().forEach(id -> {
                        Matcher matcher = WORLD.matcher(id.identifier());
                        if (!matcher.matches()) return;
                        int dimension = Integer.parseInt(matcher.group(1));
                        if (packs.containsKey(dimension)) return;
                        LightMap base = read(resources, directory + "world" + dimension + ".png");
                        if (base == null) return;
                        packs.put(dimension, new Pack(base,
                                read(resources, directory + "world" + dimension + "_rain.png"),
                                read(resources, directory + "world" + dimension + "_thunder.png")));
                    });
        }
        return packs;
    }

    private static LightMap read(ResourceManager resources, String path) {
        Resource resource = resources.getResource(new Identifier(path)).orElse(null);
        if (resource == null) return null;
        try (resource; InputStream in = resource.open()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return null;
            int width = image.getWidth(), height = image.getHeight();
            if (width < 16) {
                Cera.LOGGER.warn("[LightMaps] Invalid lightmap width {}: {}", width, path);
                return null;
            }
            return new LightMap(image.getRGB(0, 0, width, height, null, 0, width), width, height);
        } catch (IOException e) {
            Cera.LOGGER.warn("[LightMaps] Failed to read {}", path, e);
            return null;
        }
    }

    private record LightMap(int[] pixels, int width, int height) {
    }

    private record Pack(LightMap base, LightMap rain, LightMap thunder) {
    }
}
