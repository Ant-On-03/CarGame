package com.game.spellsystem.domain.value;

import java.util.Objects;

public record ProjectileId(String value) {
    public ProjectileId {
        Objects.requireNonNull(value, "value");
    }
}
