package com.game.adapters;

import com.game.domain.SurfaceType;
import com.game.ports.TerrainProvider;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Procedural terrain generator that produces infinite noise-based terrain.
 * <p>
 * The world is divided into chunks of {@value #CHUNK_SIZE_METRES}m x
 * {@value #CHUNK_SIZE_METRES}m. Each chunk contains a grid of
 * {@value #CELLS_PER_CHUNK} x {@value #CELLS_PER_CHUNK} cells, where each
 * cell has a fixed {@link SurfaceType} determined by layered value noise.
 * <p>
 * Chunks are lazily generated on first access and cached. Chunks beyond
 * a configurable radius from the camera are evicted to bound memory use.
 * <p>
 * Each chunk also has a pre-rendered {@link BufferedImage} for fast blitting
 * during rendering (generated once, then just drawn each frame).
 *
 * <h3>Performance design</h3>
 * <ul>
 *   <li>Custom value noise with integer permutation table — no Math.random()</li>
 *   <li>Chunk key packed into a single long for O(1) map lookups</li>
 *   <li>Pre-rendered chunk images: one-time cost per chunk, then single blit</li>
 *   <li>Eviction runs every N frames, not every frame</li>
 * </ul>
 */
public class ProceduralTerrainGenerator implements TerrainProvider {

    // ================================================================
    // Chunk / cell geometry
    // ================================================================

    /** Size of one chunk in world metres. */
    static final double CHUNK_SIZE_METRES = 64.0;

    /** Number of terrain cells per chunk side. */
    private static final int CELLS_PER_CHUNK = 16;

    /** Size of one cell in world metres. */
    private static final double CELL_SIZE_METRES = CHUNK_SIZE_METRES / CELLS_PER_CHUNK;

    /** Chunk image size in pixels (at 8 PPM: 64m * 8 = 512px). */
    static final int CHUNK_SIZE_PIXELS = 512;

    /** Pixels per cell in the chunk image. */
    private static final int CELL_SIZE_PIXELS = CHUNK_SIZE_PIXELS / CELLS_PER_CHUNK;

    // ================================================================
    // Cache / eviction
    // ================================================================

    /** Maximum distance (in chunks) from camera before eviction. */
    private static final int EVICTION_RADIUS = 6;

    /** Eviction check runs every N queries (amortised). */
    private static final int EVICTION_INTERVAL = 120;

    // ================================================================
    // Noise parameters
    // ================================================================

    /** Permutation table size (must be power of 2). */
    private static final int PERM_SIZE = 256;
    private static final int PERM_MASK = PERM_SIZE - 1;

    /** Noise frequency for biome selection. */
    private static final double BIOME_FREQUENCY = 0.012;

    /** Secondary noise layer frequency for detail variation. */
    private static final double DETAIL_FREQUENCY = 0.035;

    /** Road corridor noise frequency (low frequency = wide corridors). */
    private static final double ROAD_FREQUENCY = 0.006;

    /** Road corridor threshold — noise values above this are tarmac. */
    private static final double ROAD_THRESHOLD = 0.62;

    // ================================================================
    // Surface colours (RGB packed as int for direct setRGB)
    // ================================================================

    private static final int COLOR_TARMAC  = packRGB(42, 42, 48);
    private static final int COLOR_DIRT    = packRGB(95, 70, 45);
    private static final int COLOR_GRAVEL  = packRGB(115, 108, 95);
    private static final int COLOR_MUD     = packRGB(65, 50, 30);
    private static final int COLOR_ICE     = packRGB(170, 200, 220);
    private static final int COLOR_SAND    = packRGB(175, 155, 110);

    // ================================================================
    // State
    // ================================================================

    private final int[] perm;
    private final Map<Long, TerrainChunk> chunkCache = new HashMap<>();
    private int queryCounter;
    private int lastEvictCenterChunkX = Integer.MIN_VALUE;
    private int lastEvictCenterChunkY = Integer.MIN_VALUE;

    public ProceduralTerrainGenerator() {
        this(42L);
    }

    public ProceduralTerrainGenerator(long seed) {
        this.perm = buildPermutationTable(seed);
    }

    // ================================================================
    // TerrainProvider implementation
    // ================================================================

    @Override
    public SurfaceType getSurfaceAt(double worldX, double worldY) {
        int chunkX = worldToChunkCoord(worldX);
        int chunkY = worldToChunkCoord(worldY);

        TerrainChunk chunk = getOrCreateChunk(chunkX, chunkY);

        // Local cell coordinates within the chunk
        double localX = worldX - chunkX * CHUNK_SIZE_METRES;
        double localY = worldY - chunkY * CHUNK_SIZE_METRES;
        int cellX = Math.min((int) (localX / CELL_SIZE_METRES), CELLS_PER_CHUNK - 1);
        int cellY = Math.min((int) (localY / CELL_SIZE_METRES), CELLS_PER_CHUNK - 1);

        // Clamp for negative coordinates
        if (cellX < 0) cellX = 0;
        if (cellY < 0) cellY = 0;

        return chunk.cells[cellY * CELLS_PER_CHUNK + cellX];
    }

    // ================================================================
    // Chunk access (public for renderer)
    // ================================================================

    /**
     * Returns the pre-rendered image for the chunk at the given chunk coordinates.
     * Generates the chunk if it doesn't exist yet.
     *
     * @param chunkX chunk X index
     * @param chunkY chunk Y index
     * @return pre-rendered 512x512 chunk image
     */
    public BufferedImage getChunkImage(int chunkX, int chunkY) {
        return getOrCreateChunk(chunkX, chunkY).image;
    }

    /**
     * Triggers cache eviction around the given world position.
     * Should be called once per frame from the render thread.
     *
     * @param centreWorldX camera centre X in metres
     * @param centreWorldY camera centre Y in metres
     */
    public void evictDistantChunks(double centreWorldX, double centreWorldY) {
        int cx = worldToChunkCoord(centreWorldX);
        int cy = worldToChunkCoord(centreWorldY);

        // Only evict if camera has moved to a different chunk
        if (cx == lastEvictCenterChunkX && cy == lastEvictCenterChunkY) {
            return;
        }
        lastEvictCenterChunkX = cx;
        lastEvictCenterChunkY = cy;

        Iterator<Map.Entry<Long, TerrainChunk>> it = chunkCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, TerrainChunk> entry = it.next();
            long key = entry.getKey();
            int chunkX = unpackX(key);
            int chunkY = unpackY(key);
            int dx = Math.abs(chunkX - cx);
            int dy = Math.abs(chunkY - cy);
            if (dx > EVICTION_RADIUS || dy > EVICTION_RADIUS) {
                it.remove();
            }
        }
    }

    // ================================================================
    // Chunk generation
    // ================================================================

    private TerrainChunk getOrCreateChunk(int chunkX, int chunkY) {
        long key = packKey(chunkX, chunkY);
        TerrainChunk chunk = chunkCache.get(key);
        if (chunk != null) {
            return chunk;
        }

        chunk = generateChunk(chunkX, chunkY);
        chunkCache.put(key, chunk);

        // Periodic eviction (amortised)
        if (++queryCounter % EVICTION_INTERVAL == 0 && chunkCache.size() > 100) {
            // Emergency eviction if cache grows too large
            evictDistantChunks(chunkX * CHUNK_SIZE_METRES, chunkY * CHUNK_SIZE_METRES);
        }

        return chunk;
    }

    private TerrainChunk generateChunk(int chunkX, int chunkY) {
        SurfaceType[] cells = new SurfaceType[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
        BufferedImage image = new BufferedImage(
                CHUNK_SIZE_PIXELS, CHUNK_SIZE_PIXELS, BufferedImage.TYPE_INT_RGB);

        double chunkWorldX = chunkX * CHUNK_SIZE_METRES;
        double chunkWorldY = chunkY * CHUNK_SIZE_METRES;

        // Determine surface type for each cell
        for (int cy = 0; cy < CELLS_PER_CHUNK; cy++) {
            for (int cx = 0; cx < CELLS_PER_CHUNK; cx++) {
                double cellWorldX = chunkWorldX + (cx + 0.5) * CELL_SIZE_METRES;
                double cellWorldY = chunkWorldY + (cy + 0.5) * CELL_SIZE_METRES;
                cells[cy * CELLS_PER_CHUNK + cx] = classifySurface(cellWorldX, cellWorldY);
            }
        }

        // Render the chunk image with per-pixel noise detail
        renderChunkImage(image, cells, chunkWorldX, chunkWorldY);

        return new TerrainChunk(cells, image);
    }

    /**
     * Classifies the terrain surface at a world position using layered noise.
     * <p>
     * Layer 1: Road corridors (low frequency) — if noise > threshold, it's tarmac.
     * Layer 2: Biome selection (medium frequency) — determines base terrain.
     * Layer 3: Detail variation (higher frequency) — adds local variation.
     */
    private SurfaceType classifySurface(double worldX, double worldY) {
        // Layer 1: Road network
        double roadNoise = valueNoise2D(worldX * ROAD_FREQUENCY, worldY * ROAD_FREQUENCY);
        if (roadNoise > ROAD_THRESHOLD) {
            return SurfaceType.TARMAC;
        }

        // Layer 2: Base biome
        double biomeNoise = valueNoise2D(worldX * BIOME_FREQUENCY, worldY * BIOME_FREQUENCY);

        // Layer 3: Detail perturbation
        double detail = valueNoise2D(
                worldX * DETAIL_FREQUENCY + 100.0,
                worldY * DETAIL_FREQUENCY + 100.0) * 0.15;
        double combined = biomeNoise + detail;

        // Map combined noise to surface type
        if (combined < 0.18) return SurfaceType.ICE;
        if (combined < 0.32) return SurfaceType.MUD;
        if (combined < 0.48) return SurfaceType.DIRT;
        if (combined < 0.62) return SurfaceType.GRAVEL;
        if (combined < 0.78) return SurfaceType.SAND;
        return SurfaceType.DIRT;
    }

    // ================================================================
    // Chunk image rendering
    // ================================================================

    /**
     * Renders a chunk's BufferedImage with per-pixel noise for visual detail.
     * Each cell is filled with its surface colour, then per-pixel noise adds
     * texture variation (±6 RGB per pixel).
     */
    private void renderChunkImage(BufferedImage image, SurfaceType[] cells,
                                   double chunkWorldX, double chunkWorldY) {
        for (int py = 0; py < CHUNK_SIZE_PIXELS; py++) {
            int cellY = py / CELL_SIZE_PIXELS;
            if (cellY >= CELLS_PER_CHUNK) cellY = CELLS_PER_CHUNK - 1;

            for (int px = 0; px < CHUNK_SIZE_PIXELS; px++) {
                int cellX = px / CELL_SIZE_PIXELS;
                if (cellX >= CELLS_PER_CHUNK) cellX = CELLS_PER_CHUNK - 1;

                SurfaceType surface = cells[cellY * CELLS_PER_CHUNK + cellX];
                int baseColor = surfaceColor(surface);

                // Per-pixel noise for texture (fast hash-based, no floating point noise)
                int noiseVal = pixelNoise(px + (int)(chunkWorldX * 8),
                                          py + (int)(chunkWorldY * 8));

                int r = clampByte(((baseColor >> 16) & 0xFF) + noiseVal);
                int g = clampByte(((baseColor >> 8) & 0xFF) + noiseVal);
                int b = clampByte((baseColor & 0xFF) + noiseVal);

                image.setRGB(px, py, (r << 16) | (g << 8) | b);
            }
        }
    }

    /**
     * Fast integer-only pixel noise in range [-6, +5].
     * Uses the permutation table for pseudo-random but deterministic variation.
     */
    private int pixelNoise(int px, int py) {
        int h = perm[(perm[px & PERM_MASK] + py) & PERM_MASK];
        return (h % 12) - 6;
    }

    private static int surfaceColor(SurfaceType surface) {
        return switch (surface) {
            case TARMAC -> COLOR_TARMAC;
            case DIRT   -> COLOR_DIRT;
            case GRAVEL -> COLOR_GRAVEL;
            case MUD    -> COLOR_MUD;
            case ICE    -> COLOR_ICE;
            case SAND   -> COLOR_SAND;
        };
    }

    // ================================================================
    // Value noise (custom, no external libs)
    // ================================================================

    /**
     * 2D value noise with bilinear interpolation and smoothstep.
     * Returns a value in [0.0, 1.0].
     */
    private double valueNoise2D(double x, double y) {
        int ix = fastFloor(x);
        int iy = fastFloor(y);
        double fx = x - ix;
        double fy = y - iy;

        // Smoothstep for smoother interpolation
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);

        // Corner values from permutation table
        double v00 = latticeValue(ix, iy);
        double v10 = latticeValue(ix + 1, iy);
        double v01 = latticeValue(ix, iy + 1);
        double v11 = latticeValue(ix + 1, iy + 1);

        // Bilinear interpolation
        double top = v00 + sx * (v10 - v00);
        double bottom = v01 + sx * (v11 - v01);
        return top + sy * (bottom - top);
    }

    /**
     * Returns a pseudo-random value in [0.0, 1.0) for lattice point (ix, iy).
     */
    private double latticeValue(int ix, int iy) {
        int h = perm[(perm[ix & PERM_MASK] + iy) & PERM_MASK];
        return h / (double) PERM_SIZE;
    }

    // ================================================================
    // Utilities
    // ================================================================

    private static int worldToChunkCoord(double world) {
        return (int) Math.floor(world / CHUNK_SIZE_METRES);
    }

    private static long packKey(int chunkX, int chunkY) {
        return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int packRGB(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Builds a permutation table from a seed using Fisher-Yates shuffle.
     */
    private static int[] buildPermutationTable(long seed) {
        int[] table = new int[PERM_SIZE];
        for (int i = 0; i < PERM_SIZE; i++) {
            table[i] = i;
        }

        // Simple LCG-based shuffle (deterministic from seed)
        long state = seed;
        for (int i = PERM_SIZE - 1; i > 0; i--) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((state >>> 33) % (i + 1));
            if (j < 0) j += (i + 1);
            int tmp = table[i];
            table[i] = table[j];
            table[j] = tmp;
        }
        return table;
    }

    // ================================================================
    // Internal chunk data
    // ================================================================

    /**
     * A terrain chunk: grid of surface types + pre-rendered image.
     */
    static class TerrainChunk {
        final SurfaceType[] cells;
        final BufferedImage image;

        TerrainChunk(SurfaceType[] cells, BufferedImage image) {
            this.cells = cells;
            this.image = image;
        }
    }
}
