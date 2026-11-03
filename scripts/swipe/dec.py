import math, numpy as np
from collections import defaultdict
from sim import key_seq

def plen(p):
    return float(np.sum(np.linalg.norm(np.diff(p, axis=0), axis=1))) if len(p) > 1 else 0.0

def resample(p, n):
    if len(p) == 1: return np.repeat(p, n, 0)
    d = np.linalg.norm(np.diff(p, axis=0), axis=1); cum = np.concatenate([[0], np.cumsum(d)])
    if cum[-1] == 0: return np.repeat(p[:1], n, 0)
    t = np.linspace(0, cum[-1], n)
    return np.stack([np.interp(t, cum, p[:,0]), np.interp(t, cum, p[:,1])], 1)

def resample_step(p, step, cap=160):
    L = plen(p); n = int(min(cap, max(2, L / step + 1)))
    return resample(p, n)

class Old:
    """FlorisBoard's statistical classifier (shape + location Gaussians times frequency)."""
    def __init__(self, keys, words):
        self.keys = keys; self.words = words
        self.tree = defaultdict(list); self.ideal = {}
        for w, f in words.items():
            s = key_seq(w, keys)
            if not s: continue
            self.tree[(s[0], s[-1])].append(w)
            self.ideal[w] = np.array([keys[c] for c in s])
    def closest(self, x, y, n):
        return sorted(self.keys, key=lambda c: (self.keys[c][0]-x)**2 + (self.keys[c][1]-y)**2)[:n]
    def norm(self, g):
        mn = g.min(0); mx = g.max(0); side = max((mx-mn).max(), 1e-5)
        return g/side - ((mx-mn)/2 + mn)/side
    def decode(self, path, k=5):
        r = 1.0
        S = 200
        cands = []
        for a in self.closest(*path[0], 2):
            for b in self.closest(*path[-1], 2):
                cands += self.tree.get((a, b), [])
        ul = plen(path); ug = resample(path, S); un = self.norm(ug)
        res = []
        for w in cands:
            ig = self.ideal[w]
            if abs(ul - plen(ig)) >= 8.42 * r: continue
            wg = resample(ig, S); wn = self.norm(wg)
            shape = np.sum(np.linalg.norm(wn - un, axis=1))
            loc = np.sum(np.abs(wg - ug).sum(1)) / S / 2
            sp = math.exp(-0.5 * (shape/22.08)**2) / (22.08*math.sqrt(2*math.pi))
            lp = math.exp(-0.5 * (loc/(0.5109*r))**2) / (0.5109*r*math.sqrt(2*math.pi))
            f = self.words[w]
            conf = sp * lp * f
            res.append((conf, w))
        res.sort(reverse=True)
        return [w for _, w in res[:k]]

class New:
    def __init__(self, keys, words, P):
        self.keys = keys; self.words = words; self.P = P
        self.tree = defaultdict(list); self.ideal = {}
        for w, f in words.items():
            s = key_seq(w, keys)
            if not s: continue
            # consecutive duplicates collapse (the finger stays on the key)
            c = [s[0]] + [x for i, x in enumerate(s[1:], 1) if x != s[i-1]]
            self.tree[(c[0], c[-1])].append(w)
            pts = np.array([keys[x] for x in c], dtype=float)
            self.ideal[w] = (pts, plen(pts))
        self.kc = np.array(list(keys.values())); self.kn = list(keys.keys())
    def near(self, x, y):
        d = np.hypot(self.kc[:,0]-x, self.kc[:,1]-y); o = np.argsort(d)
        lim = d[o[0]] + self.P['near']
        return [self.kn[i] for i in o[:4] if d[i] <= lim]
    def decode(self, path, k=5, bonus=None):
        P = self.P
        ul = plen(path)
        up = resample_step(path, 0.25)
        cands = []
        for a in self.near(*path[0]):
            for b in self.near(*path[-1]):
                cands += self.tree.get((a, b), [])
        res = []
        M = len(up)
        for w in cands:
            pts, il = self.ideal[w]
            if il > 0 and not (0.45*il - 1.0 <= ul <= 1.8*il + 1.5): continue
            L = len(pts)
            # letter visits, in order (monotone alignment of letters to path points)
            d2 = ((pts[:,None,:] - up[None,:,:])**2).sum(2)   # L x M
            c = np.minimum(d2, P['cap'])
            D = np.full(M, np.inf); D[0] = c[0,0]*P['ends']
            if L == 1:
                V = c[0,0]*P['ends'] + c[0,-1]*P['ends']
            else:
                for i in range(1, L):
                    run = np.minimum.accumulate(D)
                    D = c[i] + run
                V = D[-1] + (c[L-1, M-1]*(P['ends']-1))
            # path -> ideal polyline (detours away from the word's keys)
            if L == 1:
                dd = np.hypot(up[:,0]-pts[0,0], up[:,1]-pts[0,1])
            else:
                a = pts[:-1]; b = pts[1:]; ab = b - a; ab2 = (ab**2).sum(1); ab2[ab2 == 0] = 1e-9
                ap = up[:,None,:] - a[None,:,:]
                t = np.clip((ap*ab[None]).sum(2)/ab2[None], 0, 1)
                proj = a[None] + t[:,:,None]*ab[None]
                dd = np.sqrt(((up[:,None,:]-proj)**2).sum(2)).min(1)
            C = float(np.mean(np.minimum(dd**2, P['cap']))) * P['cw']
            lp = 0.0
            if il > 0.3:
                r = ul / il
                lp = ((math.log(max(r, 1e-3)) - P['lmu'])/P['lsd'])**2 * 0.5
            lm = P['kappa'] * self.words[w] / 255.0
            s = -(V/P['vs'] + C + lp) + lm + (bonus(w) if bonus else 0)
            res.append((s, w))
        res.sort(reverse=True)
        out = []
        for _, w in res:
            if w not in out: out.append(w)
            if len(out) >= k: break
        return out
