package com.game.spellsystem.domain.value;

import java.util.Objects;

public record CasterId(String value) {
    public CasterId {
        Objects.requireNonNull(value, "value");
    }
}
