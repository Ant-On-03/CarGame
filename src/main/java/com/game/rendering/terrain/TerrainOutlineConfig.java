package com.game.rendering.terrain;

import com.game.rendering.outline.OutlineColorStrategy;

public class TerrainOutlineConfig {

    private float thickness = 2.4f;
    private OutlineColorStrategy colorStrategy;

    public TerrainOutlineConfig(OutlineColorStrategy strategy) {
        this.colorStrategy = strategy;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }

    public OutlineColorStrategy getStrategy() {
        return colorStrategy;
    }

    public void setStrategy(OutlineColorStrategy strategy) {
        this.colorStrategy = strategy;
    }
}