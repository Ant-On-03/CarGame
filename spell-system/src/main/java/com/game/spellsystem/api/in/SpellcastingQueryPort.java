package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

public interface SpellcastingQueryPort {
    WandRuntimeView getWandRuntime(WandId wandId);

    CasterCastingView getCasterCasting(CasterId casterId);

    SpellSystemSnapshot getSnapshot(SnapshotQuery query);
}
