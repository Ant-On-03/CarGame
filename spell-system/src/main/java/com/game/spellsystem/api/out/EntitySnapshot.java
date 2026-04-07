package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record EntitySnapshot(EntityId entityId, WorldPoint position, Map<String, Double> numericState) {
    public EntitySnapshot {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(numericState, "numericState");
    }
}
