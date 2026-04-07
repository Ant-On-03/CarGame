package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;

import java.util.List;
import java.util.Objects;

public record SpatialQueryResult(List<EntityId> entityIds) {
    public SpatialQueryResult {
        Objects.requireNonNull(entityIds, "entityIds");
    }
}
