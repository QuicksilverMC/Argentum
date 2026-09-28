package dev.rdh.argentum.impl.ext;

import net.minecraft.text.Text;

import java.util.List;

public interface SignTextCache {
    default List<Text> argentum$getWrappedLine(Text line, int fontGeneration) {
        return null;
    }

    default void argentum$putWrappedLine(Text line, int fontGeneration, List<Text> wrapped) {
    }
}
