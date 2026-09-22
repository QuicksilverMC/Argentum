package dev.rdh.argentum.impl.render.instancing;

import dev.rdh.argentum.mixin.features.model.instancing.BoxAccessor;

import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.model.Vertex;

/**
 * A box reduced to the arguments vanilla built it from, so the whole thing can be drawn from one shared unit cube
 * with the parameters carried per instance.
 * <p>
 * Nothing keeps those arguments around - {@link Box} stores only its corners and the polygons it baked - so they are
 * recovered and then checked by rebuilding every vertex and comparing against what is actually there. A box that
 * does not reproduce exactly is not ours to draw, and the caller falls back to per-part geometry.
 */
public record BoxTemplate(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                          int textureU, int textureV, int sizeX, int sizeY, int sizeZ,
                          float textureWidth, float textureHeight, boolean flipped) {

    private static final float EPSILON = 1.0E-4F;

    /** Which of the eight corners each face's four vertices use, in the order vanilla emits them. */
    public static final int[][] FACE_CORNERS = {
            {5, 1, 2, 6},
            {0, 4, 7, 3},
            {5, 4, 0, 1},
            {2, 3, 7, 6},
            {1, 0, 3, 2},
            {4, 5, 6, 7},
    };

    /**
     * Per face, the u and v rectangle as coefficients of (1, sizeX, sizeY, sizeZ) added to the texture origin:
     * {u1, u2} then {v1, v2}, each as {sizeX coefficient, sizeY coefficient, sizeZ coefficient}.
     */
    public static final int[][] FACE_U = {{1, 1}, {0, 0}, {0, 1}, {1, 2}, {0, 1}, {1, 2}};
    public static final int[][] FACE_U_Z = {{1, 2}, {0, 1}, {1, 1}, {1, 1}, {1, 1}, {2, 2}};
    public static final int[][] FACE_V = {{0, 1}, {0, 1}, {0, 0}, {0, 0}, {0, 1}, {0, 1}};
    public static final int[][] FACE_V_Z = {{1, 1}, {1, 1}, {0, 1}, {1, 0}, {1, 1}, {1, 1}};

    public static BoxTemplate of(ModelPart part, Box box) {
        Polygon[] faces = ((BoxAccessor)box).celeritas$getFaces();
        if (faces.length != 6) {
            return null;
        }
        for (Polygon face : faces) {
            if (face.vertices.length != 4) {
                return null;
            }
        }

        int sizeX = Math.round(box.maxX - box.minX);
        int sizeY = Math.round(box.maxY - box.minY);
        int sizeZ = Math.round(box.maxZ - box.minZ);
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            return null;
        }

        // the polygons carry the inflated corners, so the amount a box was grown by comes back out of them
        float lowX = Float.MAX_VALUE, lowY = Float.MAX_VALUE, lowZ = Float.MAX_VALUE;
        float highX = -Float.MAX_VALUE, highY = -Float.MAX_VALUE, highZ = -Float.MAX_VALUE;
        for (Polygon face : faces) {
            for (Vertex vertex : face.vertices) {
                lowX = Math.min(lowX, (float)vertex.pos.x);
                lowY = Math.min(lowY, (float)vertex.pos.y);
                lowZ = Math.min(lowZ, (float)vertex.pos.z);
                highX = Math.max(highX, (float)vertex.pos.x);
                highY = Math.max(highY, (float)vertex.pos.y);
                highZ = Math.max(highZ, (float)vertex.pos.z);
            }
        }

        float width = part.textureWidth;
        float height = part.textureHeight;
        if (width <= 0.0F || height <= 0.0F) {
            return null;
        }
        // face 1 starts at the texture origin, and face 2 starts one depth below it
        int textureU = Math.round(minU(faces[1]) * width);
        int textureV = Math.round(minV(faces[2]) * height);

        // a flipped box has its x corners swapped and every polygon reversed; rather than guess which happened,
        // try both and keep whichever rebuilds the baked geometry
        for (boolean flipped : new boolean[]{false, true}) {
            BoxTemplate template = new BoxTemplate(lowX, lowY, lowZ, highX, highY, highZ,
                    textureU, textureV, sizeX, sizeY, sizeZ, width, height, flipped);
            if (template.matches(faces)) {
                return template;
            }
        }
        return null;
    }

    public static boolean cornerHighX(int corner) {
        return corner == 1 || corner == 2 || corner == 5 || corner == 6;
    }

    public static boolean cornerHighY(int corner) {
        return corner == 2 || corner == 3 || corner == 6 || corner == 7;
    }

    public static boolean cornerHighZ(int corner) {
        return corner >= 4;
    }

    private boolean matches(Polygon[] faces) {
        for (int face = 0; face < 6; face++) {
            Vertex[] vertices = faces[face].vertices;
            for (int corner = 0; corner < 4; corner++) {
                // a flipped box reverses each polygon, so its vertex i is the unflipped vertex 3 - i
                int source = this.flipped ? 3 - corner : corner;
                int index = FACE_CORNERS[face][source];
                Vertex vertex = vertices[corner];
                if (!near((float)vertex.pos.x, cornerHighX(index) != this.flipped ? this.maxX : this.minX)
                        || !near((float)vertex.pos.y, cornerHighY(index) ? this.maxY : this.minY)
                        || !near((float)vertex.pos.z, cornerHighZ(index) ? this.maxZ : this.minZ)) {
                    return false;
                }
                if (!near(vertex.u, this.faceU(face, source)) || !near(vertex.v, this.faceV(face, source))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@return the u for one corner of a face} Corners 0 and 3 take the far edge, 1 and 2 the near one. */
    public float faceU(int face, int corner) {
        int side = corner == 0 || corner == 3 ? 1 : 0;
        return (this.textureU + FACE_U[face][side] * this.sizeX + FACE_U_Z[face][side] * this.sizeZ)
                / this.textureWidth;
    }

    /** {@return the v for one corner of a face} Corners 0 and 1 take the top edge, 2 and 3 the bottom one. */
    public float faceV(int face, int corner) {
        int side = corner <= 1 ? 0 : 1;
        return (this.textureV + FACE_V[face][side] * this.sizeY + FACE_V_Z[face][side] * this.sizeZ)
                / this.textureHeight;
    }

    /** {@return where the unit cube's origin corner goes} Mirrored boxes start at the far edge and span back. */
    public float originX() {
        return this.flipped ? this.maxX : this.minX;
    }

    public float spanX() {
        return this.flipped ? this.minX - this.maxX : this.maxX - this.minX;
    }

    public float spanY() {
        return this.maxY - this.minY;
    }

    public float spanZ() {
        return this.maxZ - this.minZ;
    }

    private static float minU(Polygon face) {
        float value = Float.MAX_VALUE;
        for (Vertex vertex : face.vertices) {
            value = Math.min(value, vertex.u);
        }
        return value;
    }

    private static float minV(Polygon face) {
        float value = Float.MAX_VALUE;
        for (Vertex vertex : face.vertices) {
            value = Math.min(value, vertex.v);
        }
        return value;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }
}
