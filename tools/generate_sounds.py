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


def fft_filter(x, gain):
    """Filter im Frequenzbereich: gain(f) -> Faktor."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec * gain(f), len(x))


def pink(n, seed):
    """Rosa Rauschen (1/f) – das 'Fuzz' im Phaserklang."""
    return normalize(fft_filter(noise(n, seed), lambda f: 1 / np.sqrt(np.maximum(f, 20))), 1.0)


def band(x, lo, hi):
    return fft_filter(x, lambda f: ((f >= lo) & (f <= hi)).astype(float) * 1.0)


def smooth_lp(x, cutoff):
    """weicher Tiefpass (sanfter Abfall statt harter Kante)."""
    return fft_filter(x, lambda f: 1 / np.sqrt(1 + (f / cutoff) ** 4))


def soft_env(n, attack, release, total):
    t = np.arange(n) / SR
    a = 0.5 - 0.5 * np.cos(np.pi * np.clip(t / attack, 0, 1))
    r = 0.5 - 0.5 * np.cos(np.pi * np.clip((total - t) / release, 0, 1))
    return a * r


# ---------------------------------------------------------------------------------------------
#  Strahlen (2,1 s; bricht im Spiel beim Loslassen ab). Grundidee wie beim Original: ein
#  gleichmäßiges elektrisches Brummen, mit rosa Rauschen "angefuzzt" – weich, ohne schrille Obertöne.
# ---------------------------------------------------------------------------------------------

def beam_tos(base=880, warble=0.025, rate=6.5, dur=2.1, seed=1, fuzz=0.45, cutoff=2600):
    """Originalserie: singendes Brummen mit leichtem Schweben und Rauschfahne."""
    t = t_axis(dur)
    f = base * (1 + warble * np.sin(2 * np.pi * rate * t)) * (1 + 0.03 * np.minimum(t / 0.3, 1))
    ph = phase(f)
    tone = 0.75 * np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    hiss = band(pink(len(t), seed), base * 0.6, base * 3.0)
    hiss = normalize(hiss, 1.0) * (0.8 + 0.2 * np.sin(2 * np.pi * rate * t + 1))
    x = tone + fuzz * hiss
    x = smooth_lp(x, cutoff)
    return soft(x, 1.1) * soft_env(len(t), 0.08, 0.18, dur)


def beam_tng(base=360, dur=2.1, seed=2, cutoff=1800, fuzz=0.55):
    """Next Generation: warmes, summendes Rauschen mit tiefem Kern und weichem Einsatz (‚wusch‘)."""
    t = t_axis(dur)
    sweep = 1 - 0.25 * np.exp(-t / 0.12)
    f = base * sweep * (1 + 0.006 * np.sin(2 * np.pi * 4.5 * t))
    ph = phase(f)
    core = 0.6 * np.sin(ph) + 0.25 * np.sin(phase(f * 1.004 * 2)) + 0.12 * np.sin(3 * ph)
    hiss = normalize(band(pink(len(t), seed), 500, 3200), 1.0)
    whoosh = normalize(band(pink(len(t), seed + 7), 200, 1500), 1.0) * np.exp(-t / 0.25)
    x = core + fuzz * hiss + 0.4 * whoosh
    x = smooth_lp(x, cutoff)
    return soft(x, 1.1) * soft_env(len(t), 0.1, 0.2, dur)


def beam_refit(base=170, dur=2.1, seed=3):
    """Kinofilme (rote Emitter): tiefes, rundes Dröhnen mit Rauschen, leicht ansteigend."""
    t = t_axis(dur)
    f = base * (1 + 0.12 * (1 - np.exp(-t / 0.35)))
    ph = phase(f)
    x = 0.6 * np.sin(ph) + 0.3 * np.sin(2 * ph * 1.002) + 0.15 * np.sin(3 * ph) + 0.3 * np.sin(ph / 2)
    x += 0.5 * normalize(band(pink(len(t), seed), 150, 1400), 1.0)
    x *= 1 + 0.08 * np.sin(2 * np.pi * 7 * t)
    x = smooth_lp(x, 1400)
    return soft(x, 1.1) * soft_env(len(t), 0.12, 0.25, dur)


def pulses(chirp_hi=1900, chirp_lo=480, pulse=0.16, period=0.3, dur=2.1, seed=4, body=0.5, cutoff=2800):
    """NX-01 (Phasenpistole/-kanone): weiche, kurze 'Pew'-Schüsse, passend zum Blinken des Strahls."""
    t = t_axis(dur)
    x = np.zeros(len(t))
    n = int(pulse * SR)
    tp = np.arange(n) / SR
    f = chirp_lo + (chirp_hi - chirp_lo) * np.exp(-tp / (pulse / 3))
    ph = phase(f)
    shape = soft_env(n, 0.006, pulse * 0.8, pulse)
    one = (0.8 * np.sin(ph) + 0.12 * np.sin(2 * ph)) * shape
    one += body * np.sin(phase(np.full(n, chirp_lo / 3))) * shape
    one += 0.15 * normalize(band(pink(n, seed), 600, 2500), 1.0) * np.exp(-tp / 0.04)
    start = 0.0
    while start + pulse <= dur:
        i = int(start * SR)
        x[i:i + n] += one
        start += period
    return soft(smooth_lp(x, cutoff), 1.05)


# ---------------------------------------------------------------------------------------------
#  Torpedos
# ---------------------------------------------------------------------------------------------

def torpedo(dur=1.2, f_hi=1100, f_lo=80, seed=5, shimmer=0.0, low=1.0):
    """Ausstoß mit fallendem 'Pchooo', Luftstoß und tiefem Wumms; Quanten zusätzlich metallisch."""
    t = t_axis(dur)
    f = f_lo + (f_hi - f_lo) * np.exp(-t / 0.13)
    ph = phase(f)
    x = 0.6 * np.sin(ph) + 0.15 * np.sin(2 * ph) * np.exp(-t / 0.2)
    x += low * 0.8 * np.sin(phase(np.full(len(t), 50))) * np.exp(-t / 0.4)
    x += 0.4 * normalize(band(pink(len(t), seed), 120, 1800), 1.0) * np.exp(-t / 0.2)
    if shimmer:
        for k, fr in enumerate((1700, 2350, 3100)):
            x += shimmer * 0.07 * np.sin(phase(np.full(len(t), fr)) + k) * np.exp(-t / 0.45) * (1 + 0.6 * np.sin(2 * np.pi * 19 * t))
    x = smooth_lp(x, 2200)
    return soft(x, 1.2) * soft_env(len(t), 0.008, 0.45, dur)


def biquad_bandpass(x, center, q):
    """Resonanter Bandpass mit zeitlich veränderlicher Mittenfrequenz (Array in Hz)."""
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        w = 2 * np.pi * center[i] / SR
        alpha = np.sin(w) / (2 * q)
        b0, b2 = alpha, -alpha
        a0, a1, a2 = 1 + alpha, -2 * np.cos(w), 1 - alpha
        v = (b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, x[i]
        y2, y1 = y1, v
        y[i] = v
    return y


def beam_electric(base=1100, dur=2.1, seed=41, buzz=50, cutoff=3000):
    """Variante A 'elektrisch': kurzes Hochziehen, dann gleichmäßiger Ton mit 50-Hz-Brummkörnung
    und schmalem Rauschband um den Ton (Grundidee des Originals: Brummen + Fuzz)."""
    t = t_axis(dur)
    f = base * (1 - 0.5 * np.exp(-t / 0.05))
    ph = phase(f)
    tone = np.sin(ph) + 0.25 * np.sin(2 * ph)
    grain = 1 - 0.35 * (0.5 + 0.5 * np.sin(2 * np.pi * buzz * t))
    fuzz = normalize(band(pink(len(t), seed), base * 0.8, base * 1.3), 1.0)
    x = (0.8 * tone + 0.5 * fuzz) * grain
    return soft(smooth_lp(x, cutoff), 1.2) * soft_env(len(t), 0.03, 0.15, dur)


def beam_resonant(base=130, lo=600, hi=1300, dur=2.1, seed=42, q=6):
    """Variante B 'resonant': weicher Sägezahn durch einen singenden Bandpass, der schnell hochfährt
    und dann leicht pendelt – ein 'Pjiuuu' das in ein sattes 'Zschhh' übergeht."""
    t = t_axis(dur)
    raw = 0.6 * saw(phase(np.full(len(t), base))) + 0.6 * saw(phase(np.full(len(t), base * 1.01))) + 0.5 * pink(len(t), seed)
    center = lo + (hi - lo) * (1 - np.exp(-t / 0.09))
    center = center * (1 + 0.08 * np.sin(2 * np.pi * 4 * t))
    x = biquad_bandpass(raw, center, q)
    x = normalize(x, 1.0)
    return soft(smooth_lp(x, hi * 2.2), 1.3) * soft_env(len(t), 0.02, 0.18, dur)


def beam_chorus(base=820, dur=2.1, seed=43, start=1900):
    """Variante C 'Strahlbündel': mehrere leicht verstimmte Töne (schimmernde Schwebung), Einsatz mit
    fallendem 'Pjiu', darunter ein Hauch Rauschen."""
    t = t_axis(dur)
    glide = base + (start - base) * np.exp(-t / 0.06)
    x = np.zeros(len(t))
    for k, d in enumerate((-0.017, -0.007, 0.0, 0.006, 0.015)):
        x += np.sin(phase(glide * (1 + d)) + k)
    x = x / 5 + 0.25 * normalize(band(pink(len(t), seed), base * 0.5, base * 2.5), 1.0)
    return soft(smooth_lp(x, 2600), 1.2) * soft_env(len(t), 0.02, 0.15, dur)


def chorus_pulses(base=1000, start=2200, pulse=0.15, period=0.3, dur=2.1, seed=44):
    """NX-01: kurze Pulse im Klang des Strahlbündels (fallendes 'Pjiu', ausklingend)."""
    t = t_axis(dur)
    x = np.zeros(len(t))
    n = int(pulse * SR)
    one = beam_chorus(base, pulse, seed, start) * np.exp(-np.arange(n) / SR / (pulse / 2.5))
    one = one[:n]
    s0 = 0.0
    while s0 + pulse <= dur:
        i = int(s0 * SR)
        x[i:i + n] += one
        s0 += period
    return x


VARIANTS = {
    "hand_a": lambda: beam_electric(1100, seed=51),
    "hand_b": lambda: beam_resonant(140, 650, 1400, seed=52),
    "hand_c": lambda: beam_chorus(820, seed=53),
    "ship_a": lambda: beam_electric(520, seed=61, buzz=40, cutoff=2000),
    "ship_b": lambda: beam_resonant(70, 300, 750, seed=62, q=5),
    "ship_c": lambda: beam_chorus(390, seed=63, start=1000),
}


SOUNDS = {
    # Handwaffen: Strahlbündel (mehrere leicht verstimmte Töne, fallender Einsatz)
    "phaser_hand_nx": lambda: chorus_pulses(1000, 2200, 0.15, 0.3, seed=11),
    "phaser_hand_tos": lambda: beam_chorus(900, seed=12, start=2000),
    "phaser_hand_tng": lambda: beam_chorus(760, seed=13, start=1700),
    "phaser_rifle": lambda: beam_chorus(560, seed=14, start=1300),
    # Schiffsphaser
    "phaser_ship_nx": lambda: chorus_pulses(420, 1000, 0.2, 0.3, seed=21),
    "phaser_ship_tos": lambda: beam_chorus(470, seed=22, start=1100),
    "phaser_ship_refit": lambda: beam_chorus(300, seed=23, start=800),
    "phaser_ship_tng": lambda: beam_chorus(390, seed=24, start=1000),
    # Torpedos
    "torpedo_photon": lambda: torpedo(1.2, 1200, 90, seed=31),
    "torpedo_quantum": lambda: torpedo(1.5, 800, 55, seed=32, shimmer=1.0, low=1.3),
}


def write_ogg(name, x):
    x = normalize(np.asarray(x, dtype=np.float64), 0.5)  # leiser und mit Reserve
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
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == "--variants":   # Klangvarianten zum Vergleichen
        OUT = sys.argv[2]
        for name, fn in VARIANTS.items():
            write_ogg(name, fn())
            print("ok", name)
        sys.exit()
    for name, fn in SOUNDS.items():
        write_ogg(name, fn())
        print("ok", name)
