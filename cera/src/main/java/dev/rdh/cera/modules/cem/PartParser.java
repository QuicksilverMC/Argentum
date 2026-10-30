package dev.rdh.cera.modules.cem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.rdh.argentum.impl.ext.ModelPartExtension;
import dev.rdh.cera.props.Json;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.model.Vertex;
import net.minecraft.resource.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PartParser {
    private PartParser() {
    }

    public static ModelPart parse(JsonObject json, Model owner, int[] inheritedTextureSize, String basePath) {
        ModelPart part = new ModelPart(owner, Json.string(json, "id"));
        ModelPartExtension extension = (ModelPartExtension)part;
        extension.argentum$getScale().set(Json.number(json, "scale", 1.0F));
        String texture = Json.string(json, "texture");
        if (texture != null) extension.argentum$setTexture(resource(basePath, texture, ".png"));
        int[] textureSize = Json.ints(json.get("textureSize"), 2);
        if (textureSize == null) textureSize = inheritedTextureSize;
        if (textureSize != null) part.setTextureSize(textureSize[0], textureSize[1]);

        String invert = lower(Json.string(json, "invertAxis"));
        float[] translate = Json.floats(json.get("translate"), 3);
        float[] rotate = Json.floats(json.get("rotate"), 3);
        if (translate == null) translate = new float[3];
        if (rotate == null) rotate = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            rotate[axis] = rotate[axis] / 180.0F * (float)Math.PI;
            if (invert.indexOf("xyz".charAt(axis)) >= 0) {
                translate[axis] = -translate[axis];
                rotate[axis] = -rotate[axis];
            }
        }
        part.setPos(translate[0], translate[1], translate[2]);
        part.rotationX = rotate[0];
        part.rotationY = rotate[1];
        part.rotationZ = rotate[2];
        String mirror = lower(Json.string(json, "mirrorTexture"));
        part.flipped = mirror.contains("u");
        boolean mirrorV = mirror.contains("v");

        for (JsonObject box : Json.objects(json, "boxes")) {
            int[] offset = Json.ints(box.get("textureOffset"), 2);
            int[][] faceUvs = faceUvs(box);
            if (offset == null && faceUvs == null) throw new JsonParseException("Texture offset not specified");
            float[] coordinates = coordinates(box, invert);
            float sizeAdd = Json.number(box, "sizeAdd", 0.0F);
            float[] sizesAdd = Json.floats(box.get("sizesAdd"), 3);
            if (sizesAdd != null && faceUvs == null) {
                faceUvs = boxUvs(offset, (int)coordinates[3], (int)coordinates[4], (int)coordinates[5]);
            }
            if (faceUvs != null) {
                part.boxes.add(new UvBox(part, faceUvs, coordinates, sizesAdd != null ? sizesAdd : new float[]{sizeAdd, sizeAdd, sizeAdd}));
            } else {
                part.setTextureCoords(offset[0], offset[1]);
                part.addBox(coordinates[0], coordinates[1], coordinates[2],
                        (int)coordinates[3], (int)coordinates[4], (int)coordinates[5], sizeAdd);
            }
        }
        for (JsonObject sprite : Json.objects(json, "sprites")) {
            int[] offset = Json.ints(sprite.get("textureOffset"), 2);
            if (offset == null) throw new JsonParseException("Texture offset not specified");
            part.boxes.add(new SpriteBox(part, offset, coordinates(sprite, invert), mirrorV));
        }

        JsonObject submodel = Json.object(json.get("submodel"));
        if (submodel != null) part.addChild(parse(submodel, owner, textureSize, basePath));
        for (JsonObject child : Json.objects(json, "submodels")) {
            part.addChild(parse(child, owner, textureSize, basePath));
        }
        return part;
    }

    public static Identifier resource(String basePath, String path, String extension) {
        if (!path.endsWith(extension)) path += extension;
        if (!path.contains("/")) path = basePath + "/" + path;
        else if (path.startsWith("./")) path = basePath + "/" + path.substring(2);
        else if (path.startsWith("~/")) path = "optifine/" + path.substring(2);
        return new Identifier(path);
    }

    public static void inherit(JsonObject from, JsonObject to) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            if (!entry.getKey().equals("id") && !to.has(entry.getKey())) to.add(entry.getKey(), entry.getValue());
        }
    }

    private static float[] coordinates(JsonObject box, String invert) {
        float[] coordinates = Json.floats(box.get("coordinates"), 6);
        if (coordinates == null) throw new JsonParseException("Coordinates not specified");
        for (int axis = 0; axis < 3; axis++) {
            if (invert.indexOf("xyz".charAt(axis)) >= 0) {
                coordinates[axis] = -coordinates[axis] - coordinates[axis + 3];
            }
        }
        return coordinates;
    }

    // the layout vanilla's box gives a texture offset, in the order and orientation UvBox reads face uvs
    private static int[][] boxUvs(int[] offset, int width, int height, int depth) {
        int u = offset[0];
        int v = offset[1];
        return new int[][]{{u + depth + 2 * width, v, u + depth + width, v + depth},
                {u + depth + width, v + depth, u + depth, v},
                {u + depth, v + depth, u + depth + width, v + depth + height},
                {u + 2 * depth + width, v + depth, u + 2 * depth + 2 * width, v + depth + height},
                {u + depth + width, v + depth, u + 2 * depth + width, v + depth + height},
                {u, v + depth, u + depth, v + depth + height}};
    }

    private static int[][] faceUvs(JsonObject box) {
        int[][] uvs = {Json.ints(box.get("uvDown"), 4), Json.ints(box.get("uvUp"), 4), Json.ints(box.get("uvNorth"), 4),
                Json.ints(box.get("uvSouth"), 4), Json.ints(box.get("uvWest"), 4), Json.ints(box.get("uvEast"), 4)};
        if (uvs[2] == null) uvs[2] = Json.ints(box.get("uvFront"), 4);
        if (uvs[3] == null) uvs[3] = Json.ints(box.get("uvBack"), 4);
        if (uvs[4] == null) uvs[4] = Json.ints(box.get("uvLeft"), 4);
        if (uvs[5] == null) uvs[5] = Json.ints(box.get("uvRight"), 4);
        for (int[] uv : uvs) if (uv != null) return uvs;
        return null;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class UvBox extends Box {
        private UvBox(ModelPart part, int[][] uvs, float[] coordinates, float[] sizeAdd) {
            super(part, 0, 0, coordinates[0], coordinates[1], coordinates[2],
                    (int)coordinates[3], (int)coordinates[4], (int)coordinates[5], 0.0F, false);
            float minX = coordinates[0] - sizeAdd[0];
            float minY = coordinates[1] - sizeAdd[1];
            float minZ = coordinates[2] - sizeAdd[2];
            float maxX = coordinates[0] + coordinates[3] + sizeAdd[0];
            float maxY = coordinates[1] + coordinates[4] + sizeAdd[1];
            float maxZ = coordinates[2] + coordinates[5] + sizeAdd[2];
            if (part.flipped) {
                float swap = minX;
                minX = maxX;
                maxX = swap;
            }
            Vertex p0 = new Vertex(minX, minY, minZ, 0.0F, 0.0F);
            Vertex p1 = new Vertex(maxX, minY, minZ, 0.0F, 8.0F);
            Vertex p2 = new Vertex(maxX, maxY, minZ, 8.0F, 8.0F);
            Vertex p3 = new Vertex(minX, maxY, minZ, 8.0F, 0.0F);
            Vertex p4 = new Vertex(minX, minY, maxZ, 0.0F, 0.0F);
            Vertex p5 = new Vertex(maxX, minY, maxZ, 0.0F, 8.0F);
            Vertex p6 = new Vertex(maxX, maxY, maxZ, 8.0F, 8.0F);
            Vertex p7 = new Vertex(minX, maxY, maxZ, 8.0F, 0.0F);
            List<Polygon> faces = new ArrayList<>(6);
            face(faces, part, uvs[4], false, p5, p1, p2, p6);
            face(faces, part, uvs[5], false, p0, p4, p7, p3);
            face(faces, part, uvs[1], true, p5, p4, p0, p1);
            face(faces, part, uvs[0], true, p2, p3, p7, p6);
            face(faces, part, uvs[2], false, p1, p0, p3, p2);
            face(faces, part, uvs[3], false, p4, p5, p6, p7);
            if (part.flipped) {
                for (Polygon face : faces) face.flip();
            }
            this.faces = faces.toArray(new Polygon[0]);
        }

        private static void face(List<Polygon> faces, ModelPart part, int[] uv, boolean reversed, Vertex... vertices) {
            if (uv == null) return;
            faces.add(reversed
                    ? new Polygon(vertices, uv[2], uv[3], uv[0], uv[1], part.textureWidth, part.textureHeight)
                    : new Polygon(vertices, uv[0], uv[1], uv[2], uv[3], part.textureWidth, part.textureHeight));
        }
    }

    private static final class SpriteBox extends Box {
        private SpriteBox(ModelPart part, int[] offset, float[] coordinates, boolean mirrorV) {
            super(part, 0, 0, coordinates[0], coordinates[1], coordinates[2],
                    (int)coordinates[3], (int)coordinates[4], (int)coordinates[5], 0.0F, false);
            int sizeX = (int)coordinates[3];
            int sizeY = (int)coordinates[4];
            float depth = Math.max((int)coordinates[5], 0.01F);
            float minU = offset[0] / part.textureWidth;
            float maxU = (offset[0] + sizeX) / part.textureWidth;
            float minV = offset[1] / part.textureHeight;
            float maxV = (offset[1] + sizeY) / part.textureHeight;
            if (part.flipped) {
                float swap = minU;
                minU = maxU;
                maxU = swap;
            }
            if (mirrorV) {
                float swap = minV;
                minV = maxV;
                maxV = swap;
            }
            float du = maxU - minU;
            float dv = maxV - minV;
            float width = Math.abs(du) * part.textureWidth;
            float height = Math.abs(dv) * part.textureHeight;
            float x = coordinates[0];
            float y = coordinates[1];
            float z = coordinates[2];
            List<Polygon> faces = new ArrayList<>(2 + 2 * sizeX + 2 * sizeY);
            faces.add(quad(x, y, z, 0, height, 0, minU, maxV, width, height, 0, maxU, maxV,
                    width, 0, 0, maxU, minV, 0, 0, 0, minU, minV));
            faces.add(quad(x, y, z, 0, 0, depth, minU, minV, width, 0, depth, maxU, minV,
                    width, height, depth, maxU, maxV, 0, height, depth, minU, maxV));
            float halfU = 0.5F * du / sizeX;
            float halfV = 0.5F * dv / sizeY;
            for (int i = 0; i < sizeX; i++) {
                float from = (float)i / sizeX * width;
                float u = minU + du * ((float)i / sizeX) + halfU;
                faces.add(quad(x, y, z, from, height, depth, u, maxV, from, height, 0, u, maxV,
                        from, 0, 0, u, minV, from, 0, depth, u, minV));
            }
            for (int i = 0; i < sizeX; i++) {
                float to = ((float)i / sizeX + 1.0F / sizeX) * width;
                float u = minU + du * ((float)i / sizeX) + halfU;
                faces.add(quad(x, y, z, to, 0, depth, u, minV, to, 0, 0, u, minV,
                        to, height, 0, u, maxV, to, height, depth, u, maxV));
            }
            for (int i = 0; i < sizeY; i++) {
                float to = ((float)i / sizeY + 1.0F / sizeY) * height;
                float v = minV + dv * ((float)i / sizeY) + halfV;
                faces.add(quad(x, y, z, 0, to, depth, minU, v, width, to, depth, maxU, v,
                        width, to, 0, maxU, v, 0, to, 0, minU, v));
            }
            for (int i = 0; i < sizeY; i++) {
                float from = (float)i / sizeY * height;
                float v = minV + dv * ((float)i / sizeY) + halfV;
                faces.add(quad(x, y, z, width, from, depth, maxU, v, 0, from, depth, minU, v,
                        0, from, 0, minU, v, width, from, 0, maxU, v));
            }
            this.faces = faces.toArray(new Polygon[0]);
        }

        private static Polygon quad(float x, float y, float z, float... corners) {
            Vertex[] vertices = new Vertex[4];
            for (int i = 0; i < 4; i++) {
                int c = i * 5;
                vertices[i] = new Vertex(x + corners[c], y + corners[c + 1], z + corners[c + 2], corners[c + 3], corners[c + 4]);
            }
            return new Polygon(vertices);
        }
    }
}
