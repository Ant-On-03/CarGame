package com.game.spellsystem.domain.value;

import java.util.Objects;

public record WandId(String value) {
    public WandId {
        Objects.requireNonNull(value, "value");
    }
}
