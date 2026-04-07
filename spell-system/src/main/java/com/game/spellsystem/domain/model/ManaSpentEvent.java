package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.Mana;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record ManaSpentEvent(WandId wandId, Mana amount) implements DomainEvent {
    public ManaSpentEvent {
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(amount, "amount");
    }
}
