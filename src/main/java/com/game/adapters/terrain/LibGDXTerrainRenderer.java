package com.game.adapters.terrain;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.game.domain.SurfaceType;
import com.game.domain.world.terrain.ElevationFunction;
import com.game.domain.world.terrain.TerrainChunkData;
import com.game.domain.world.terrain.TerrainConfig;
import com.game.ports.TerrainRenderConfig;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardware-accelerated Terrain Renderer.
 * <p>
 * Generates a single LibGDX {@link Texture} per chunk.
 * RGB channels store the surface color and directional shading.
 * Alpha channel stores the elevation height map (0-255).
 * <p>
 * This single texture is designed to be passed to a GLSL shader
 * which will use the Alpha channel to perform the 24-layer parallax
 * effect directly on the GPU.
 */
public class LibGDXTerrainRenderer {

    private final TerrainChunkGenerator chunkGenerator;
    private final ElevationFunction elevationFunction;
    private final TerrainConfig config;
    private final TerrainRenderConfig renderConfig;

    // Cache of OpenGL Textures instead of BufferedImages
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();

    public LibGDXTerrainRenderer(TerrainChunkGenerator chunkGenerator,
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

    /**
     * Gets or generates the chunk texture. Must be called on the GL thread.
     */
    public Texture getChunkTexture(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return textureCache.computeIfAbsent(key, k -> renderChunk(chunkX, chunkY));
    }

    public TerrainRenderConfig getRenderConfig() {
        return renderConfig;
    }

    /**
     * Evicts distant chunks. Crucially, this manually disposes of the
     * OpenGL textures to prevent massive memory leaks.
     */
    public void evictDistantChunks(double camX, double camY) {
        int camChunkX = (int) Math.floor(camX / config.chunkSizeMetres());
        int camChunkY = (int) Math.floor(camY / config.chunkSizeMetres());
        int radius = config.evictionRadius();

        Iterator<Map.Entry<String, Texture>> iterator = textureCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Texture> entry = iterator.next();
            String[] parts = entry.getKey().split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);

            if (Math.abs(cx - camChunkX) > radius || Math.abs(cy - camChunkY) > radius) {
                // MUST dispose GPU memory
                entry.getValue().dispose();
                iterator.remove();
            }
        }

        chunkGenerator.evictDistantChunks(camX, camY);
    }

    // ================================================================
    // Texture Generation
    // ================================================================

    private Texture renderChunk(int chunkX, int chunkY) {
        TerrainChunkData chunkData = chunkGenerator.getOrCreateChunk(chunkX, chunkY);

        int res = config.chunkImageResolution();
        int cellPx = config.cellSizePixels();
        int cellsPerChunk = config.cellsPerChunk();
        double ppm = config.pixelsPerMetre();

        double chunkWorldX = chunkX * config.chunkSizeMetres();
        double chunkWorldY = chunkY * config.chunkSizeMetres();

        // 1. Pre-compute full elevation grid
        double[] elevGrid = new double[res * res];
        for (int py = 0; py < res; py++) {
            double worldY = chunkWorldY + py / ppm;
            for (int px = 0; px < res; px++) {
                double worldX = chunkWorldX + px / ppm;
                elevGrid[py * res + px] = elevationFunction.getElevation(worldX, worldY);
            }
        }

        // 2. Create LibGDX Pixmap (RGBA8888)
        Pixmap pixmap = new Pixmap(res, res, Pixmap.Format.RGBA8888);

        // 3. Render pixels
        for (int py = 0; py < res; py++) {
            int cellY = Math.min(py / cellPx, cellsPerChunk - 1);

            for (int px = 0; px < res; px++) {
                int cellX = Math.min(px / cellPx, cellsPerChunk - 1);
                int idx = py * res + px;

                SurfaceType surface = chunkData.cells()[cellY * cellsPerChunk + cellX];
                int baseColor = SurfaceColorPalette.colorFor(surface);
                double elevation = elevGrid[idx];

                // Extract RGB from palette
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;

                // Directional shading (baked into texture to save GPU fragment cycles)
                double dzdx = 0, dzdy = 0;
                if (px < res - 1) dzdx = elevGrid[idx + 1] - elevation;
                if (py < res - 1) dzdy = elevGrid[idx + res] - elevation;

                double lightFactor = 1.0 - 0.3 * (dzdx + dzdy);
                r = clampByte((int) (r * lightFactor));
                g = clampByte((int) (g * lightFactor));
                b = clampByte((int) (b * lightFactor));

                // Map elevation (-100 to +100) to Alpha channel (0 to 255)
                // The shader will read this alpha to know the pixel's height
                int a = clampByte((int) ((elevation + 100) / 200.0 * 255.0));

                // Pack RGBA and draw to pixmap
                int rgba = (r << 24) | (g << 16) | (b << 8) | a;
                pixmap.drawPixel(px, py, rgba);
            }
        }

        // 4. Upload Pixmap to GPU Texture and free CPU memory
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();

        return texture;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}