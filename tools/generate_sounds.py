"""Erzeugt die Waffensounds der Mod per Synthese (eigene Klänge, an die Stimmung der Serien
angelehnt, keine Aufnahmen). Ergebnis: Ogg-Vorbis (mono) in assets/simonspacemod/sounds.
Aufruf: python3 tools/generate_sounds.py   (braucht numpy und ffmpeg)"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "simonspacemod", "sounds")


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def phase(freq):
    """Phase für eine zeitlich veränderliche Frequenz (Array in Hz)."""
    return 2 * np.pi * np.cumsum(freq) / SR


def saw(ph):
    return 2 * ((ph / (2 * np.pi)) % 1.0) - 1


def square(ph, duty=0.5):
    return np.where(((ph / (2 * np.pi)) % 1.0) < duty, 1.0, -1.0)


def envelope(n, attack, release, total):
    t = np.arange(n) / SR
    env = np.ones(n)
    env = np.minimum(env, t / max(attack, 1e-4))
    env = np.minimum(env, np.clip((total - t) / max(release, 1e-4), 0, 1))
    return env


def lowpass(x, cutoff):
    # einfacher Einpol-Tiefpass
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def noise(n, seed):
    return np.random.default_rng(seed).uniform(-1, 1, n)


def normalize(x, peak=0.85):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def soft(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


# ---------------------------------------------------------------------------------------------
#  Strahlen (2,1 s; bricht im Spiel beim Loslassen ab)
# ---------------------------------------------------------------------------------------------

def beam_tos(base=1450, depth=180, rate=17, dur=2.1, seed=1):
    """Originalserie: helles, schnell wobbelndes Sirren mit leichtem Anstieg."""
    t = t_axis(dur)
    f = base * (1 + 0.06 * np.minimum(t / 0.25, 1)) + depth * np.sin(2 * np.pi * rate * t)
    ph = phase(f)
    x = 0.6 * np.sin(ph) + 0.25 * np.sin(2 * ph + 0.3) + 0.15 * square(ph, 0.3)
    x += 0.05 * highpass(noise(len(t), seed), 3000)
    return soft(x, 1.8) * envelope(len(t), 0.03, 0.12, dur)


def beam_tng(base=620, dur=2.1, seed=2, bright=1.0):
    """Next Generation: summender, sägender Dauerton mit Schwebung und Knistern; setzt mit Anstieg ein."""
    t = t_axis(dur)
    sweep = 1 - 0.35 * np.exp(-t / 0.08)
    f1 = base * sweep * (1 + 0.004 * np.sin(2 * np.pi * 5 * t))
    f2 = base * 1.007 * sweep
    x = 0.45 * saw(phase(f1)) + 0.35 * saw(phase(f2)) + 0.2 * np.sin(phase(f1 * 2.01))
    x = lowpass(x, 3500 * bright) * 1.3
    crackle = highpass(noise(len(t), seed), 4000) * (np.random.default_rng(seed + 1).random(len(t)) < 0.02)
    x += 0.25 * crackle + 0.06 * highpass(noise(len(t), seed + 2), 6000)
    return soft(x, 2.0) * envelope(len(t), 0.04, 0.15, dur)


def beam_refit(base=210, dur=2.1, seed=3):
    """Kinofilme (rote Emitter): tiefer, kräftiger Strahl mit Sägezahn und Unterton, leicht ansteigend."""
    t = t_axis(dur)
    f = base * (1 + 0.15 * (1 - np.exp(-t / 0.3)))
    x = 0.5 * saw(phase(f)) + 0.3 * saw(phase(f * 1.5 * 1.003)) + 0.4 * np.sin(phase(f / 2))
    x = lowpass(x, 2200)
    x += 0.12 * lowpass(noise(len(t), seed), 900)
    x *= 1 + 0.15 * np.sin(2 * np.pi * 9 * t)
    return soft(x, 2.2) * envelope(len(t), 0.05, 0.2, dur)


def pulses(chirp_hi=3200, chirp_lo=650, pulse=0.15, period=0.3, dur=2.1, seed=4, body=1.0):
    """NX-01 (Phasenpistole/-kanone): einzelne 'Pew'-Schüsse, passend zum Blinken des Strahls."""
    t = t_axis(dur)
    x = np.zeros(len(t))
    n = int(pulse * SR)
    tp = np.arange(n) / SR
    f = chirp_lo + (chirp_hi - chirp_lo) * np.exp(-tp / (pulse / 3))
    ph = phase(f)
    one = (0.7 * np.sin(ph) + 0.3 * square(ph, 0.4)) * np.exp(-tp / (pulse / 2.2))
    one += body * 0.5 * np.sin(phase(np.full(n, chirp_lo / 4))) * np.exp(-tp / (pulse / 2))
    one += 0.1 * highpass(noise(n, seed), 2500) * np.exp(-tp / 0.03)
    start = 0.0
    while start + pulse <= dur:
        i = int(start * SR)
        x[i:i + n] += one
        start += period
    return soft(x, 1.6)


# ---------------------------------------------------------------------------------------------
#  Torpedos
# ---------------------------------------------------------------------------------------------

def torpedo(dur=1.1, f_hi=1800, f_lo=90, seed=5, shimmer=0.0, low=1.0):
    """Ausstoß mit fallendem 'Pchooo', Luftstoß und tiefem Wumms; Quanten zusätzlich metallisch."""
    t = t_axis(dur)
    f = f_lo + (f_hi - f_lo) * np.exp(-t / 0.12)
    ph = phase(f)
    x = 0.5 * np.sin(ph) + 0.25 * saw(ph) * np.exp(-t / 0.25)
    x += low * 0.8 * np.sin(phase(np.full(len(t), 55))) * np.exp(-t / 0.35)
    x += 0.35 * lowpass(noise(len(t), seed), 1500) * np.exp(-t / 0.18)
    if shimmer:
        for k, fr in enumerate((2300, 3170, 4410)):
            x += shimmer * 0.12 * np.sin(phase(np.full(len(t), fr)) + k) * np.exp(-t / 0.5) * (1 + np.sin(2 * np.pi * 23 * t))
    return soft(x, 1.8) * envelope(len(t), 0.005, 0.3, dur)


SOUNDS = {
    # Handwaffen
    "phaser_hand_nx": lambda: pulses(3400, 700, 0.13, 0.3, seed=11, body=0.6),
    "phaser_hand_tos": lambda: beam_tos(1500, 170, 18, seed=12),
    "phaser_hand_tng": lambda: beam_tng(760, seed=13, bright=1.2),
    "phaser_rifle": lambda: beam_tng(430, seed=14, bright=0.8) * 0.7 + beam_tos(900, 90, 11, seed=15) * 0.35,
    # Schiffsphaser
    "phaser_ship_nx": lambda: pulses(1700, 260, 0.2, 0.3, seed=21, body=1.4),
    "phaser_ship_tos": lambda: beam_tos(760, 120, 12, seed=22),
    "phaser_ship_refit": lambda: beam_refit(190, seed=23),
    "phaser_ship_tng": lambda: beam_tng(330, seed=24, bright=0.9),
    # Torpedos
    "torpedo_photon": lambda: torpedo(1.1, 1900, 110, seed=31, shimmer=0.0),
    "torpedo_quantum": lambda: torpedo(1.4, 1200, 60, seed=32, shimmer=1.0, low=1.4),
}


def write_ogg(name, x):
    x = normalize(np.asarray(x, dtype=np.float64))
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        "-map_metadata", "-1", "-fflags", "+bitexact", os.path.join(OUT, name + ".ogg")], check=True)


if __name__ == "__main__":
    for name, fn in SOUNDS.items():
        write_ogg(name, fn())
        print("ok", name)
