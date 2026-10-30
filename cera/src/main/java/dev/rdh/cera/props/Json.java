package dev.rdh.cera.props;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

public final class Json {
    private Json() {
    }

    public static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static List<JsonObject> objects(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonArray()) return List.of();
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new JsonParseException("Invalid " + key + " entry");
            objects.add(element.getAsJsonObject());
        }
        return objects;
    }

    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    public static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }

    public static int[] ints(JsonElement value, int length) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != length) return null;
        JsonArray array = value.getAsJsonArray();
        int[] values = new int[length];
        for (int index = 0; index < length; index++) values[index] = array.get(index).getAsInt();
        return values;
    }

    public static float[] floats(JsonElement value, int length) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != length) return null;
        JsonArray array = value.getAsJsonArray();
        float[] values = new float[length];
        for (int index = 0; index < length; index++) values[index] = array.get(index).getAsFloat();
        return values;
    }
}
