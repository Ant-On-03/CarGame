package com.game.spellsystem.domain.value;

import java.time.Instant;
import java.util.Objects;

public record SimulationTime(Instant value) {
    public SimulationTime {
        Objects.requireNonNull(value, "value");
    }
}
