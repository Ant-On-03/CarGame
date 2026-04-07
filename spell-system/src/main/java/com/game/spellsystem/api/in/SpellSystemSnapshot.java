package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.model.WandRuntimeState;

import java.util.List;
import java.util.Objects;

public record SpellSystemSnapshot(List<WandRuntimeState> wandStates) {
    public SpellSystemSnapshot {
        Objects.requireNonNull(wandStates, "wandStates");
    }
}
