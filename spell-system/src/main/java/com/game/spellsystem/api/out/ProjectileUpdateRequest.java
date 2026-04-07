package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.ProjectileId;
import com.game.spellsystem.domain.value.Vector2d;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record ProjectileUpdateRequest(ProjectileId projectileId, WorldPoint position, Vector2d velocity) {
    public ProjectileUpdateRequest {
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }
}
