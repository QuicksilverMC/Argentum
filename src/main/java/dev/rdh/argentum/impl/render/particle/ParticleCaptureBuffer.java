package dev.rdh.argentum.impl.render.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import java.nio.ByteOrder;
import java.util.Arrays;

final class ParticleCaptureBuffer extends BufferBuilder {
    private static final int STRIDE = DefaultVertexFormat.PARTICLE.getIntSize();
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private int[] vertices = new int[8 * STRIDE];
    private int count;
    private int x;
    private int y;
    private int z;
    private int u;
    private int v;
    private int color;
    private int light;
    private boolean unsupported;

    ParticleCaptureBuffer() {
        super(64);
        super.begin(GL11.GL_QUADS, DefaultVertexFormat.PARTICLE);
    }

    void reset() {
        this.count = 0;
        this.unsupported = false;
    }

    int ints() {
        return this.count * STRIDE;
    }

    int[] vertices() {
        return this.vertices;
    }

    boolean unsupported() {
        return this.unsupported;
    }

    @Override
    public BufferBuilder vertex(double x, double y, double z) {
        this.x = Float.floatToRawIntBits((float)x);
        this.y = Float.floatToRawIntBits((float)y);
        this.z = Float.floatToRawIntBits((float)z);
        return this;
    }

    @Override
    public BufferBuilder texture(double u, double v) {
        this.u = Float.floatToRawIntBits((float)u);
        this.v = Float.floatToRawIntBits((float)v);
        return this;
    }

    @Override
    public BufferBuilder texture(int u, int v) {
        this.light = LITTLE_ENDIAN ? v & 0xFFFF | u << 16 : v << 16 | u & 0xFFFF;
        return this;
    }

    @Override
    public BufferBuilder color(int r, int g, int b, int a) {
        this.color = r & 0xFF | (g & 0xFF) << 8 | (b & 0xFF) << 16 | a << 24;
        return this;
    }

    @Override
    public void nextVertex() {
        int i = this.count * STRIDE;
        if (i + STRIDE > this.vertices.length) this.vertices = Arrays.copyOf(this.vertices, this.vertices.length * 2);
        this.vertices[i] = this.x;
        this.vertices[i + 1] = this.y;
        this.vertices[i + 2] = this.z;
        this.vertices[i + 3] = this.u;
        this.vertices[i + 4] = this.v;
        this.vertices[i + 5] = this.color;
        this.vertices[i + 6] = this.light;
        this.count++;
    }

    @Override
    public int getVertexCount() {
        return this.count;
    }

    @Override
    public void vertices(int[] vertices) {
        this.unsupported = true;
    }

    @Override
    public void lightColor(int r, int g, int b, int a) {
        this.unsupported = true;
    }

    @Override
    public void postPosition(double x, double y, double z) {
        this.unsupported = true;
    }

    @Override
    public void multiplyColor(float r, float g, float b, int index) {
        this.unsupported = true;
    }

    @Override
    public void setColor(float r, float g, float b, int index) {
        this.unsupported = true;
    }

    @Override
    public void uncolored() {
        this.unsupported = true;
    }

    @Override
    public void postNormal(float x, float y, float z) {
        this.unsupported = true;
    }

    @Override
    public BufferBuilder normal(float x, float y, float z) {
        this.unsupported = true;
        return this;
    }

    @Override
    public void offset(double x, double y, double z) {
        this.unsupported = true;
    }

    @Override
    public void setQuadColor(int rgb) {
        this.unsupported = true;
    }

    @Override
    public void setQuadColor(float r, float g, float b) {
        this.unsupported = true;
    }

    @Override
    public void sortQuads(float x, float y, float z) {
        this.unsupported = true;
    }

    @Override
    public void setState(BufferBuilder.State state) {
        this.unsupported = true;
    }

    @Override
    public void begin(int drawMode, VertexFormat format) {
        this.unsupported = true;
    }

    @Override
    public void end() {
        this.unsupported = true;
    }

    @Override
    public void clear() {
        this.unsupported = true;
    }
}
