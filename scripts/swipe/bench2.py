import random, sys, json, numpy as np
from sim import *
from dec import Old
from feat import Feat
from tune import score
P = json.loads(sys.argv[3])
lang = sys.argv[1]; n = int(sys.argv[2])
d = load(lang); keys = KEYS[lang]
words = {w: f for w, f in d.items() if key_seq(w, keys) and len(key_seq(w,keys)) == len([c for c in w.lower() if c.isalpha()])}
top = sorted(words.items(), key=lambda kv: -kv[1])[:8000]
rng = random.Random(99); sample = rng.sample([w for w, f in top if len(w) >= 2], n)
old = Old(keys, words); F = Feat(keys, words)
r2 = random.Random(5); o1=o3=n1=n3=0
for w in sample:
    p = simulate(w, keys, r2)
    ro = old.decode(p, 3); o1 += bool(ro) and ro[0]==w; o3 += w in ro
    fs = F.features(p)
    if fs:
        X = np.array([f[1:] for f in fs], float); s = score(X, P); o = np.argsort(-s)
        rn = [fs[i][0] for i in o[:3]]; n1 += rn[0]==w; n3 += w in rn
print(f"{lang} old top1 {o1/n:.3f} top3 {o3/n:.3f} | new top1 {n1/n:.3f} top3 {n3/n:.3f}")
