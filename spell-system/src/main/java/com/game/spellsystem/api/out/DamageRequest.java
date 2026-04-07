package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;
import com.game.spellsystem.domain.value.ProjectileId;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record DamageRequest(
        EntityId sourceId,
        EntityId targetId,
        ProjectileId projectileId,
        WorldPoint hitPoint,
        double amount,
        Map<String, Double> tags
) {
    public DamageRequest {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(hitPoint, "hitPoint");
        Objects.requireNonNull(tags, "tags");
    }
}
