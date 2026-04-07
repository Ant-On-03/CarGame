package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.Angle;
import com.game.spellsystem.domain.value.ProjectileId;
import com.game.spellsystem.domain.value.Vector2d;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record ProjectileSpec(
        ProjectileId projectileId,
        WorldPoint origin,
        Vector2d initialVelocity,
        Angle direction,
        double lifetimeSeconds,
        Map<String, Double> numericProps
) {
    public ProjectileSpec {
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(initialVelocity, "initialVelocity");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(numericProps, "numericProps");
    }
}
