package com.game.spellsystem.domain.model;

import com.game.spellsystem.domain.value.Cooldown;
import com.game.spellsystem.domain.value.Mana;
import com.game.spellsystem.domain.value.SpellCardId;
import com.game.spellsystem.domain.value.WandId;

import java.util.List;
import java.util.Objects;

public record WandDefinition(
        WandId wandId,
        int capacity,
        boolean shuffle,
        Cooldown castDelay,
        Cooldown rechargeTime,
        Mana manaMax,
        Mana manaRechargePerSecond,
        double spreadDegrees,
        double recoil,
        List<SpellCardId> deck
) {
    public WandDefinition {
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(castDelay, "castDelay");
        Objects.requireNonNull(rechargeTime, "rechargeTime");
        Objects.requireNonNull(manaMax, "manaMax");
        Objects.requireNonNull(manaRechargePerSecond, "manaRechargePerSecond");
        Objects.requireNonNull(deck, "deck");
    }
}
