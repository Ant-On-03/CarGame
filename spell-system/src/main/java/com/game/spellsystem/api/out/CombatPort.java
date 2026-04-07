package com.game.spellsystem.api.out;

public interface CombatPort {
    DamageResult applyDamage(DamageRequest request);

    void applyImpulse(ImpulseRequest request);

    void applyStatusEffect(StatusEffectRequest request);
}
