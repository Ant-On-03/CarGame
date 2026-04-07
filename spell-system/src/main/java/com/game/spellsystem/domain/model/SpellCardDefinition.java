package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.Charges;
import com.game.spellsystem.domain.value.Mana;
import com.game.spellsystem.domain.value.SpellCardId;

import java.util.Map;
import java.util.Objects;

public record SpellCardDefinition(
        SpellCardId spellCardId,
        SpellCardType type,
        Mana manaCost,
        Charges charges,
        Map<String, Double> numericParams
) {
    public SpellCardDefinition {
        Objects.requireNonNull(spellCardId, "spellCardId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(manaCost, "manaCost");
        Objects.requireNonNull(charges, "charges");
        Objects.requireNonNull(numericParams, "numericParams");
    }
}
