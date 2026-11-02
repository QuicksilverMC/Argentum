package dev.rdh.argentum.api.render.instancing;

import dev.rdh.argentum.impl.render.entity.instancing.InstanceKind;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;

/**
 * Supplies shader state for captured entity, block-entity and dropped-item batches.
 * When active, Argentum expands captured geometry into standard vertex, color, normal and texture-coordinate
 * arrays instead of using its own instancing shader. The target owns shader selection and uniforms;
 * Argentum owns geometry upload, texture binding, draw submission and the pass's normal GL state setup.
 * All callbacks run on the render thread.
 *
 * <p>Install through {@link dev.rdh.argentum.impl.ext.MinecraftExtension#argentum$setInstanceDrawTarget}.
 * Each nonempty pass calls {@link #beginPass}, then {@link #beginDraw} before each draw, followed by
 * {@link #endPass} in a {@code finally} block. There is no per-draw end callback.
 */
public interface InstanceDrawTarget {
    /**
     * Selects the external rendering path at the start of a capture batch.
     *
     * @return whether captured geometry should use this target for that batch
     */
    boolean active();

    /**
     * Selects shader state for a nonempty pass, after Argentum has configured its normal GL state.
     * Once this returns successfully, {@link #endPass} is called even if a draw fails. A target that throws
     * here must clean up any state it already changed.
     *
     * @param pass the material/effect pass about to be drawn
     */
    void beginPass(InstanceRenderPass pass);

    /**
     * Updates identity-dependent shader state before a draw. Argentum binds the batch texture after this call.
     * The identity describes the captured source, not necessarily one object: instances sharing it may be batched.
     * IDs have not been remapped by the external shader engine.
     *
     * @param kind the source category, which determines how to interpret {@code id}
     * @param id the entity registry ID for entities and dropped items, or {@code (blockId << 4) | metadata}
     *           for block entities; dropped items use the item-entity type, not the contained item's ID
     */
    void beginDraw(InstanceKind kind, int id);

    /**
     * Restores shader state changed by {@link #beginPass} and {@link #beginDraw}, including on draw failure.
     * This ends the current pass, not the enclosing capture batch.
     *
     * @param pass the same pass passed to {@link #beginPass}
     */
    void endPass(InstanceRenderPass pass);
}
