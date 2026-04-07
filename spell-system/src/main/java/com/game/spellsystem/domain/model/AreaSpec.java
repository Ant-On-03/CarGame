package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record AreaSpec(
        WorldPoint center,
        double radius,
        Map<String, Double> numericProps
) {
    public AreaSpec {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(numericProps, "numericProps");
    }
}
