package dev.rdh.argentum.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import org.embeddedt.embeddium.impl.gui.framework.DrawContext;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.framework.TextFormattingStyle;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.jetbrains.annotations.Nullable;

import dev.rdh.argentum.impl.Argentum;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LegacyDrawContext implements DrawContext {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final TextRenderer font = this.minecraft.textRenderer;

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiElement.fill(x1, y1, x2, y2, color);
    }

    @Override
    public int drawString(TextComponent text, int x, int y, int color, boolean shadow) {
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        int width = this.font.draw(this.extractString(text), x, y, color, shadow);
        GlStateManager.disableBlend();
        return width;
    }

    @Override
    public void blitWholeImage(@NotNull String texture, int x, int y, int width, int height) {
        this.minecraft.getTextureManager().bind(new Identifier(texture));
        GuiElement.drawTexture(x, y, 0.0F, 0.0F, width, height, width, height);
    }

    @Override
    public void pushMatrix() {
        GlStateManager.pushMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translated(x, y, z);
    }

    @Override
    public void popMatrix() {
        GlStateManager.popMatrix();
    }

    @Override
    public void enableScissor(int x, int y, int x2, int y2) {
        Window window = new Window(this.minecraft);
        int scale = window.getScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, this.minecraft.height - y2 * scale, (x2 - x) * scale, (y2 - y) * scale);
    }

    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public int getStringWidth(TextComponent text) {
        return this.font.getWidth(this.extractString(text));
    }

    @Override
    public String substrByWidth(String text, int width) {
        return this.font.trim(text, width);
    }

    @Override
    public List<TextComponent> split(TextComponent text, int width) {
        return this.font.split(this.extractString(text), width).stream().map(TextComponent::literal).toList();
    }

    @Override
    public String extractString(TextComponent component) {
        if (component instanceof TextComponent.Literal(String text)) {
            return text;
        }
        if (component instanceof TextComponent.Translatable(List<String> keys, List<?> args)) {
            return I18n.translate(
                    keys.getFirst(),
                    args.stream()
                            .map(arg -> arg instanceof TextComponent c ? this.extractString(c) : arg)
                            .toArray()
            );
        }
        if (component instanceof TextComponent.Styled(TextComponent inner, java.util.EnumSet<TextFormattingStyle> styles)) {
            StringBuilder result = new StringBuilder();
            for (TextFormattingStyle style : styles) {
                result.append('§').append(style.isColor() ? Integer.toHexString(style.ordinal())
                        : switch (style) {
                            case STRIKETHROUGH -> "m";
                            case UNDERLINE -> "n";
                            case ITALIC -> "o";
                            default -> "";
                        }
                );
            }
            return result.append(this.extractString(inner)).append("§r").toString();
        }
        throw new IllegalArgumentException("Unsupported text component: " + component.getClass().getName());
    }

    @Override
    public int lineHeight() {
        return this.font.fontHeight;
    }

    @Override
    public TextComponent getFriendlyModName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> TextComponent.literal(container.getMetadata().getName()))
                .orElseGet(() -> DrawContext.super.getFriendlyModName(modId));
    }

    @Override
    public @Nullable String getModLogoPath(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.getMetadata().getIconPath(32).flatMap(container::findPath))
                .map(path -> this.rememberPath(modId, path))
                .orElse(null);
    }

    private final Map<Path, Identifier> loadedLogos = new Object2ObjectOpenHashMap<>();
    private final Set<Path> erroredModLogos = new ObjectOpenHashSet<>();

    private String rememberPath(String modId, Path path) {
        if (erroredModLogos.contains(path)) return null;
        Identifier tex = loadedLogos.get(path);
        if (tex != null) return tex.toString();
        tex = new Identifier(Argentum.ID, "mod_logo/" + modId);
        if (this.minecraft.getTextureManager().get(tex) != null) {
            loadedLogos.put(path, tex);
            return tex.toString();
        }
        try (InputStream stream = Files.newInputStream(path)) {
            BufferedImage img = ImageIO.read(stream);
            if (img == null || img.getWidth() != img.getHeight()) {
                Argentum.LOGGER.warn("Mod icon {} is not square, ignoring", path);
                erroredModLogos.add(path);
                return null;
            }

            this.minecraft.getTextureManager().register(tex, new DynamicTexture(img));
            loadedLogos.put(path, tex);
            return tex.toString();
        } catch (IOException e) {
			Argentum.LOGGER.warn("Failed to read mod icon {}", path, e);
            erroredModLogos.add(path);
            return null;
		}
    }
}
