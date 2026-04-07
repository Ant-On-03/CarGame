package com.game.spellsystem.api.out;

public interface EffectPort {
    void emitVisual(VisualEffectRequest request);

    void emitAudio(AudioEffectRequest request);

    void emitGameplayEvent(GameplayEvent event);
}
