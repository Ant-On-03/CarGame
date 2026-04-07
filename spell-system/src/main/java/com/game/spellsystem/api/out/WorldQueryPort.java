package com.game.spellsystem.api.out;

public interface WorldQueryPort {
    RaycastResult raycast(RaycastQuery query);

    SpatialQueryResult queryArea(SpatialQuery query);

    boolean hasLineOfSight(LineOfSightQuery query);
}
