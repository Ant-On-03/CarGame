package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.SpellCardId;

import java.util.Map;
import java.util.Objects;

public record ModifierDefinition(
        SpellCardId sourceCard,
        Map<String, Double> multipliers,
        Map<String, Double> additives
) {
    public ModifierDefinition {
        Objects.requireNonNull(sourceCard, "sourceCard");
        Objects.requireNonNull(multipliers, "multipliers");
        Objects.requireNonNull(additives, "additives");
    }
}
