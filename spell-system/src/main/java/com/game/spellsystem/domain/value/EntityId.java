package com.game.spellsystem.domain.value;

import java.util.Objects;

public record EntityId(String value) {
    public EntityId {
        Objects.requireNonNull(value, "value");
    }
}
