package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.SpellCardId;

import java.util.List;
import java.util.Objects;

public record CastProgram(
        List<SpellCardId> drawnCards,
        List<SpellCardId> executionOrder,
        int multicastCount,
        int triggerDepth
) {
    public CastProgram {
        Objects.requireNonNull(drawnCards, "drawnCards");
        Objects.requireNonNull(executionOrder, "executionOrder");
    }
}
