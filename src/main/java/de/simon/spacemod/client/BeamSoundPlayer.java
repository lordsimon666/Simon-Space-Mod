package de.simon.spacemod.client;

import de.simon.spacemod.ModSounds;
import de.simon.spacemod.entity.PhaserBeamEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;

/** Spielt den Strahlsound am Strahl ab; er endet, wenn der Strahl verschwindet. */
public final class BeamSoundPlayer {
    private BeamSoundPlayer() {}

    public static void play(PhaserBeamEntity beam) {
        int i = beam.soundIndex();
        if (i < 0 || i >= ModSounds.BEAMS.size()) return;
        Minecraft.getInstance().getSoundManager().play(new EntityBoundSoundInstance(ModSounds.BEAMS.get(i).get(),
            SoundSource.PLAYERS, beam.soundVolume(), beam.soundPitch(), beam, beam.getId()));
    }
}
