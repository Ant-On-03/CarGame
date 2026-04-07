package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.model.ProjectileSpec;

import java.util.Objects;

public record ProjectileSpawnRequest(ProjectileSpec projectileSpec) {
    public ProjectileSpawnRequest {
        Objects.requireNonNull(projectileSpec, "projectileSpec");
    }
}
