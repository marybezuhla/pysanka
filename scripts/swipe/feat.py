import math, random, sys, pickle, numpy as np
from collections import defaultdict
from sim import *
from dec import plen, resample, resample_step

class Feat:
    def __init__(self, keys, words):
        self.keys = keys; self.words = words
        self.tree = defaultdict(list); self.ideal = {}
        for w, f in words.items():
            s = key_seq(w, keys)
            if not s: continue
            c = [s[0]] + [x for i, x in enumerate(s[1:], 1) if x != s[i-1]]
            self.tree[(c[0], c[-1])].append(w)
            pts = np.array([keys[x] for x in c], dtype=float)
            self.ideal[w] = (pts, plen(pts))
        self.kc = np.array(list(keys.values())); self.kn = list(keys.keys())
    def near(self, x, y, extra=1.0, most=4):
        d = np.hypot(self.kc[:,0]-x, self.kc[:,1]-y); o = np.argsort(d)
        return [self.kn[i] for i in o[:most] if d[i] <= d[o[0]] + extra]
    @staticmethod
    def norm(g):
        mn = g.min(0); mx = g.max(0); side = max((mx-mn).max(), 1e-5)
        return g/side - ((mx-mn)/2 + mn)/side
    def features(self, path):
        S = 64
        ul = plen(path); ug = resample(path, S); un = self.norm(ug)
        up = resample_step(path, 0.25)
        M = len(up)
        out = []
        cands = set()
        for a in self.near(*path[0]):
            for b in self.near(*path[-1]):
                cands.update(self.tree.get((a, b), []))
        for w in cands:
            pts, il = self.ideal[w]
            if il > 0 and not (0.4*il - 1.2 <= ul <= 2.0*il + 1.5): continue
            L = len(pts)
            wg = resample(pts, S); wn = self.norm(wg)
            shape = float(np.mean(np.linalg.norm(wn - un, axis=1)))
            loc = float(np.mean(np.linalg.norm(wg - ug, axis=1)))
            d2 = ((pts[:,None,:] - up[None,:,:])**2).sum(2)
            c = np.minimum(d2, 4.0)
            if L == 1:
                V = c[0,0] + c[0,-1]
            else:
                D = np.full(M, np.inf); D[0] = c[0,0]
                for i in range(1, L):
                    D = c[i] + np.minimum.accumulate(D)
                V = float(D[-1])
            ends = float(c[0,0] + c[L-1, M-1])
            if L == 1:
                dd = np.hypot(up[:,0]-pts[0,0], up[:,1]-pts[0,1])
            else:
                a = pts[:-1]; b = pts[1:]; ab = b - a; ab2 = (ab**2).sum(1); ab2[ab2 == 0] = 1e-9
                ap = up[:,None,:] - a[None,:,:]
                t = np.clip((ap*ab[None]).sum(2)/ab2[None], 0, 1)
                proj = a[None] + t[:,:,None]*ab[None]
                dd = np.sqrt(((up[:,None,:]-proj)**2).sum(2)).min(1)
            C = float(np.mean(np.minimum(dd**2, 4.0)))
            lr = math.log(max(ul,1e-3)/il) if il > 0.3 else 0.0
            out.append((w, shape, loc, V, ends, C, lr, L, self.words[w]))
        return out

if __name__ == "__main__":
    lang, n, seed, outp = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    d = load(lang); keys = KEYS[lang]
    words = {w: f for w, f in d.items() if key_seq(w, keys) and len(key_seq(w,keys)) == len([c for c in w.lower() if c.isalpha()])}
    top = sorted(words.items(), key=lambda kv: -kv[1])[:8000]
    rng = random.Random(seed)
    sample = rng.sample([w for w, f in top if len(w) >= 2], n)
    F = Feat(keys, words); rng2 = random.Random(seed + 100); data = []
    for w in sample:
        data.append((w, F.features(simulate(w, keys, rng2))))
    pickle.dump(data, open(outp, "wb"))
    print("done", len(data), np.mean([len(f) for _, f in data]))
