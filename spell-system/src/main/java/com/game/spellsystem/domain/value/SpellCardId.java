package com.game.spellsystem.domain.value;

import java.util.Objects;

public record SpellCardId(String value) {
    public SpellCardId {
        Objects.requireNonNull(value, "value");
    }
}
