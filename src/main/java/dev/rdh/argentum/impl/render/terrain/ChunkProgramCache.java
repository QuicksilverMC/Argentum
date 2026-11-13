package dev.rdh.argentum.impl.render.terrain;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ChunkProgramCache {
    private record Key(List<ChunkShaderComponent.Factory<?>> components, Set<String> defines, ChunkVertexType vertexType) {
    }

    private final Map<Key, GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    GlProgram<ChunkShaderInterface> get(ChunkShaderOptions options, Function<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> compiler) {
        Key key = new Key(options.components(), Set.copyOf(options.constants().getDefineStrings()), options.pass().vertexType());
        return this.programs.computeIfAbsent(key, k -> compiler.apply(options));
    }

    public void delete() {
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
    }
}
