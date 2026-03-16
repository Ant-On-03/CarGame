package com.game.rendering.outline;

import com.badlogic.gdx.math.Vector3;

public class ComplementaryOutlineStrategy implements OutlineColorStrategy {

    private final float mix;

    public ComplementaryOutlineStrategy(float mix) {
        this.mix = mix;
    }

    @Override
    public Vector3 compute(Vector3 baseColor, float depth) {

        Vector3 comp = new Vector3(
                1f - baseColor.x,
                1f - baseColor.y,
                1f - baseColor.z
        );

        return baseColor.lerp(comp, mix);
    }
}