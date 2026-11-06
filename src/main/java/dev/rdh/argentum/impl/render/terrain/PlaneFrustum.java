package dev.rdh.argentum.impl.render.terrain;

import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;

public final class PlaneFrustum implements Frustum {
    private final float[] planes;

    public PlaneFrustum(float[] planes) {
        this.planes = planes;
    }

    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        final float[] planes = this.planes;
        // `i + 3` lets bounds checks fold away
        for (int i = 0; i + 3 < planes.length; i += 4) {
            final float a = planes[i];
            final float b = planes[i + 1];
            final float c = planes[i + 2];

            if (a * (a < 0.0F ? minX : maxX)
                    + b * (b < 0.0F ? minY : maxY)
                    + c * (c < 0.0F ? minZ : maxZ)
                    + planes[i + 3] <= 0.0F) {
                return false;
            }
        }

        return true;
    }
}
