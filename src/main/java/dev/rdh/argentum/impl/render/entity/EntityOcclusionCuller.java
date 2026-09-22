package dev.rdh.argentum.impl.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
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
    private static final double BOX_MARGIN = 0.2D;
    private static final double MAX_BOX_VOLUME = 64.0D * 64.0D * 64.0D;

    private final ArgentumWorldRenderer renderer;
    private final Map<Entity, Query> queries = new Reference2ReferenceOpenHashMap<>();
    private long frame;
    private int queryMode = 0;

    public EntityOcclusionCuller(ArgentumWorldRenderer renderer) {
        this.renderer = renderer;
    }

    public void prepare(List<Entity> entities, Entity camera, double cameraX, double cameraY, double cameraZ) {
        if (!Argentum.CONFIG.entityCulling || !GL.getCapabilities().OpenGL15) {
            this.clear();
            return;
        }

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
                    this.poll(query);

                    if (query.pending || now - query.lastQueryTime < Argentum.CONFIG.entityOcclusionIntervalMs) {
                        continue;
                    }
                }

                if (!this.shouldQuery(entity, camera, cameraX, cameraY, cameraZ)) {
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
                this.issue(query, entity.getShape(), cameraX, cameraY, cameraZ);
                query.lastQueryTime = now;
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
        return query == null || !query.occluded;
    }

    public void clear() {
        for (Query query : this.queries.values()) {
            if (query.id != 0) {
                GL15C.glDeleteQueries(query.id);
            }
        }
        this.queries.clear();
    }

    private boolean shouldQuery(Entity entity, Entity camera, double cameraX, double cameraY, double cameraZ) {
        if (entity == camera || entity.removed) {
            return false;
        }

        Box box = entity.getShape();
        if (!isFinite(box) || contains(box, cameraX, cameraY, cameraZ)) {
            return false;
        }

        double volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        return volume > 0.0D && volume <= MAX_BOX_VOLUME && this.renderer.isEntitySectionVisible(box);
    }

    private void poll(Query query) {
        if (query.pending && GL15C.glGetQueryObjecti(query.id, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
            query.occluded = GL15C.glGetQueryObjecti(query.id, GL15C.GL_QUERY_RESULT) == 0;
            query.pending = false;
        }
    }

    private void issue(Query query, Box box, double cameraX, double cameraY, double cameraZ) {
        if (query.id == 0) {
            query.id = GL15C.glGenQueries();
        }

        GL15C.glBeginQuery(this.queryMode, query.id);

        BufferBuilder buffer = Tesselator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormat.POSITION);
        addBox(buffer, box, cameraX, cameraY, cameraZ);
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

    private static void addBox(BufferBuilder buffer, Box box, double cameraX, double cameraY, double cameraZ) {
        double minX = box.minX - BOX_MARGIN - cameraX;
        double minY = box.minY - BOX_MARGIN - cameraY;
        double minZ = box.minZ - BOX_MARGIN - cameraZ;
        double maxX = box.maxX + BOX_MARGIN - cameraX;
        double maxY = box.maxY + BOX_MARGIN - cameraY;
        double maxZ = box.maxZ + BOX_MARGIN - cameraZ;

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

    private static boolean contains(Box box, double x, double y, double z) {
        return x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY
                && z >= box.minZ && z <= box.maxZ;
    }

    private static class Query {
        private int id;
        private boolean pending;
        private boolean occluded;
        private long lastQueryTime;
        private long lastSeenFrame;
    }
}
