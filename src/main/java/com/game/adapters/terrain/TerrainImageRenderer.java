package com.game.adapters.terrain;

import com.game.domain.SurfaceType;
import com.game.domain.world.terrain.ElevationFunction;
import com.game.domain.world.terrain.TerrainChunkData;
import com.game.domain.world.terrain.TerrainConfig;
import com.game.ports.TerrainImageProvider;
import com.game.ports.TerrainRenderConfig;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders terrain chunk data into layered {@link BufferedImage} arrays
 * for the renderer to blit. Implements the renderer-facing
 * {@link TerrainImageProvider} port.
 * <p>
 * <strong>Optimisation strategy:</strong>
 * <ul>
 * <li>Elevation grid is pre-computed once per chunk (eliminates 2/3 of
 * elevation function calls vs the naive per-pixel approach)</li>
 * <li>Pixels are written via direct {@code int[]} buffer access instead
 * of {@link BufferedImage#setRGB} (avoids color-model overhead)</li>
 * <li>200 elevation levels are mapped into 24 parallax bands — the
 * renderer only iterates 24 layers while colour precision stays high</li>
 * <li>Images rendered at half resolution (256×256) and upscaled by the
 * renderer, cutting pixel count to 1/4</li>
 * </ul>
 */
public final class TerrainImageRenderer implements TerrainImageProvider {

    private final TerrainChunkGenerator chunkGenerator;
    private final ElevationFunction elevationFunction;
    private final TerrainConfig config;
    private final TerrainRenderConfig renderConfig;
    private final Map<String, BufferedImage[]> imageCache = new ConcurrentHashMap<>();

    public TerrainImageRenderer(TerrainChunkGenerator chunkGenerator,
            ElevationFunction elevationFunction,
            TerrainConfig config) {
        this.chunkGenerator = chunkGenerator;
        this.elevationFunction = elevationFunction;
        this.config = config;
        this.renderConfig = new TerrainRenderConfig(
                config.chunkSizeMetres(),
                config.chunkImageResolution(),
                config.parallaxBands(),
                config.parallaxScaleFactor());
    }

    // ================================================================
    // TerrainImageProvider port (renderer-facing)
    // ================================================================

    @Override
    public BufferedImage[] getChunkImages(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return imageCache.computeIfAbsent(key, k -> renderChunk(chunkX, chunkY));
    }

    @Override
    public void evictDistantChunks(double camX, double camY) {
        int camChunkX = (int) Math.floor(camX / config.chunkSizeMetres());
        int camChunkY = (int) Math.floor(camY / config.chunkSizeMetres());
        int radius = config.evictionRadius();

        imageCache.keySet().removeIf(key -> {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);
            return Math.abs(cx - camChunkX) > radius || Math.abs(cy - camChunkY) > radius;
        });

        chunkGenerator.evictDistantChunks(camX, camY);
    }

    @Override
    public TerrainRenderConfig getRenderConfig() {
        return renderConfig;
    }

    // ================================================================
    // Image rendering (optimised)
    // ================================================================

    private BufferedImage[] renderChunk(int chunkX, int chunkY) {
        TerrainChunkData chunkData = chunkGenerator.getOrCreateChunk(chunkX, chunkY);

        int res = config.chunkImageResolution();
        int cellPx = config.cellSizePixels();
        int cellsPerChunk = config.cellsPerChunk();
        int bands = config.parallaxBands();
        int elevLevels = config.elevationLevels();
        double ppm = config.pixelsPerMetre();

        double chunkWorldX = chunkX * config.chunkSizeMetres();
        double chunkWorldY = chunkY * config.chunkSizeMetres();

        // --- Step 1: Pre-compute full elevation grid (eliminates redundant calls) ---
        double[] elevGrid = new double[res * res];
        for (int py = 0; py < res; py++) {
            double worldY = chunkWorldY + py / ppm;
            for (int px = 0; px < res; px++) {
                double worldX = chunkWorldX + px / ppm;
                elevGrid[py * res + px] = elevationFunction.getElevation(worldX, worldY);
            }
        }

        // --- Step 2: Allocate band images lazily, with direct int[] pixel buffers ---
        BufferedImage[] bandImages = new BufferedImage[bands];
        int[][] bandPixels = new int[bands][]; // direct pixel buffers

        // --- Step 3: Render all pixels ---
        for (int py = 0; py < res; py++) {
            int cellY = Math.min(py / cellPx, cellsPerChunk - 1);

            for (int px = 0; px < res; px++) {
                int cellX = Math.min(px / cellPx, cellsPerChunk - 1);
                int idx = py * res + px;

                SurfaceType surface = chunkData.cells()[cellY * cellsPerChunk + cellX];
                int baseColor = SurfaceColorPalette.colorFor(surface);
                double elevation = elevGrid[idx];

                // Map elevation → band index
                int bandIndex = (int) ((elevation + 100) / 200.0 * bands);
                bandIndex = clamp(bandIndex, 0, bands - 1);

                // Lazy band image allocation
                if (bandImages[bandIndex] == null) {
                    bandImages[bandIndex] = new BufferedImage(
                            res, res, BufferedImage.TYPE_INT_ARGB);
                    bandPixels[bandIndex] = ((DataBufferInt) bandImages[bandIndex].getRaster().getDataBuffer())
                            .getData();
                }

                // --- Shading with 200-level elevation precision ---
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;

                // Height-based brightness (200-level precision, not band-level)
                double heightFactor = 0.6 + 0.4 * (elevation + 100) / 200.0;
                r = clampByte((int) (r * heightFactor));
                g = clampByte((int) (g * heightFactor));
                b = clampByte((int) (b * heightFactor));

                // Directional shading via pre-computed grid (zero extra elevation calls)
                double dzdx = 0, dzdy = 0;
                if (px < res - 1)
                    dzdx = elevGrid[idx + 1] - elevation;
                if (py < res - 1)
                    dzdy = elevGrid[idx + res] - elevation;

                double lightFactor = 1.0 - 0.3 * (dzdx + dzdy);
                r = clampByte((int) (r * lightFactor));
                g = clampByte((int) (g * lightFactor));
                b = clampByte((int) (b * lightFactor));

                // Direct int[] write — bypasses setRGB color-model overhead
                bandPixels[bandIndex][idx] = (255 << 24) | (r << 16) | (g << 8) | b;
            }
        }

        return bandImages;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
