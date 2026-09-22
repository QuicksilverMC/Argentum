package dev.rdh.argentum.impl.ext;

import net.minecraft.text.Text;

import java.util.List;

public interface SignTextCache {
    default List<Text> argentum$getWrappedLine(Text line, boolean unicode) {
        return null;
    }

    default void argentum$putWrappedLine(Text line, boolean unicode, List<Text> wrapped) {
    }
}
