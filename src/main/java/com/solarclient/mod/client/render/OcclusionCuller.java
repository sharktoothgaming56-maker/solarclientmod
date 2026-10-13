package com.solarclient.mod.client.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.lang.reflect.Method;

/**
 * Skips drawing entities and block entities that are completely hidden
 * behind solid blocks.
 *
 * Sodium already skips whole chunk sections the camera can't see, but a mob
 * in the next room or a chest inside a base wall is usually in a section
 * that IS visible, so it still gets drawn and then painted over by the wall.
 * This casts rays from the camera to points spread over the thing's box and
 * only skips it when every ray hits a full opaque cube on the way.
 *
 * Why this can't change what's on screen:
 *  - Only full opaque cubes block a ray (stone, dirt, planks...). Glass,
 *    leaves, slabs, fences, doors, water: all see-through to this.
 *  - The tested box is bigger than anything the renderer draws for that
 *    entity / block entity.
 *  - Anything that can show through walls is never skipped: players (name
 *    tags), always-visible custom names, glowing outlines, display entities
 *    (server holograms), beams, leashes, fishing lines, and block entities
 *    that draw outside their block (beacon beams, end gateways).
 *  - With a shader pack on, nothing is skipped, so shadows stay intact.
 *  - When unsure (out of time this frame, unloaded chunk, camera inside a
 *    block) the answer is always "draw it", which is plain vanilla.
 */
public final class OcclusionCuller {
    private OcclusionCuller() {}

    public static boolean enabled = true;

    // Wall time allowed for new ray tests per frame. Past it, things are just
    // drawn this frame, which costs exactly what vanilla costs.
    private static final long FRAME_BUDGET_NS = 600_000L;
    // Results are reused while the camera, the target and the blocks are
    // unchanged; this is only a backstop for changes nothing reported.
    private static final long MAX_AGE_NS = 1_000_000_000L;
    // Vanilla's own frustum box is the hitbox + 0.5; another 0.5 covers
    // movement inside one cache cell (half a block).
    private static final double ENTITY_GROW = 1.0;
    // Double chests, beds and banners draw up to one block past their own.
    private static final double BLOCK_ENTITY_GROW = 1.0;
    private static final double MIN_DISTANCE_SQ = 3.0 * 3.0;
    private static final double MAX_DISTANCE_SQ = 160.0 * 160.0;
    private static final double MAX_ENTITY_SIZE = 6.0;
    private static final double FAST_ENTITY_SQ = 0.5 * 0.5;
    private static final double EPS = 1.0e-4;

    private static final class Entry {
        long camKey;
        long targetKey;
        int epoch;
        long time;
        boolean visible;
    }

    private static final Int2ObjectOpenHashMap<Entry> ENTITIES = new Int2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Entry> BLOCK_ENTITIES = new Long2ObjectOpenHashMap<>();

    private static ClientWorld world;
    private static boolean active;
    private static double cx, cy, cz;
    private static long camKey;
    private static long frameTime;
    private static long spentNs;
    private static long lastPrune;
    private static int epoch;
    private static int bottomY, topY;

    private static Chunk lastChunk;
    private static int lastChunkX, lastChunkZ;

    /** Called once per frame, right after the game points the entity renderer at the camera. */
    public static void beginFrame(Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld w = client.world;
        if (w != world) {
            world = w;
            ENTITIES.clear();
            BLOCK_ENTITIES.clear();
        }
        lastChunk = null;
        spentNs = 0L;
        active = false;
        if (!enabled || w == null || camera == null || !camera.isReady() || shadersInUse()) return;

        Vec3d p = camera.getCameraPos();
        cx = p.x;
        cy = p.y;
        cz = p.z;
        bottomY = w.getBottomY();
        topY = w.getTopYInclusive();
        // From inside a block everything looks hidden; draw normally instead.
        if (opaque(MathHelper.floor(cx), MathHelper.floor(cy), MathHelper.floor(cz))) return;

        camKey = halfBlockKey(cx, cy, cz);
        frameTime = System.nanoTime();
        if (frameTime - lastPrune > 2_000_000_000L) {
            lastPrune = frameTime;
            long cutoff = frameTime - MAX_AGE_NS;
            ENTITIES.values().removeIf(e -> e.time < cutoff);
            BLOCK_ENTITIES.values().removeIf(e -> e.time < cutoff);
        }
        active = true;
    }

    /** Any block changed client-side; cached results may now be wrong. */
    public static void blockChanged() {
        epoch++;
    }

    public static boolean isEntityVisible(Entity e) {
        if (!active || neverCull(e)) return true;
        Box b = e.getBoundingBox();
        if (b.getLengthX() > MAX_ENTITY_SIZE || b.getLengthY() > MAX_ENTITY_SIZE || b.getLengthZ() > MAX_ENTITY_SIZE) return true;

        long key = halfBlockKey(e.getX(), e.getY(), e.getZ());
        Entry entry = ENTITIES.get(e.getId());
        if (entry != null && entry.camKey == camKey && entry.targetKey == key && entry.epoch == epoch
                && frameTime - entry.time < MAX_AGE_NS) {
            return entry.visible;
        }
        if (spentNs >= FRAME_BUDGET_NS) return true;

        long t0 = System.nanoTime();
        boolean visible = boxVisible(b.minX - ENTITY_GROW, b.minY - ENTITY_GROW, b.minZ - ENTITY_GROW,
                b.maxX + ENTITY_GROW, b.maxY + ENTITY_GROW, b.maxZ + ENTITY_GROW);
        spentNs += System.nanoTime() - t0;

        if (entry == null) {
            entry = new Entry();
            ENTITIES.put(e.getId(), entry);
        }
        entry.camKey = camKey;
        entry.targetKey = key;
        entry.epoch = epoch;
        entry.time = frameTime;
        entry.visible = visible;
        return visible;
    }

    public static boolean isBlockEntityVisible(BlockEntity be) {
        if (!active) return true;
        BlockPos pos = be.getPos();
        long key = pos.asLong();
        Entry entry = BLOCK_ENTITIES.get(key);
        if (entry != null && entry.camKey == camKey && entry.epoch == epoch && frameTime - entry.time < MAX_AGE_NS) {
            return entry.visible;
        }
        if (spentNs >= FRAME_BUDGET_NS) return true;

        long t0 = System.nanoTime();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean visible = boxVisible(x - BLOCK_ENTITY_GROW, y - BLOCK_ENTITY_GROW, z - BLOCK_ENTITY_GROW,
                x + 1 + BLOCK_ENTITY_GROW, y + 1 + BLOCK_ENTITY_GROW, z + 1 + BLOCK_ENTITY_GROW);
        spentNs += System.nanoTime() - t0;

        if (entry == null) {
            entry = new Entry();
            BLOCK_ENTITIES.put(key, entry);
        }
        entry.camKey = camKey;
        entry.targetKey = key;
        entry.epoch = epoch;
        entry.time = frameTime;
        entry.visible = visible;
        return visible;
    }

    private static boolean neverCull(Entity e) {
        if (e instanceof PlayerEntity || e instanceof DisplayEntity || e instanceof FishingBobberEntity
                || e instanceof LightningEntity || e instanceof GuardianEntity) return true;
        if (e instanceof EndCrystalEntity crystal && crystal.getBeamTarget() != null) return true;
        if (e instanceof Leashable leashable && leashable.isLeashed()) return true;
        if (e.hasCustomName() && e.isCustomNameVisible()) return true;
        if (e.getVelocity().lengthSquared() > FAST_ENTITY_SQ) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.hasOutline(e)) return true;
        Entity cam = client.getCameraEntity();
        return cam != null && e.getRootVehicle() == cam.getRootVehicle();
    }

    /** True if any sampled point of the box can be seen from the camera. */
    private static boolean boxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY && cz >= minZ && cz <= maxZ) return true;
        double nx = MathHelper.clamp(cx, minX, maxX) - cx;
        double ny = MathHelper.clamp(cy, minY, maxY) - cy;
        double nz = MathHelper.clamp(cz, minZ, maxZ) - cz;
        double d2 = nx * nx + ny * ny + nz * nz;
        if (d2 < MIN_DISTANCE_SQ || d2 > MAX_DISTANCE_SQ) return true;

        // Points sit a hair inside the box so they land in its cells.
        double x0 = minX + EPS, x1 = maxX - EPS;
        double y0 = minY + EPS, y1 = maxY - EPS;
        double z0 = minZ + EPS, z1 = maxZ - EPS;
        double fx = cx < minX ? x0 : cx > maxX ? x1 : Double.NaN;
        double fy = cy < minY ? y0 : cy > maxY ? y1 : Double.NaN;
        double fz = cz < minZ ? z0 : cz > maxZ ? z1 : Double.NaN;

        // Face centres first: the usual case is a visible target, and one
        // clear ray is enough.
        double mx = (x0 + x1) * 0.5, my = (y0 + y1) * 0.5, mz = (z0 + z1) * 0.5;
        if (!Double.isNaN(fx) && rayClear(fx, my, mz)) return true;
        if (!Double.isNaN(fy) && rayClear(mx, fy, mz)) return true;
        if (!Double.isNaN(fz) && rayClear(mx, my, fz)) return true;

        // Then a grid no coarser than one block over every face that faces the camera.
        int sx = Math.max(1, MathHelper.ceil(x1 - x0));
        int sy = Math.max(1, MathHelper.ceil(y1 - y0));
        int sz = Math.max(1, MathHelper.ceil(z1 - z0));
        if (!Double.isNaN(fx)) {
            for (int j = 0; j <= sy; j++) for (int k = 0; k <= sz; k++) {
                if (rayClear(fx, y0 + (y1 - y0) * j / sy, z0 + (z1 - z0) * k / sz)) return true;
            }
        }
        if (!Double.isNaN(fy)) {
            for (int i = 0; i <= sx; i++) for (int k = 0; k <= sz; k++) {
                if (rayClear(x0 + (x1 - x0) * i / sx, fy, z0 + (z1 - z0) * k / sz)) return true;
            }
        }
        if (!Double.isNaN(fz)) {
            for (int i = 0; i <= sx; i++) for (int j = 0; j <= sy; j++) {
                if (rayClear(x0 + (x1 - x0) * i / sx, y0 + (y1 - y0) * j / sy, fz)) return true;
            }
        }
        return false;
    }

    /** Walks every block cell between the camera and the point; false if one is an opaque full cube. */
    private static boolean rayClear(double px, double py, double pz) {
        double dx = px - cx, dy = py - cy, dz = pz - cz;
        int x = MathHelper.floor(cx), y = MathHelper.floor(cy), z = MathHelper.floor(cz);
        int ex = MathHelper.floor(px), ey = MathHelper.floor(py), ez = MathHelper.floor(pz);
        int stepX = Integer.signum(ex - x), stepY = Integer.signum(ey - y), stepZ = Integer.signum(ez - z);

        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tMaxX = stepX > 0 ? (x + 1 - cx) / dx : stepX < 0 ? (x - cx) / dx : Double.MAX_VALUE;
        double tMaxY = stepY > 0 ? (y + 1 - cy) / dy : stepY < 0 ? (y - cy) / dy : Double.MAX_VALUE;
        double tMaxZ = stepZ > 0 ? (z + 1 - cz) / dz : stepZ < 0 ? (z - cz) / dz : Double.MAX_VALUE;

        int steps = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z);
        for (int i = 0; i < steps; i++) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX = x == ex ? Double.MAX_VALUE : tMaxX + tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY = y == ey ? Double.MAX_VALUE : tMaxY + tDeltaY;
            } else {
                z += stepZ;
                tMaxZ = z == ez ? Double.MAX_VALUE : tMaxZ + tDeltaZ;
            }
            if (opaque(x, y, z)) return false;
        }
        return true;
    }

    private static boolean opaque(int x, int y, int z) {
        if (y < bottomY || y > topY) return false;
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (lastChunk == null || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            lastChunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
            if (lastChunk == null) return false;
        }
        ChunkSection section = lastChunk.getSection(world.getSectionIndex(y));
        if (section == null || section.isEmpty()) return false;
        return section.getBlockState(x & 15, y & 15, z & 15).isOpaqueFullCube();
    }

    private static long halfBlockKey(double x, double y, double z) {
        long qx = MathHelper.floor(x * 2.0) & 0x3FFFFFL;
        long qy = MathHelper.floor(y * 2.0) & 0xFFFFFL;
        long qz = MathHelper.floor(z * 2.0) & 0x3FFFFFL;
        return (qx << 42) | (qz << 20) | qy;
    }

    private static Object irisApi;
    private static Method irisShaderInUse;
    private static boolean irisLooked;

    private static boolean shadersInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisShaderInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisApi = null;
            }
        }
        if (irisApi == null) return false;
        try {
            return (Boolean) irisShaderInUse.invoke(irisApi);
        } catch (Throwable t) {
            return true;
        }
    }
}
