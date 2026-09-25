package dev.rdh.argentum.impl.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.boss.Boss;
import net.minecraft.util.math.Box;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EntityOcclusionCuller {
    private static final double RENDER_MARGIN = 0.5D;
    private static final double MOTION_MARGIN = 0.1D;
    private static final double EYE_TOLERANCE = 0.1D;
    private static final double MAX_BOX_VOLUME = 64.0D * 64.0D * 64.0D;

    private final ArgentumWorldRenderer renderer;
    private final Map<Entity, Query> queries = new Reference2ReferenceOpenHashMap<>();
    private final Matrix4f viewProjection = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f eye = new Vector3f();
    private long frame;
    private int queryMode = 0;

    public EntityOcclusionCuller(ArgentumWorldRenderer renderer) {
        this.renderer = renderer;
    }

    public void prepare(List<Entity> entities, Entity camera, float tickDelta, ChunkRenderMatrices matrices,
            double cameraX, double cameraY, double cameraZ) {
        if (!Argentum.CONFIG.entityCulling || matrices == null || !GL.getCapabilities().OpenGL15) {
            this.clear();
            return;
        }

        this.frustum.set(matrices.projection().mul(matrices.modelView(), this.viewProjection));
        matrices.modelView().originAffine(this.eye);
        double eyeX = cameraX + this.eye.x;
        double eyeY = cameraY + this.eye.y;
        double eyeZ = cameraZ + this.eye.z;

        long now = System.nanoTime() / 1_000_000L;
        this.frame++;
        if (this.queryMode == 0) {
            this.queryMode = GL.getCapabilities().OpenGL33 ? GL33C.GL_ANY_SAMPLES_PASSED : GL15C.GL_SAMPLES_PASSED;
        }

        boolean queryPass = false;
        try {
            for (Entity entity : entities) {
                Query query = this.queries.get(entity);
                if (query != null) {
                    query.lastSeenFrame = this.frame;
                    query.culled = false;
                    this.poll(query);
                }

                if (!this.isCullable(entity, camera)) {
                    continue;
                }

                Box shape = entity.getShape();
                double offsetX = (entity.prevX - entity.x) * (1.0D - tickDelta);
                double offsetY = (entity.prevY - entity.y) * (1.0D - tickDelta);
                double offsetZ = (entity.prevZ - entity.z) * (1.0D - tickDelta);
                double minX = shape.minX + offsetX;
                double minY = shape.minY + offsetY;
                double minZ = shape.minZ + offsetZ;
                double maxX = shape.maxX + offsetX;
                double maxY = shape.maxY + offsetY;
                double maxZ = shape.maxZ + offsetZ;

                if (query != null) {
                    query.culled = query.occluded
                            && query.result.covers(eyeX, eyeY, eyeZ, EYE_TOLERANCE, minX, minY, minZ, maxX, maxY, maxZ);
                    // refresh an occluded result before it stops covering the camera or the entity
                    if (query.pending || now - query.issuedAt < Argentum.CONFIG.entityOcclusionIntervalMs && (!query.occluded
                            || query.result.covers(eyeX, eyeY, eyeZ, EYE_TOLERANCE / 2.0D, minX, minY, minZ, maxX, maxY, maxZ))) {
                        continue;
                    }
                }

                double motion = MOTION_MARGIN + Math.max(Math.abs(entity.x - entity.prevX),
                        Math.max(Math.abs(entity.y - entity.prevY), Math.abs(entity.z - entity.prevZ)));
                minX -= motion;
                minY -= motion;
                minZ -= motion;
                maxX += motion;
                maxY += motion;
                maxZ += motion;

                // a box partly off screen draws no samples there, so only a box fully on screen can be proven hidden
                if (this.frustum.intersectAab(
                        (float) (minX - RENDER_MARGIN - cameraX), (float) (minY - RENDER_MARGIN - cameraY), (float) (minZ - RENDER_MARGIN - cameraZ),
                        (float) (maxX + RENDER_MARGIN - cameraX), (float) (maxY + RENDER_MARGIN - cameraY), (float) (maxZ + RENDER_MARGIN - cameraZ))
                        != FrustumIntersection.INSIDE) {
                    continue;
                }

                if (query == null) {
                    query = new Query();
                    query.lastSeenFrame = this.frame;
                    this.queries.put(entity, query);
                }

                if (!queryPass) {
                    beginQueryPass();
                    queryPass = true;
                }
                query.issued.set(eyeX, eyeY, eyeZ, minX, minY, minZ, maxX, maxY, maxZ);
                this.issue(query, cameraX, cameraY, cameraZ);
                query.issuedAt = now;
            }
        } finally {
            if (queryPass) {
                endQueryPass();
            }
        }

        if (this.frame % 120 == 0) {
            this.removeStaleQueries();
        }
    }

    public boolean isVisible(Entity entity) {
        Query query = this.queries.get(entity);
        return query == null || !query.culled;
    }

    public void clear() {
        for (Query query : this.queries.values()) {
            if (query.id != 0) {
                GL15C.glDeleteQueries(query.id);
            }
        }
        this.queries.clear();
    }

    private boolean isCullable(Entity entity, Entity camera) {
        if (entity == camera || entity.removed || entity.ignoreCameraFrustum || entity instanceof Boss
                || entity instanceof MobEntity mob && mob.isLeashed()) {
            return false;
        }

        Box box = entity.getShape();
        if (!isFinite(box)) {
            return false;
        }

        double volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        return volume > 0.0D && volume <= MAX_BOX_VOLUME && this.renderer.isEntitySectionVisible(box);
    }

    private void poll(Query query) {
        if (query.pending && GL15C.glGetQueryObjecti(query.id, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
            query.occluded = GL15C.glGetQueryObjecti(query.id, GL15C.GL_QUERY_RESULT) == 0;
            query.pending = false;
            Snapshot result = query.result;
            query.result = query.issued;
            query.issued = result;
        }
    }

    private void issue(Query query, double cameraX, double cameraY, double cameraZ) {
        if (query.id == 0) {
            query.id = GL15C.glGenQueries();
        }

        GL15C.glBeginQuery(this.queryMode, query.id);

        Snapshot box = query.issued;
        BufferBuilder buffer = Tesselator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormat.POSITION);
        addBox(buffer,
                box.minX - RENDER_MARGIN - cameraX, box.minY - RENDER_MARGIN - cameraY, box.minZ - RENDER_MARGIN - cameraZ,
                box.maxX + RENDER_MARGIN - cameraX, box.maxY + RENDER_MARGIN - cameraY, box.maxZ + RENDER_MARGIN - cameraZ);
        Tesselator.getInstance().end();

        GL15C.glEndQuery(this.queryMode);
        query.pending = true;
    }

    private static void beginQueryPass() {
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(false, false, false, false);
    }

    private static void endQueryPass() {
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableAlphaTest();
        GlStateManager.enableCull();
    }

    private void removeStaleQueries() {
        Iterator<Query> iterator = this.queries.values().iterator();
        while (iterator.hasNext()) {
            Query query = iterator.next();
            if (this.frame - query.lastSeenFrame > 120) {
                if (query.id != 0) {
                    GL15C.glDeleteQueries(query.id);
                }
                iterator.remove();
            }
        }
    }

    private static void addBox(BufferBuilder buffer, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        vertex(buffer, maxX, maxY, maxZ);
        vertex(buffer, maxX, maxY, minZ);
        vertex(buffer, minX, maxY, maxZ);
        vertex(buffer, minX, maxY, minZ);
        vertex(buffer, minX, minY, maxZ);
        vertex(buffer, minX, minY, minZ);
        vertex(buffer, minX, maxY, minZ);
        vertex(buffer, minX, minY, minZ);
        vertex(buffer, maxX, maxY, minZ);
        vertex(buffer, maxX, minY, minZ);
        vertex(buffer, maxX, maxY, maxZ);
        vertex(buffer, maxX, minY, maxZ);
        vertex(buffer, minX, maxY, maxZ);
        vertex(buffer, minX, minY, maxZ);
        vertex(buffer, minX, minY, maxZ);
        vertex(buffer, maxX, minY, maxZ);
        vertex(buffer, minX, minY, minZ);
        vertex(buffer, maxX, minY, minZ);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z) {
        buffer.vertex(x, y, z).nextVertex();
    }

    private static boolean isFinite(Box box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    private static class Query {
        private int id;
        private boolean pending;
        private boolean occluded;
        private boolean culled;
        private long issuedAt;
        private long lastSeenFrame;
        private Snapshot issued = new Snapshot();
        private Snapshot result = new Snapshot();
    }

    private static class Snapshot {
        private double eyeX, eyeY, eyeZ;
        private double minX, minY, minZ, maxX, maxY, maxZ;

        private void set(double eyeX, double eyeY, double eyeZ,
                double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.eyeX = eyeX;
            this.eyeY = eyeY;
            this.eyeZ = eyeZ;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private boolean covers(double eyeX, double eyeY, double eyeZ, double tolerance,
                double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            double dx = eyeX - this.eyeX;
            double dy = eyeY - this.eyeY;
            double dz = eyeZ - this.eyeZ;
            return dx * dx + dy * dy + dz * dz <= tolerance * tolerance
                    && minX >= this.minX && minY >= this.minY && minZ >= this.minZ
                    && maxX <= this.maxX && maxY <= this.maxY && maxZ <= this.maxZ;
        }
    }
}
