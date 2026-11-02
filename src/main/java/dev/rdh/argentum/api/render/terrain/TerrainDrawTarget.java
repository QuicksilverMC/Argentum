package dev.rdh.argentum.api.render.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.Nullable;

/**
 * Lets an external shader engine draw Argentum's terrain batches with its own program and vertex layout.
 * Argentum still owns chunk compilation, visibility, buffers and draw submission. The target owns the bound
 * shader program, framebuffer and camera matrices; Argentum applies each region's translation to model-view.
 * All callbacks run on the render thread.
 *
 * <p>Install through {@link dev.rdh.argentum.impl.ext.MinecraftExtension#argentum$setTerrainDrawTarget}.
 * The vertex layout and shadow-list support are selected when the section manager is created. Reload chunk
 * renderers when either changes, including when enabling or disabling the external shader engine.
 */
public interface TerrainDrawTarget {
    /**
     * Selects the layout used to compile terrain and whether Argentum uses the external rendering path.
     *
     * @return the external vertex layout, or {@code null} to use Argentum's normal terrain renderer
     */
    @Nullable ChunkVertexType vertexType();

    /**
     * Configures the vertex pointers and enables the arrays/attributes required by {@link #vertexType()}.
     * Called while configuring a region tessellation, after its vertex and index buffers are bound.
     * The target must leave its shader program and framebuffer bound.
     */
    void bindVertexAttributes();

    /**
     * Requests separate terrain visibility lists for a shadow pass. This is sampled when the section manager
     * is created; changing it requires reloading chunk renderers.
     *
     * @return whether to allocate and maintain shadow render lists
     */
    boolean hasShadowPass();

    /**
     * Selects shadow visibility lists and disables camera-facing block-face culling while drawing shadows.
     * The external engine must expose the correct value for the duration of its shadow pass.
     *
     * @return whether terrain is currently being rendered into the shadow map
     */
    boolean isShadowPass();
}
