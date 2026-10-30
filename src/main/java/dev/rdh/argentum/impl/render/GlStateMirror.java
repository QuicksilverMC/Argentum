package dev.rdh.argentum.impl.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix4f;

public final class GlStateMirror {
    public static final int MODELVIEW = 0x1700;
    public static final int PROJECTION = 0x1701;
    public static final int MODELVIEW_MATRIX = 0xBA6;
    public static final int PROJECTION_MATRIX = 0xBA7;

    public static final boolean VERIFY = Boolean.getBoolean("argentum.verifyGlMirror");

    private static final int DEPTH = 64;
    private static final Matrix4f[][] STACKS = new Matrix4f[2][DEPTH];
    private static final int[] TOP = new int[2];
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final int[] VIEWPORT = new int[4];

    private static int current;
    private static int mode = MODELVIEW;
    private static boolean viewportKnown;

    static {
        for (Matrix4f[] stack : STACKS) {
            for (int i = 0; i < DEPTH; i++) stack[i] = new Matrix4f();
        }
    }

    public static void matrixMode(int newMode) {
        mode = newMode;
        current = newMode == MODELVIEW ? 0 : newMode == PROJECTION ? 1 : -1;
    }

    public static int matrixMode() {
        return mode;
    }

    private static Matrix4f top() {
        return current < 0 ? null : STACKS[current][TOP[current]];
    }

    public static void loadIdentity() {
        Matrix4f m = top();
        if (m != null) m.identity();
    }

    public static void push() {
        if (current < 0 || TOP[current] + 1 >= DEPTH) return;
        Matrix4f[] stack = STACKS[current];
        stack[TOP[current] + 1].set(stack[TOP[current]]);
        TOP[current]++;
    }

    public static void pop() {
        if (current >= 0 && TOP[current] > 0) TOP[current]--;
    }

    public static void translate(double x, double y, double z) {
        Matrix4f m = top();
        if (m != null) m.translate((float) x, (float) y, (float) z);
    }

    public static void scale(double x, double y, double z) {
        Matrix4f m = top();
        if (m != null) m.scale((float) x, (float) y, (float) z);
    }

    public static void rotate(float degrees, float x, float y, float z) {
        Matrix4f m = top();
        if (m == null) return;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0F) return;
        m.rotate((float) Math.toRadians(degrees), x / length, y / length, z / length);
    }

    public static void ortho(double l, double r, double b, double t, double n, double f) {
        Matrix4f m = top();
        if (m != null) m.ortho((float) l, (float) r, (float) b, (float) t, (float) n, (float) f);
    }

    public static void perspective(float fovDegrees, float aspect, float near, float far) {
        Matrix4f m = top();
        if (m != null) m.perspective((float) Math.toRadians(fovDegrees), aspect, near, far);
    }

    public static void multiply(FloatBuffer matrix) {
        Matrix4f m = top();
        if (m != null) m.mul(SCRATCH.set(matrix));
    }

    public static void viewport(int x, int y, int width, int height) {
        VIEWPORT[0] = x;
        VIEWPORT[1] = y;
        VIEWPORT[2] = width;
        VIEWPORT[3] = height;
        viewportKnown = true;
    }

    public static boolean readMatrix(int parameter, FloatBuffer out) {
        int stack = parameter == MODELVIEW_MATRIX ? 0 : parameter == PROJECTION_MATRIX ? 1 : -1;
        if (stack < 0) return false;
        STACKS[stack][TOP[stack]].get(out.position(), out);
        return true;
    }

    public static boolean readViewport(IntBuffer out) {
        if (!viewportKnown) return false;
        int p = out.position();
        for (int i = 0; i < 4; i++) out.put(p + i, VIEWPORT[i]);
        return true;
    }

    public static Matrix4f matrix(int parameter) {
        return STACKS[parameter == MODELVIEW_MATRIX ? 0 : 1][TOP[parameter == MODELVIEW_MATRIX ? 0 : 1]];
    }
}
