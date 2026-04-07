package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.Vector2d;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record RaycastQuery(WorldPoint origin, Vector2d direction, double maxDistance) {
    public RaycastQuery {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
    }
}
