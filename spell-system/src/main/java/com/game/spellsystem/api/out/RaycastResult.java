package com.game.spellsystem.api.out;

import java.util.Objects;
import java.util.Optional;

public record RaycastResult(Optional<RaycastHit> hit) {
    public RaycastResult {
        Objects.requireNonNull(hit, "hit");
    }
}
