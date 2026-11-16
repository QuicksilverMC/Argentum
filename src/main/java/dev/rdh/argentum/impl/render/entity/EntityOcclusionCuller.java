package dev.rdh.argentum.impl.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.boss.Boss;
import net.minecraft.util.math.Box;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EntityOcclusionCuller {
    private static final double RENDER_MARGIN = 0.5D;
    private static final double MOTION_MARGIN = 0.1D;
    private static final double EYE_TOLERANCE = 0.1D;
    private static final double GROUP_SIZE = 2.0D;
    private static final double MAX_BOX_VOLUME = 64.0D * 64.0D * 64.0D;

    private final ArgentumWorldRenderer renderer;
    private final Long2ObjectOpenHashMap<Query> queries = new Long2ObjectOpenHashMap<>();
    private final List<Query> active = new ArrayList<>();
    private final ReferenceOpenHashSet<Entity> culled = new ReferenceOpenHashSet<>();
    private final Matrix4f viewProjection = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f eye = new Vector3f();
    private long frame;
    private int queryMode = 0;
    private int previousProgram;

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

        List<Query> active = this.active;
        active.clear();
        for (Entity entity : entities) {
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

            long key = cell((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D);
            Query query = this.queries.get(key);
            if (query == null) {
                query = new Query();
                this.queries.put(key, query);
            }
            if (query.lastSeenFrame != this.frame) {
                query.lastSeenFrame = this.frame;
                query.members.clear();
                query.motion = 0.0D;
                query.box.set(0.0D, 0.0D, 0.0D, minX, minY, minZ, maxX, maxY, maxZ);
                active.add(query);
            } else {
                query.box.include(minX, minY, minZ, maxX, maxY, maxZ);
            }
            query.members.add(entity);
            query.motion = Math.max(query.motion, Math.max(Math.abs(entity.x - entity.prevX),
                    Math.max(Math.abs(entity.y - entity.prevY), Math.abs(entity.z - entity.prevZ))));
        }

        this.culled.clear();
        boolean queryPass = false;
        try {
            for (Query query : active) {
                this.poll(query);
                Snapshot box = query.box;
                Snapshot result = query.result;
                boolean culled = query.occluded && result.covers(eyeX, eyeY, eyeZ, EYE_TOLERANCE,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                if (culled) {
                    this.culled.addAll(query.members);
                }

                // refresh an occluded result before it stops covering the camera or the entity
                if (query.pending || now - query.issuedAt < Argentum.CONFIG.entityOcclusionIntervalMs && (!query.occluded
                        || result.covers(eyeX, eyeY, eyeZ, EYE_TOLERANCE / 2.0D,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ))) {
                    continue;
                }

                double motion = MOTION_MARGIN + query.motion;
                double minX = box.minX - motion, minY = box.minY - motion, minZ = box.minZ - motion;
                double maxX = box.maxX + motion, maxY = box.maxY + motion, maxZ = box.maxZ + motion;
                if ((maxX - minX) * (maxY - minY) * (maxZ - minZ) > MAX_BOX_VOLUME) {
                    continue;
                }

                // a box partly off screen draws no samples there, so only a box fully on screen can be proven hidden
                if (this.frustum.intersectAab(
                        (float) (minX - RENDER_MARGIN - cameraX), (float) (minY - RENDER_MARGIN - cameraY), (float) (minZ - RENDER_MARGIN - cameraZ),
                        (float) (maxX + RENDER_MARGIN - cameraX), (float) (maxY + RENDER_MARGIN - cameraY), (float) (maxZ + RENDER_MARGIN - cameraZ))
                        != FrustumIntersection.INSIDE) {
                    continue;
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

    private static long cell(double x, double y, double z) {
        long cellX = (long) Math.floor(x / GROUP_SIZE) & 0x1FFFFFL;
        long cellY = (long) Math.floor(y / GROUP_SIZE) & 0x1FFFFFL;
        long cellZ = (long) Math.floor(z / GROUP_SIZE) & 0x1FFFFFL;
        return cellX << 42 | cellY << 21 | cellZ;
    }

    public boolean isVisible(Entity entity) {
        return !this.culled.contains(entity);
    }

    public void clear() {
        for (Query query : this.queries.values()) {
            if (query.id != 0) {
                GL15C.glDeleteQueries(query.id);
            }
        }
        this.queries.clear();
        this.active.clear();
        this.culled.clear();
    }

    private boolean isCullable(Entity entity, Entity camera) {
        if (entity == camera || entity.removed || entity.ignoreCameraFrustum || entity instanceof Boss
                || entity instanceof MobEntity mob && mob.isLeashed() || BakedItemFrames.fullyBaked(entity)
                || entity instanceof GuardianEntity guardian && guardian.hasBeamTarget()) {
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

    private void beginQueryPass() {
        this.previousProgram = GL11.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(0);
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(false, false, false, false);
    }

    private void endQueryPass() {
        GL20C.glUseProgram(this.previousProgram);
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
        private final List<Entity> members = new ArrayList<>();
        private final Snapshot box = new Snapshot();
        private int id;
        private boolean pending;
        private boolean occluded;
        private long issuedAt;
        private long lastSeenFrame = -1L;
        private double motion;
        private Snapshot issued = new Snapshot();
        private Snapshot result = new Snapshot();
    }

    private static class Snapshot {
        private double eyeX, eyeY, eyeZ;
        private double minX, minY, minZ, maxX, maxY, maxZ;

        private void include(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = Math.min(this.minX, minX);
            this.minY = Math.min(this.minY, minY);
            this.minZ = Math.min(this.minZ, minZ);
            this.maxX = Math.max(this.maxX, maxX);
            this.maxY = Math.max(this.maxY, maxY);
            this.maxZ = Math.max(this.maxZ, maxZ);
        }

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
