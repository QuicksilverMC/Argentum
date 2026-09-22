package dev.rdh.argentum.impl.render.entity.instancing;

import org.embeddedt.embeddium.impl.gl.device.CommandList;

public abstract class InstanceGeometry {
    private InstanceBatcher.TextureBatch batch;
    private Instances instances;

    final Instances instances(InstanceBatcher.TextureBatch batch) {
        if (this.batch != batch) {
            this.batch = batch;
            this.instances = batch.instances(this);
        }
        return this.instances;
    }

    abstract void render(CommandList commandList, Instances instances);

    /** {@return whether this draws the shared unit cube} The shader rebuilds position and uv for those. */
    boolean usesBoxInstancing() {
        return false;
    }

    public abstract void delete(CommandList commandList);
}
