package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record CastStartedEvent(CasterId casterId, WandId wandId) implements DomainEvent {
    public CastStartedEvent {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(wandId, "wandId");
    }
}
