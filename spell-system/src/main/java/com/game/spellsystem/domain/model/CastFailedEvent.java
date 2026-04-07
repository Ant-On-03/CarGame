package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record CastFailedEvent(CasterId casterId, WandId wandId, CastFailureReason reason) implements DomainEvent {
    public CastFailedEvent {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(reason, "reason");
    }
}
