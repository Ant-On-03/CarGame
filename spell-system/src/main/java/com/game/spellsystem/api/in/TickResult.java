package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.model.DomainEvent;

import java.util.List;
import java.util.Objects;

public record TickResult(List<DomainEvent> events) {
    public TickResult {
        Objects.requireNonNull(events, "events");
    }
}
