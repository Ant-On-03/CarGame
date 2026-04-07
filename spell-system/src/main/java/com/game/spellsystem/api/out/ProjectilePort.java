package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.ProjectileId;

public interface ProjectilePort {
    ProjectileHandle spawnProjectile(ProjectileSpawnRequest request);

    void updateProjectile(ProjectileUpdateRequest request);

    void despawnProjectile(ProjectileId projectileId, DespawnReason reason);
}
