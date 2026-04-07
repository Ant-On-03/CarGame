package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.RngSeed;
import com.game.spellsystem.domain.value.SimulationTime;

import java.time.Duration;
import java.util.Objects;

public record TickCommand(SimulationTime now, Duration delta, RngSeed seed) {
    public TickCommand {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(seed, "seed");
    }
}
