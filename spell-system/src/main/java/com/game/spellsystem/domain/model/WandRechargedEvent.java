package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record WandRechargedEvent(WandId wandId) implements DomainEvent {
    public WandRechargedEvent {
        Objects.requireNonNull(wandId, "wandId");
    }
}
