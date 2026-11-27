package dev.rdh.argentum.impl.render.entity.instancing;

public final class CapturedOverlay {
    private float tickDelta;
    private boolean combine;
    private boolean deferred;
    private boolean applied;
    private int immediateDraws;

    void reset() {
        this.deferred = false;
        this.applied = false;
        this.immediateDraws = 0;
    }

    public boolean beginSetup(float tickDelta, boolean combine) {
        this.tickDelta = tickDelta;
        this.combine = combine;
        return this.immediateDraws == 0;
    }

    public void endSetup(boolean skipped, boolean overlay) {
        if (skipped) {
            this.deferred = overlay;
        } else {
            this.applied |= overlay;
            this.deferred = false;
        }
    }

    public boolean beginTeardown() {
        return !this.applied;
    }

    public void endTeardown() {
        this.deferred = false;
        this.applied = false;
    }

    public boolean beginImmediate() {
        this.immediateDraws++;
        return this.deferred && !this.applied;
    }

    public void endImmediate() {
        this.immediateDraws--;
    }

    public float tickDelta() {
        return this.tickDelta;
    }

    public boolean combine() {
        return this.combine;
    }
}
