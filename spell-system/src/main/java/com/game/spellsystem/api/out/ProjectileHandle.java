package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.ProjectileId;

import java.util.Objects;

public record ProjectileHandle(ProjectileId projectileId) {
    public ProjectileHandle {
        Objects.requireNonNull(projectileId, "projectileId");
    }
}
