package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record VisualEffectRequest(String effectType, WorldPoint at, Map<String, Double> params) {
    public VisualEffectRequest {
        Objects.requireNonNull(effectType, "effectType");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(params, "params");
    }
}
