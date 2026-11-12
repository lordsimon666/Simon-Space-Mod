package de.simon.spacemod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Waffensounds (selbst synthetisiert mit tools/generate_sounds.py, an die Serien angelehnt):
 * Strahlen je Epoche und Waffe sowie Photonen- und Quantentorpedos.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SimonSpaceMod.MODID);
    /** Strahlsounds in fester Reihenfolge (Index wird mit dem Strahl synchronisiert). */
    public static final List<RegistryObject<SoundEvent>> BEAMS = new ArrayList<>();

    public static final RegistryObject<SoundEvent> PHASER_HAND_NX = beam("phaser_hand_nx");
    public static final RegistryObject<SoundEvent> PHASER_HAND_TOS = beam("phaser_hand_tos");
    public static final RegistryObject<SoundEvent> PHASER_HAND_TNG = beam("phaser_hand_tng");
    public static final RegistryObject<SoundEvent> PHASER_RIFLE = beam("phaser_rifle");
    public static final RegistryObject<SoundEvent> PHASER_SHIP_NX = beam("phaser_ship_nx");
    public static final RegistryObject<SoundEvent> PHASER_SHIP_TOS = beam("phaser_ship_tos");
    public static final RegistryObject<SoundEvent> PHASER_SHIP_REFIT = beam("phaser_ship_refit");
    public static final RegistryObject<SoundEvent> PHASER_SHIP_TNG = beam("phaser_ship_tng");
    public static final RegistryObject<SoundEvent> TORPEDO_PHOTON = sound("torpedo_photon");
    public static final RegistryObject<SoundEvent> TORPEDO_QUANTUM = sound("torpedo_quantum");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, name)));
    }

    private static RegistryObject<SoundEvent> beam(String name) {
        RegistryObject<SoundEvent> s = sound(name);
        BEAMS.add(s);
        return s;
    }

    public static int index(RegistryObject<SoundEvent> s) {
        return BEAMS.indexOf(s);
    }
}
