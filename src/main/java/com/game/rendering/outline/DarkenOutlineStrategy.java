package com.game.rendering.outline;

import com.badlogic.gdx.math.Vector3;

public class DarkenOutlineStrategy implements OutlineColorStrategy {

    private final float factor;

    public DarkenOutlineStrategy(float factor) {
        this.factor = factor;
    }

    @Override
    public Vector3 compute(Vector3 baseColor, float depth) {
        return new Vector3(
                baseColor.x * factor,
                baseColor.y * factor,
                baseColor.z * factor
        );
    }
}