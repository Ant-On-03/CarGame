package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.Cooldown;
import com.game.spellsystem.domain.value.Mana;
import com.game.spellsystem.domain.value.SpellCardId;
import com.game.spellsystem.domain.value.WandId;

import java.util.List;
import java.util.Objects;

public record WandRuntimeState(
        WandId wandId,
        Mana currentMana,
        Cooldown castDelayRemaining,
        Cooldown rechargeRemaining,
        List<SpellCardId> drawStack,
        List<SpellCardId> discardStack,
        boolean triggerLatched
) {
    public WandRuntimeState {
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(currentMana, "currentMana");
        Objects.requireNonNull(castDelayRemaining, "castDelayRemaining");
        Objects.requireNonNull(rechargeRemaining, "rechargeRemaining");
        Objects.requireNonNull(drawStack, "drawStack");
        Objects.requireNonNull(discardStack, "discardStack");
    }
}
