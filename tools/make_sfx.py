"""Synthesizes Neon Rush's sound effects as short 16-bit WAV files.

Run: python3 tools/make_sfx.py   (writes godot/audio/sfx_*.wav)
Everything is made from sine/saw waves and noise, so there are no licences to track.
"""
import os
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "godot", "audio")
rng = np.random.default_rng(7)


def t(sec):
    return np.arange(int(RATE * sec)) / RATE


def env(n, attack=0.005, release=0.1):
    e = np.ones(n)
    a = max(1, int(RATE * attack))
    r = max(1, int(RATE * release))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def sweep(f0, f1, sec, shape="sine"):
    x = t(sec)
    f = np.geomspace(f0, f1, len(x))
    ph = 2 * np.pi * np.cumsum(f) / RATE
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1) - 1
    if shape == "square":
        return np.sign(np.sin(ph))
    return np.sin(ph)


def tone(freq, sec, shape="sine"):
    return sweep(freq, freq, sec, shape)


def lowpass(x, k):
    """One-pole low-pass; k in (0, 1], smaller is darker."""
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += k * (v - acc)
        y[i] = acc
    return y


def save(name, x, gain=0.8):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    data = (x * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, f"sfx_{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())


def seq(*parts):
    return np.concatenate(parts)


os.makedirs(OUT, exist_ok=True)

# UI click: a short bright blip.
x = tone(1400, 0.05) * 0.6 + tone(2800, 0.05) * 0.2
save("click", x * env(len(x), 0.001, 0.04), 0.5)

# Countdown beep and the GO chord.
x = tone(660, 0.14, "square") * 0.3 + tone(660, 0.14)
save("count", lowpass(x, 0.3) * env(len(x), 0.002, 0.08), 0.6)
x = sum(tone(f, 0.5, "saw") for f in (523, 659, 784, 1046)) + sweep(200, 1200, 0.5) * 2
save("go", lowpass(x, 0.25) * env(len(x), 0.005, 0.35), 0.75)

# Coin: two quick rising chimes.
a = tone(1318, 0.06) + tone(2636, 0.06) * 0.3
b = tone(1976, 0.16) + tone(3952, 0.16) * 0.3
x = seq(a * env(len(a), 0.001, 0.02), b * env(len(b), 0.001, 0.14))
save("coin", x, 0.5)

# Capture: a rising arpeggio with a shimmer.
notes = [523, 659, 784, 1046, 1318]
parts = []
for f in notes:
    p = tone(f, 0.07, "saw") * 0.4 + tone(f * 2, 0.07) * 0.3
    parts.append(lowpass(p, 0.35) * env(len(p), 0.002, 0.03))
tail = tone(1568, 0.3) + tone(2093, 0.3) * 0.5
x = seq(*parts, tail * env(len(tail), 0.002, 0.28))
save("capture", x, 0.6)

# Knockout: a noise blast over a falling bass drop and a glassy zap.
n = rng.standard_normal(int(RATE * 0.7))
blast = lowpass(n, 0.12) * np.exp(-t(0.7) * 6)
drop = sweep(180, 40, 0.7) * np.exp(-t(0.7) * 3) * 1.4
zap = sweep(2400, 200, 0.25, "saw") * 0.35
zap = np.pad(zap, (0, len(blast) - len(zap)))
x = blast + drop + lowpass(zap, 0.3)
save("ko", x * env(len(x), 0.001, 0.2), 0.9)

# Power-up: a bright upward sweep.
x = sweep(400, 2400, 0.35, "saw") * 0.5 + sweep(800, 4800, 0.35) * 0.4
save("power", lowpass(x, 0.3) * env(len(x), 0.003, 0.15), 0.6)

# Blocked by a shield: a dull metallic clang.
x = tone(220, 0.25, "square") * 0.4 + tone(331, 0.25) + tone(587, 0.25) * 0.4
save("blocked", lowpass(x, 0.2) * np.exp(-t(0.25) * 14), 0.6)

# Win fanfare and lose sting.
parts = []
for f, d in ((523, 0.12), (659, 0.12), (784, 0.12), (1046, 0.5)):
    p = sum(tone(f * m, d, "saw") * g for m, g in ((1, 0.5), (1.5, 0.2), (2, 0.25)))
    parts.append(lowpass(p, 0.3) * env(len(p), 0.003, min(0.3, d * 0.6)))
save("win", seq(*parts), 0.75)
parts = []
for f, d in ((392, 0.18), (330, 0.18), (262, 0.5)):
    p = tone(f, d, "saw")
    parts.append(lowpass(p, 0.15) * env(len(p), 0.003, d * 0.6))
save("lose", seq(*parts), 0.6)

# Streak: a quick laser triple.
p = sweep(1800, 600, 0.08, "square")
x = seq(*(lowpass(p, 0.4) * env(len(p), 0.001, 0.03) for _ in range(3)))
save("streak", x, 0.5)
print("done")
