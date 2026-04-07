package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.SpellCardId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record CardDrawnEvent(WandId wandId, SpellCardId spellCardId) implements DomainEvent {
    public CardDrawnEvent {
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(spellCardId, "spellCardId");
    }
}
