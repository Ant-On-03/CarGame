package com.game.spellsystem.domain.value;

import java.time.Duration;
import java.util.Objects;

public record Cooldown(Duration value) {
    public Cooldown {
        Objects.requireNonNull(value, "value");
    }
}
