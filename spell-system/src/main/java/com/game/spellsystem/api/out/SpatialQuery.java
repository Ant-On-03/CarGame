package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record SpatialQuery(WorldPoint center, double radius) {
    public SpatialQuery {
        Objects.requireNonNull(center, "center");
    }
}
