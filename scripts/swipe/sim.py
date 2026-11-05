import json, math, pathlib, random, unicodedata, sys
import numpy as np

UK_ROWS = ["йцукенгшщзхї", "фівапролджє", "ячсмитьбю"]
EN_ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
RU_ROWS = ["йцукенгшщзх", "фывапролджэ", "ячсмитьбю"]
BE_ROWS = ["йцукенгшўзх", "фывапролджэ", "ячсмітьбю"]
QWERTZ_ROWS = ["qwertzuiop", "asdfghjkl", "yxcvbnm"]
AZERTY_ROWS = ["azertyuiop", "qsdfghjklm", "wxcvbn"]
HU_ROWS = ["qwertzuiopö", "asdfghjkléá", "yxcvbnmü"]
ES_ROWS = ["qwertyuiop", "asdfghjklñ", "zxcvbnm"]
H = 1.35  # key height / width
def layout(rows, offsets):
    keys = {}
    for r, (row, off) in enumerate(zip(rows, offsets)):
        for i, c in enumerate(row):
            keys[c] = (off + i + 0.5, r * H + H / 2)
    return keys
UK = layout(UK_ROWS, [0, 0.5, 1.5])
EN = layout(EN_ROWS, [0, 0.5, 1.5])
RU = layout(RU_ROWS, [0, 0.5, 1.5])
QZ = layout(QWERTZ_ROWS, [0, 0.5, 1.5])
KEYS = {"uk": UK, "en": EN, "ru": RU, "be": layout(BE_ROWS, [0, 0.5, 1.5]), "pl": EN, "ro": EN, "pt": EN,
        "es": layout(ES_ROWS, [0, 0.5, 1.5]), "de": QZ, "sk": QZ, "hu": layout(HU_ROWS, [0, 0.5, 1.5]),
        "fr": layout(AZERTY_ROWS, [0, 0.5, 1.5])}

def load(lang):
    d = json.load(open(pathlib.Path(__file__).resolve().parents[2] / f"app/src/main/assets/ime/dict/{lang}.json", encoding="utf-8"))
    return d

def key_seq(word, keys):
    seq = []
    for ch in word.lower():
        c = ch if ch in keys else {"ґ": "г", "ъ": "ь", "ł": "l", "ß": "s", "æ": "a", "œ": "o", "ő": "ö", "ű": "ü"}.get(ch, unicodedata.normalize("NFD", ch)[0])
        if c in keys:
            seq.append(c)
    return seq

def catmull(pts, n=12):
    out = []
    P = [pts[0]] + pts + [pts[-1]]
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = map(np.array, (P[i-1], P[i], P[i+1], P[i+2]))
        for t in np.linspace(0, 1, n, endpoint=False):
            t2, t3 = t*t, t*t*t
            out.append(0.5*((2*p1) + (-p0+p2)*t + (2*p0-5*p1+4*p2-p3)*t2 + (-p0+3*p1-3*p2+p3)*t3))
    out.append(np.array(P[-2]))
    return np.array(out)

import os
NOISE=float(os.environ.get("NOISE","0.27")); CUT=float(os.environ.get("CUT","0.55"))
def simulate(word, keys, rng, noise=None, cut=None, jitter=0.05):
    noise = NOISE if noise is None else noise; cut = CUT if cut is None else cut
    seq = key_seq(word, keys)
    tg = []
    for i, c in enumerate(seq):
        x, y = keys[c]
        x += rng.gauss(0, noise); y += rng.gauss(0, noise * 0.9)
        if tg and seq[i-1] == c:
            # a little wiggle for a double letter, or nothing
            tg.append((x + 0.15, y + 0.1)); 
        tg.append((x, y))
    if len(tg) == 1:
        tg = [tg[0], (tg[0][0] + 0.05, tg[0][1] + 0.05)]
    path = catmull(tg, 14)
    # corner cutting: moving average
    k = max(1, int(cut * 14))
    if k > 1 and len(path) > k:
        ker = np.ones(k) / k
        xs = np.convolve(np.pad(path[:,0], (k//2, k-1-k//2), mode="edge"), ker, mode="valid")
        ys = np.convolve(np.pad(path[:,1], (k//2, k-1-k//2), mode="edge"), ker, mode="valid")
        path = np.stack([xs, ys], 1)
    # irregular sampling (speed varies): keep every point with probability
    keep = [0] + [i for i in range(1, len(path)-1) if rng.random() < 0.5] + [len(path)-1]
    path = path[keep]
    path = path + np.array([[rng.gauss(0, jitter), rng.gauss(0, jitter)] for _ in path])
    return path
