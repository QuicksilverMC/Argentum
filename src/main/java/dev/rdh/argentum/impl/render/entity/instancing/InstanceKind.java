package dev.rdh.argentum.impl.render.entity.instancing;

/**
 * Identifies the source of a captured draw for
 * {@link dev.rdh.argentum.api.render.instancing.InstanceDrawTarget#beginDraw}.
 */
public enum InstanceKind {
    /** An entity; the draw ID is its entity registry ID. */
    ENTITY,
    /** A block entity; the draw ID packs the block registry ID and four metadata bits. */
    BLOCK_ENTITY,
    /** A dropped item; the draw ID identifies the item-entity type, not the contained item. */
    ITEM
}
