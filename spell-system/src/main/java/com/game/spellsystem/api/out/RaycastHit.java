package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;
import java.util.Optional;

public record RaycastHit(Optional<EntityId> entityId, WorldPoint point, double distance) {
    public RaycastHit {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(point, "point");
    }
}
