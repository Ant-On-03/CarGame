package com.game.rendering.outline;

import com.badlogic.gdx.math.Vector3;

public interface OutlineColorStrategy {
    Vector3 compute(Vector3 baseColor, float depth);
}