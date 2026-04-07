package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record AudioEffectRequest(String soundType, WorldPoint at, Map<String, Double> params) {
    public AudioEffectRequest {
        Objects.requireNonNull(soundType, "soundType");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(params, "params");
    }
}
