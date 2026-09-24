#!/usr/bin/env python3
"""Synthesises LavaVisual's own hit/crit/totem/kill sounds (pip install numpy soundfile).

Every waveform is generated from maths in this file, so the results are original MIT content.
Output: mono 44.1 kHz Ogg Vorbis (Minecraft needs mono for positional audio)."""
from pathlib import Path
import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent / 'ports' / 'mc26.2' / 'src' / 'main' / 'resources' / 'assets' / 'lavavisual' / 'sounds' / 'lib'
RNG = np.random.default_rng(2607)


def T(d):
    return np.arange(int(SR * d)) / SR


def fit(x, n):
    return x[:n] if len(x) >= n else np.pad(x, (0, n - len(x)))


def env(d, tau, attack=0.0015):
    t = T(d)
    a = np.minimum(1, t / attack) if attack > 0 else 1
    return a * np.exp(-t / tau)


def sweep(f0, f1, d, k=1.0):
    t = T(d)
    f = f1 + (f0 - f1) * np.exp(-t / (d * k / 5))
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def exp_sweep(f0, f1, d):
    t = T(d)
    f = f0 * (f1 / f0) ** (t / d)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def noise(d):
    return RNG.standard_normal(int(SR * d))


def band(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    w = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) if lo > 0 else 1
    w = w * (1 / (1 + (f / hi) ** 4)) if hi else w
    return np.fft.irfft(spec * w, len(x))


def partials(freqs, amps, taus, d, attack=0.0008, phase=True):
    t = T(d)
    out = np.zeros_like(t)
    for f, a, tau in zip(freqs, amps, taus):
        out += a * np.sin(2 * np.pi * f * t + (RNG.uniform(0, 6.28) if phase else 0)) * np.exp(-t / tau)
    release = np.clip((d - t) / (0.3 * d), 0, 1) ** 2  # no clicks when a note is cut
    return out * np.minimum(1, t / attack) * release


def glide(f0, f1, dsweep, dhold):
    t = T(dsweep + dhold)
    f = np.where(t < dsweep, f0 * (f1 / f0) ** (t / dsweep), f1)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def square(f, d, duty=0.5, harmonics=40):
    t = T(d)
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        if k * f > SR / 2.2:
            break
        out += np.sin(np.pi * k * duty) / k * np.cos(2 * np.pi * k * f * t)
    return out


def at(total, *parts):
    out = np.zeros(int(SR * total))
    for start, x in parts:
        i = int(SR * start)
        n = min(len(x), len(out) - i)
        out[i:i + n] += x[:n]
    return out


def finish(x, loud=0.2, tail=0.012):
    x = x - np.mean(x)
    n = len(x)
    fade = int(SR * tail)
    x[-fade:] *= np.linspace(1, 0, fade) ** 2
    x[:32] *= np.linspace(0, 1, 32)
    head = x[:int(SR * 0.12)]
    rms = np.sqrt(np.mean(head ** 2)) + 1e-9
    x = x * (loud / rms)
    peak = np.max(np.abs(x))
    if peak > 0.94:
        x = np.tanh(x / peak * 1.4) / np.tanh(1.4) * 0.94
    return x.astype(np.float32)


def bell(f0=1318.5, d=0.9):
    body = partials([f0, f0 * 2.0, f0 * 2.76, f0 * 5.40, f0 * 8.93, f0 * 1.003],
                    [1, .32, .45, .2, .08, .5], [.55, .3, .22, .09, .05, .5], d)
    strike = band(noise(0.006), 3000, 12000) * .35
    return at(d, (0, body), (0, strike * np.linspace(1, 0, len(strike))))


def sounds():
    s = {}
    s['bell'] = finish(bell(), .16)
    d = .16
    pop = fit(glide(380, 1250, .07, .09), int(SR * d))
    s['bubble'] = finish(pop * fit(env(d, .045, .003), int(SR * d)), .22)
    click = partials([2300, 4100], [1, .4], [.012, .006], .05) + band(noise(.05), 2000, 7000) * env(.05, .003, 0) * .6
    s['click'] = finish(click, .22, .005)
    tick = np.sin(2 * np.pi * 3100 * T(.07) + 2.2 * np.exp(-T(.07) / .012) * np.sin(2 * np.pi * 4650 * T(.07))) * env(.07, .016, .0005)
    s['tick'] = finish(tick + band(noise(.07), 4000, 14000) * env(.07, .004, 0) * .5, .22, .006)
    punch = sweep(190, 52, .2, .7) * env(.2, .07, .001) + band(noise(.2), 0, 1600) * env(.2, .018, 0) * .9
    s['punch'] = finish(np.tanh(punch * 2.2), .26)
    metal = partials([523, 529, 1187, 1742, 2573, 3380, 4721], [1, .8, .7, .5, .35, .22, .12], [.4, .38, .25, .17, .11, .07, .04], .55)
    s['metal'] = finish(metal + band(noise(.55), 2500, 10000) * env(.55, .006, 0) * .8, .17)
    glass = partials([2637, 4186, 5920, 7458, 9890], [1, .7, .45, .3, .15], [.16, .1, .07, .05, .03], .32)
    s['glass'] = finish(glass + band(noise(.32), 6000, 16000) * env(.32, .005, 0) * .5, .16)
    laser = np.sin(2 * np.pi * np.cumsum(1900 * (220 / 1900) ** (T(.2) / .16)) / SR)
    laser = np.sign(laser) * np.abs(laser) ** .45
    s['laser'] = finish(band(laser, 150, 5000) * env(.2, .06, .001), .19)
    coin = at(.34, (0, square(880, .06) * .8), (.055, square(1318.5, .28) * env(.28, .09, .001)))
    s['coin'] = finish(band(coin, 200, 9000), .15)
    crystal = at(.7, *[(i * .035, partials([f, f * 2.76, f * 5.1], [1, .25, .08], [.28, .1, .05], .6)) for i, f in enumerate([2093, 2637, 3136])])
    s['crystal'] = finish(crystal, .15)
    retro = at(.13, (0, square(660, .035, .25) * .8), (.03, square(1320, .1, .25) * env(.1, .045, .001)))
    s['retro'] = finish(band(retro, 150, 8000), .15)
    bass = np.tanh(sweep(165, 58, .36, .5) * env(.36, .12, .001) * 3) + band(noise(.36), 0, 3000) * env(.36, .004, 0) * .6
    s['bass'] = finish(bass, .28, .03)
    snap = at(.14, *[(i * .009, band(noise(.1), 900, 4200) * env(.1, .012 + .01 * i, 0) * (1 - .25 * i)) for i in range(3)])
    s['snap'] = finish(snap, .22)
    drop = glide(520, 1600, .028, .1)
    s['drop'] = finish(drop * fit(env(.128, .04, .002), len(drop)), .2)
    wood = partials([620, 1410, 2350, 3900], [1, .6, .35, .15], [.04, .025, .015, .008], .12) + band(noise(.12), 500, 5000) * env(.12, .004, 0) * .7
    s['wood'] = finish(wood, .22, .01)
    t = T(.18)
    zap = band(noise(.18) * (np.sign(np.sin(2 * np.pi * 70 * t)) * .5 + .7), 1500, 9000) * env(.18, .05, .001)
    s['zap'] = finish(zap + np.sin(2 * np.pi * np.cumsum(2600 - 1800 * t / .18) / SR) * env(.18, .04) * .5, .19)
    sparkle = bell(1760, .5) * .6
    pings = [(RNG.uniform(0, .07), partials([RNG.uniform(3500, 7500)], [.6], [.05], .12)) for _ in range(7)]
    s['sparkle'] = finish(at(.5, (0, sparkle), *pings), .17)
    s['double'] = finish(at(1.0, (0, bell(1568, .8)), (.085, bell(2093, .8) * .9)), .16)
    crack = band(noise(.5), 300, 9000) * env(.5, .018, 0) + band(noise(.5), 0, 260) * env(.5, .16, .01) * 1.6
    s['thunder'] = finish(np.tanh(crack * 1.5), .22, .05)
    notes = [523.25, 659.25, 783.99, 1046.5]
    fan = at(.8, *[(i * .075, (square(f, .7, .5, 12) * .35 + partials([f, f * 2], [1, .4], [.3, .2], .7)) * env(.7, .22 if i == 3 else .09, .002)) for i, f in enumerate(notes)])
    s['fanfare'] = finish(band(fan, 150, 9000), .15, .05)
    magic = at(1.0, *[(i * .05, partials([f, f * 1.004, f * 2.01], [1, .7, .25], [.35, .35, .15], .7)) for i, f in enumerate([880, 1108.7, 1318.5, 1760, 2217.5, 2637])])
    shimmer = band(noise(1.0), 5000, 14000) * fit(np.concatenate([np.linspace(0, 1, int(SR * .3)), np.linspace(1, 0, int(SR * .7))]), int(SR * 1.0)) * .05
    s['magic'] = finish(magic + shimmer, .12, .08)
    return s


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in sounds().items():
        path = OUT / f'{name}.ogg'
        sf.write(path, data, SR, format='OGG', subtype='VORBIS')
        print(f'{name:8s} {len(data) / SR:5.2f}s peak {np.max(np.abs(data)):.2f} rms {np.sqrt(np.mean(data[:int(SR * .12)] ** 2)):.3f} {path.stat().st_size} B')
