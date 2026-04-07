package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record LineOfSightQuery(WorldPoint from, WorldPoint to) {
    public LineOfSightQuery {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
