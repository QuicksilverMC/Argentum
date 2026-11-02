package dev.rdh.cera.modules.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;

public final class ShaderSkyBox {
    private ShaderSkyBox() {
    }

    public static void preSkyList() {
        var state = Minecraft.getInstance().cera$getShaderEngine().state();
        state.setUpPosition();
        var fogColor = state.fogColor();
        GlStateManager.color4f((float) fogColor.x(), (float) fogColor.y(), (float) fogColor.z(), 1F);

        double farDistance = Minecraft.getInstance().options.viewDistance * 16;
        double xzq = farDistance * 0.9238;
        double xzp = farDistance * 0.3826;
        double xzn = -xzp;
        double xzm = -xzq;
        double top = 16f;
        double bot = -state.camPos()
                          .y();

        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.getBuffer();
        buffer.begin(7, DefaultVertexFormat.POSITION);
        buffer.vertex(xzn, bot, xzm).nextVertex();
        buffer.vertex(xzn, top, xzm).nextVertex();
        buffer.vertex(xzm, top, xzn).nextVertex();
        buffer.vertex(xzm, bot, xzn).nextVertex();
        buffer.vertex(xzm, bot, xzn).nextVertex();
        buffer.vertex(xzm, top, xzn).nextVertex();
        buffer.vertex(xzm, top, xzp).nextVertex();
        buffer.vertex(xzm, bot, xzp).nextVertex();
        buffer.vertex(xzm, bot, xzp).nextVertex();
        buffer.vertex(xzm, top, xzp).nextVertex();
        buffer.vertex(xzn, top, xzp).nextVertex();
        buffer.vertex(xzn, bot, xzp).nextVertex();
        buffer.vertex(xzn, bot, xzp).nextVertex();
        buffer.vertex(xzn, top, xzp).nextVertex();
        buffer.vertex(xzp, top, xzq).nextVertex();
        buffer.vertex(xzp, bot, xzq).nextVertex();
        buffer.vertex(xzp, bot, xzq).nextVertex();
        buffer.vertex(xzp, top, xzq).nextVertex();
        buffer.vertex(xzq, top, xzp).nextVertex();
        buffer.vertex(xzq, bot, xzp).nextVertex();
        buffer.vertex(xzq, bot, xzp).nextVertex();
        buffer.vertex(xzq, top, xzp).nextVertex();
        buffer.vertex(xzq, top, xzn).nextVertex();
        buffer.vertex(xzq, bot, xzn).nextVertex();
        buffer.vertex(xzq, bot, xzn).nextVertex();
        buffer.vertex(xzq, top, xzn).nextVertex();
        buffer.vertex(xzp, top, xzm).nextVertex();
        buffer.vertex(xzp, bot, xzm).nextVertex();
        buffer.vertex(xzp, bot, xzm).nextVertex();
        buffer.vertex(xzp, top, xzm).nextVertex();
        buffer.vertex(xzn, top, xzm).nextVertex();
        buffer.vertex(xzn, bot, xzm).nextVertex();
        tesselator.end();

        var skyColor = state.skyColor();
        GlStateManager.color4f((float) skyColor.x(), (float) skyColor.y(), (float) skyColor.z(), 1F);
    }
}
