package com.game.adapters;

import com.game.domain.SurfaceType;
import com.game.ports.TerrainProvider;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ProceduralTerrainGenerator  implements TerrainProvider {

    // ================================================================
    // Map & Chunk Constants
    // ================================================================
    public static final double CHUNK_SIZE_METRES = 64.0;
    public static final int CELLS_PER_CHUNK = 32;
    public static final double CELL_SIZE_METRES = CHUNK_SIZE_METRES / CELLS_PER_CHUNK;

    public static final int CHUNK_SIZE_PIXELS = 512;
    public static final int CELL_SIZE_PIXELS = CHUNK_SIZE_PIXELS / CELLS_PER_CHUNK;

    // ================================================================
    // Noise & Biome parameters
    // ================================================================
    private static final double BIOME_FREQUENCY = 0.005;
    private static final double ROAD_FREQUENCY = 0.01;
    private static final double ROAD_THRESHOLD = 0.8;

    // ================================================================
    // Elevation (Topography) parameters
    // ================================================================
    /** The absolute highest a mountain can go. Flat chunks will ignore most of these. */
    public static final int MAX_POSSIBLE_LAYERS = 20;
    /** Noise frequency for hills/mountains (lower = wider mountains). */
    private static final double ELEVATION_FREQUENCY = 0.008;

    // ================================================================
    // State
    // ================================================================
    private final int[] perm;
    private final Map<String, TerrainChunk> chunkCache = new ConcurrentHashMap<>();

    public ProceduralTerrainGenerator() {
        this(42L); // Default seed
    }

    public ProceduralTerrainGenerator(long seed) {
        this.perm = buildPermutationTable(seed);
    }

    /**
     * Builds a 512-element permutation table for the value noise algorithm.
     */
    private int[] buildPermutationTable(long seed) {
        int[] p = new int[512];
        Random rnd = new Random(seed);
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int swap = rnd.nextInt(256);
            int temp = p[i];
            p[i] = p[swap];
            p[swap] = temp;
        }
        for (int i = 0; i < 256; i++) {
            p[i + 256] = p[i];
        }
        return p;
    }

    // ================================================================
    // Public API
    // ================================================================

    /**
     * Returns the array of topographic image layers for the renderer.
     */
    public BufferedImage[] getChunkImages(int chunkX, int chunkY) {
        return getOrCreateChunk(chunkX, chunkY).layerImages;
    }

    /**
     * Used by the Physics Engine to know what surface the car is driving on.
     */
    public SurfaceType getSurfaceAt(double worldX, double worldY) {
        int chunkX = (int) Math.floor(worldX / CHUNK_SIZE_METRES);
        int chunkY = (int) Math.floor(worldY / CHUNK_SIZE_METRES);
        TerrainChunk chunk = getOrCreateChunk(chunkX, chunkY);

        double localX = worldX - (chunkX * CHUNK_SIZE_METRES);
        double localY = worldY - (chunkY * CHUNK_SIZE_METRES);

        int cellX = (int) (localX / CELL_SIZE_METRES);
        int cellY = (int) (localY / CELL_SIZE_METRES);

        cellX = Math.max(0, Math.min(CELLS_PER_CHUNK - 1, cellX));
        cellY = Math.max(0, Math.min(CELLS_PER_CHUNK - 1, cellY));

        return chunk.cells[cellY * CELLS_PER_CHUNK + cellX];
    }

    public void evictDistantChunks(double camX, double camY) {
        int camChunkX = (int) Math.floor(camX / CHUNK_SIZE_METRES);
        int camChunkY = (int) Math.floor(camY / CHUNK_SIZE_METRES);
        int radius = 3;

        chunkCache.keySet().removeIf(key -> {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);
            return Math.abs(cx - camChunkX) > radius || Math.abs(cy - camChunkY) > radius;
        });
    }

    // ================================================================
    // Chunk Generation
    // ================================================================

    private TerrainChunk getOrCreateChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return chunkCache.computeIfAbsent(key, k -> generateChunk(chunkX, chunkY));
    }

    private TerrainChunk generateChunk(int chunkX, int chunkY) {
        SurfaceType[] cells = new SurfaceType[CELLS_PER_CHUNK * CELLS_PER_CHUNK];

        // LAZY ALLOCATION: Create an array of null pointers (consumes almost zero RAM)
        BufferedImage[] layerImages = new BufferedImage[MAX_POSSIBLE_LAYERS];

        double chunkWorldX = chunkX * CHUNK_SIZE_METRES;
        double chunkWorldY = chunkY * CHUNK_SIZE_METRES;

        // 1. Calculate physics cells
        for (int cy = 0; cy < CELLS_PER_CHUNK; cy++) {
            for (int cx = 0; cx < CELLS_PER_CHUNK; cx++) {
                double cellWorldX = chunkWorldX + (cx + 0.5) * CELL_SIZE_METRES;
                double cellWorldY = chunkWorldY + (cy + 0.5) * CELL_SIZE_METRES;
                cells[cy * CELLS_PER_CHUNK + cx] = classifySurface(cellWorldX, cellWorldY);
            }
        }

        // 2. Render pixels to the correct height layer
        renderChunkImages(layerImages, cells, chunkWorldX, chunkWorldY);

        return new TerrainChunk(cells, layerImages);
    }

    private void renderChunkImages(BufferedImage[] layerImages, SurfaceType[] cells,
                                   double chunkWorldX, double chunkWorldY) {
        for (int py = 0; py < CHUNK_SIZE_PIXELS; py++) {
            int cellY = py / CELL_SIZE_PIXELS;
            if (cellY >= CELLS_PER_CHUNK) cellY = CELLS_PER_CHUNK - 1;

            for (int px = 0; px < CHUNK_SIZE_PIXELS; px++) {
                int cellX = px / CELL_SIZE_PIXELS;
                if (cellX >= CELLS_PER_CHUNK) cellX = CELLS_PER_CHUNK - 1;

                SurfaceType surface = cells[cellY * CELLS_PER_CHUNK + cellX];
                int baseColor = surfaceColor(surface);

                // Find the height of this specific pixel
                double pixelWorldX = chunkWorldX + (px / 8.0); // 8 Pixels Per Meter
                double pixelWorldY = chunkWorldY + (py / 8.0);
                int elevationLayer = getElevationLayerAt(pixelWorldX, pixelWorldY);


                int noiseVal = pixelNoise(px + (int)(chunkWorldX * 8), py + (int)(chunkWorldY * 8));
                int heightShade = (int)((elevationLayer / (double)MAX_POSSIBLE_LAYERS) * 40) - 20;
                int r = clampByte(((baseColor >> 16) & 0xFF) + noiseVal + heightShade);
                int g = clampByte(((baseColor >> 8) & 0xFF) + noiseVal + heightShade);
                int b = clampByte((baseColor & 0xFF) + noiseVal + heightShade);

                // Fully opaque color (Alpha = 255)
                int colorARGB = (255 << 24) | (r << 16) | (g << 8) | b;





                // LAZY INSTANTIATION: Create the image ONLY if a pixel reaches this height
                if (layerImages[elevationLayer] == null) {
                    layerImages[elevationLayer] = new BufferedImage(
                            CHUNK_SIZE_PIXELS, CHUNK_SIZE_PIXELS, BufferedImage.TYPE_INT_ARGB);
                }

                // Draw the pixel onto its specific layer
                layerImages[elevationLayer].setRGB(px, py, colorARGB);
            }
        }
    }

    // ================================================================
    // Noise & Biome Math
    // ================================================================

    /**
     * Returns the elevation layer (0 to MAX_POSSIBLE_LAYERS - 1) for a world coordinate.
     */
    private int getElevationLayerAt(double worldX, double worldY) {

        double noise = valueNoise2D(
                worldX * ELEVATION_FREQUENCY,
                worldY * ELEVATION_FREQUENCY
        );

        // exaggerate high terrain
        noise = noise * noise;

        int layer = (int)(noise * MAX_POSSIBLE_LAYERS);

        return Math.max(0, Math.min(MAX_POSSIBLE_LAYERS - 1, layer));
    }

    private SurfaceType classifySurface(double x, double y) {
        double roadNoise = valueNoise2D(x * ROAD_FREQUENCY, y * ROAD_FREQUENCY);
        if (roadNoise > ROAD_THRESHOLD) return SurfaceType.TARMAC;

        double biomeNoise = valueNoise2D(x * BIOME_FREQUENCY + 100, y * BIOME_FREQUENCY + 100);
        if (biomeNoise > 0.6) return SurfaceType.MUD;
        if (biomeNoise < 0.3) return SurfaceType.ICE;
        return SurfaceType.DIRT;
    }

    private int surfaceColor(SurfaceType type) {
        return switch (type) {
            case TARMAC -> 0x555555;
            case DIRT -> 0x6B5428;
            case MUD -> 0x3D2817;
            case ICE -> 0xA0D0E0;
            default -> 0x44AA44;
        };
    }

    private int pixelNoise(int x, int y) {
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ (hash >> 13)) * 1274126177;
        return (hash & 15) - 7;
    }

    private int clampByte(int val) {
        return Math.max(0, Math.min(255, val));
    }

    private double valueNoise2D(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);

        double tx = x - xi;
        double ty = y - yi;

        int rx0 = xi & 255;
        int rx1 = (rx0 + 1) & 255;
        int ry0 = yi & 255;
        int ry1 = (ry0 + 1) & 255;

        double c00 = perm[perm[rx0] + ry0] / 255.0;
        double c10 = perm[perm[rx1] + ry0] / 255.0;
        double c01 = perm[perm[rx0] + ry1] / 255.0;
        double c11 = perm[perm[rx1] + ry1] / 255.0;

        double sx = smoothstep(tx);
        double sy = smoothstep(ty);

        double nx0 = lerp(c00, c10, sx);
        double nx1 = lerp(c01, c11, sx);
        return lerp(nx0, nx1, sy);
    }

    private double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    // ================================================================
    // Data Structures
    // ================================================================

    static class TerrainChunk {
        final SurfaceType[] cells;
        final BufferedImage[] layerImages;

        TerrainChunk(SurfaceType[] cells, BufferedImage[] layerImages) {
            this.cells = cells;
            this.layerImages = layerImages;
        }
    }
}