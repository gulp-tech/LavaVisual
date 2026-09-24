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


def sat(x, drive=2.0):
    """Soft saturation: dense, loud transients without hard clipping."""
    return np.tanh(x * drive) / np.tanh(drive)


def room(x, d=0.08, mix=0.12, lo=250, hi=7500):
    """A very short diffuse room so hits feel solid instead of dry clicks."""
    n = int(SR * d)
    t = np.arange(n) / SR
    ir = band(RNG.standard_normal(n), lo, hi) * np.exp(-t / (d / 5))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x)]
    return x + mix * wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x))


def crackle(d, count, spread, lo=2500, hi=10000, tau=0.0025, amp=1.0):
    out = np.zeros(int(SR * d))
    for _ in range(count):
        i = int(SR * RNG.uniform(0, spread))
        k = band(RNG.standard_normal(int(SR * .02)), lo, hi) * env(.02, tau, 0) * RNG.uniform(.4, 1) * amp
        n = min(len(k), len(out) - i)
        out[i:i + n] += k[:n]
    return out


def rich():
    """2.11: saturated hits and crits - sub thump, body knock, noise smack and crack, soft saturation, short room."""
    global RNG
    RNG = np.random.default_rng(2611)
    s = {}
    d = .26
    x = sweep(175, 46, d, .55) * env(d, .07, .0006) * 1.15 + sweep(560, 190, d, .35) * env(d, .028, .0004) * .5 \
        + band(noise(d), 650, 3200) * env(d, .02, 0) * .85 + band(noise(d), 3800, 12000) * env(d, .005, 0) * .55
    s['juicy'] = finish(room(sat(x, 2.6), .07, .12), .34, .02)
    d = .32
    x = sweep(128, 38, d, .75) * env(d, .1, .0008) * 1.25 + sweep(330, 115, d, .3) * env(d, .024, .0004) * .7 \
        + band(noise(d), 280, 2300) * env(d, .03, 0) * .95 + band(noise(d), 2500, 9000) * env(d, .007, 0) * .45
    s['power'] = finish(room(sat(x, 3.2), .09, .1), .36, .03)
    d = .2
    x = band(noise(d), 1700, 9800) * env(d, .013, .0003) + partials([930, 1860, 2790], [1, .45, .2], [.032, .018, .01], d, .0005) * .5 \
        + sweep(210, 72, d, .45) * env(d, .032, .0006) * .7
    s['whip'] = finish(room(sat(x, 2.2), .06, .1), .3, .015)
    d = .36
    x = sweep(108, 34, d, .85) * env(d, .13, .0015) * 1.35 + band(noise(d), 60, 850) * env(d, .03, 0) * .95 \
        + band(noise(d), 4200, 11000) * env(d, .0028, 0) * .4
    s['boom'] = finish(sat(x, 2.8), .36, .04)
    d = .62
    t = T(d)
    hit = sat(sweep(215, 58, d, .5) * env(d, .06, .0008) + band(noise(d), 1400, 7000) * env(d, .012, 0) * .75, 2.4)
    shing = partials([1870, 2995, 4410, 6120, 8150], [1, .8, .55, .35, .2], [.34, .26, .18, .12, .07], d, .002) * .5
    swoosh = band(noise(d), 4000, 13000) * np.minimum(1, t / .025) * np.exp(-np.maximum(0, t - .025) / .06) * .3
    s['blade'] = finish(room(hit + shing + swoosh, .14, .16), .28, .06)
    d = .7
    x = sweep(150, 36, d, .8) * env(d, .15, .0008) * 1.2 + band(noise(d), 180, 6000) * env(d, .065, .0005) * .9 \
        + crackle(d, 14, .28, 2200, 10000, .003, .8) * .6
    spark = at(d, (.015, exp_sweep(1700, 5400, .22) * env(.22, .07, .002) * .16))
    s['burst'] = finish(sat(x, 2.8) + spark, .32, .08)
    d = .56
    t = T(d)
    zap = band(noise(d) * (np.sign(np.sin(2 * np.pi * 95 * t)) * .5 + .7), 1500, 9500) * env(d, .085, .0008)
    fall = np.sin(2 * np.pi * np.cumsum(3300 * (650 / 3300) ** np.minimum(1, t / .16)) / SR) * env(d, .09, .001) * .45
    x = sweep(185, 50, d, .55) * env(d, .065, .0008) * 1.1 + zap + crackle(d, 10, .12, 2500, 11000, .002, .9)
    s['storm'] = finish(room(sat(x, 2.5) + fall, .1, .12), .3, .06)
    d = .82
    punch = sat(sweep(195, 54, d, .55) * env(d, .065, .0008) + band(noise(d), 800, 5200) * env(d, .014, 0) * .65, 2.3)
    chord = at(d, *[(i * .018, partials([f, f * 2.0, f * 3.01], [1, .3, .12], [.42 - i * .04, .2, .1], d - i * .018, .002))
                    for i, f in enumerate([1046.5, 1318.5, 1568, 2093])]) * .32
    shimmer = band(noise(d), 6000, 15000) * env(d, .16, .012) * .07
    s['radiant'] = finish(punch + chord + shimmer, .26, .08)
    return s


if __name__ == '__main__':
    import sys
    OUT.mkdir(parents=True, exist_ok=True)
    # --new writes only the 2.11 sounds and keeps the older files byte-identical.
    batch = rich() if sys.argv[1:] == ['--new'] else {**sounds(), **rich()}
    for name, data in batch.items():
        path = OUT / f'{name}.ogg'
        sf.write(path, data, SR, format='OGG', subtype='VORBIS')
        print(f'{name:8s} {len(data) / SR:5.2f}s peak {np.max(np.abs(data)):.2f} rms {np.sqrt(np.mean(data[:int(SR * .12)] ** 2)):.3f} {path.stat().st_size} B')
