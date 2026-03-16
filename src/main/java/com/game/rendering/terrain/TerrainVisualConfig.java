package com.game.rendering.terrain;

public class TerrainVisualConfig {

    private final TerrainLightingConfig lighting;
    private final TerrainOutlineConfig outline;

    public TerrainVisualConfig(
            TerrainLightingConfig lighting,
            TerrainOutlineConfig outline) {

        this.lighting = lighting;
        this.outline = outline;
    }

    public TerrainLightingConfig lighting() {
        return lighting;
    }

    public TerrainOutlineConfig outline() {
        return outline;
    }
}