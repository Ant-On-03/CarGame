package com.game.spellsystem.api.out;

import java.util.Map;
import java.util.Objects;

public record WandExternalModifiers(Map<String, Double> multipliers, Map<String, Double> additives) {
    public WandExternalModifiers {
        Objects.requireNonNull(multipliers, "multipliers");
        Objects.requireNonNull(additives, "additives");
    }
}
