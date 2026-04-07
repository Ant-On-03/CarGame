package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.model.WandRuntimeState;

import java.util.Objects;

public record WandRuntimeView(WandRuntimeState state) {
    public WandRuntimeView {
        Objects.requireNonNull(state, "state");
    }
}
