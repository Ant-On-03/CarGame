package com.game.spellsystem.application;

import com.game.spellsystem.api.out.CasterStatePort;
import com.game.spellsystem.api.out.CombatPort;
import com.game.spellsystem.api.out.EffectPort;
import com.game.spellsystem.api.out.EntityLookupPort;
import com.game.spellsystem.api.out.ProjectilePort;
import com.game.spellsystem.api.out.WorldQueryPort;

import java.util.Objects;

public record SpellSystemPorts(
        CasterStatePort casterStatePort,
        WorldQueryPort worldQueryPort,
        ProjectilePort projectilePort,
        CombatPort combatPort,
        EffectPort effectPort,
        EntityLookupPort entityLookupPort
) {
    public SpellSystemPorts {
        Objects.requireNonNull(casterStatePort, "casterStatePort");
        Objects.requireNonNull(worldQueryPort, "worldQueryPort");
        Objects.requireNonNull(projectilePort, "projectilePort");
        Objects.requireNonNull(combatPort, "combatPort");
        Objects.requireNonNull(effectPort, "effectPort");
        Objects.requireNonNull(entityLookupPort, "entityLookupPort");
    }
}
