package com.game.spellsystem.domain.model;

import java.util.Objects;

public record ProjectileRequestedEvent(ProjectileSpec projectileSpec) implements DomainEvent {
    public ProjectileRequestedEvent {
        Objects.requireNonNull(projectileSpec, "projectileSpec");
    }
}
