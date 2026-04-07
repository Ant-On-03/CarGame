package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.EntityId;
import com.game.spellsystem.domain.value.ProjectileId;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record HitSpec(
        ProjectileId projectileId,
        EntityId targetId,
        WorldPoint contactPoint,
        double damage
) {
    public HitSpec {
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(contactPoint, "contactPoint");
    }
}
