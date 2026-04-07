package com.game.spellsystem.api.in;

public interface SpellcastingCommandPort {
    CastCycleResult equipWand(EquipWandCommand command);

    CastCycleResult setTriggerState(SetTriggerStateCommand command);

    TickResult tick(TickCommand command);

    CastCycleResult forceReload(ForceReloadCommand command);
}
