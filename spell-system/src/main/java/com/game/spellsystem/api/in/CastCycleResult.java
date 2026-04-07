package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.model.DomainEvent;

import java.util.List;
import java.util.Objects;

public record CastCycleResult(boolean accepted, List<DomainEvent> events) {
    public CastCycleResult {
        Objects.requireNonNull(events, "events");
    }
}
