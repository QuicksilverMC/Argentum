package dev.rdh.argentum.impl.debug;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import net.minecraft.text.Formatting;

import java.lang.management.ManagementFactory;
import java.util.List;

public class DebugStrings {
    public static List<Pair<String, Formatting>> getStringsToRender() {
        List<Pair<String, Formatting>> stringsToRender = new ObjectArrayList<>();

        stringsToRender.add(Pair.of(getNativeMemoryString(), Formatting.RESET));
        stringsToRender.add(Pair.of("", Formatting.RESET));

        stringsToRender.add(Pair.of("Argentum Renderer (%s)".formatted(Argentum.VERSION), Formatting.GREEN));
        stringsToRender.add(Pair.of("Chunk Draws: " + (Argentum.indirectChunkDraws() ? "Indirect" : "Direct"), Formatting.RESET));
        stringsToRender.add(Pair.of("", Formatting.RESET));

        var renderer = ArgentumWorldRenderer.instanceNullable();

        if (renderer != null) {
            stringsToRender.add(Pair.of(renderer.getEntityInstancing().getDebugString(), Formatting.RESET));
            stringsToRender.add(Pair.of("", Formatting.RESET));
            renderer.getDebugStrings().forEach(str -> stringsToRender.add(Pair.of(str, Formatting.RESET)));
        }

        return stringsToRender;
    }

    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }
}
