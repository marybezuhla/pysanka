import pickle, sys, random, numpy as np
def prep(path):
    data = pickle.load(open(path, "rb")); out = []
    for w, feats in data:
        if not feats: out.append((None, None)); continue
        ws = [f[0] for f in feats]
        X = np.array([f[1:] for f in feats], dtype=float)
        out.append((ws.index(w) if w in ws else -1, X))
    return out
def score(X, p):
    shape, loc, V, ends, C, lr, L, f = X.T
    return -(p[0]*shape**2 + p[1]*loc**2 + p[2]*V + p[3]*ends + p[4]*C + p[5]*(lr - p[6])**2) + p[7]*f/255
def acc(data, p):
    t1 = t3 = 0
    for i, X in data:
        if X is None or i < 0: continue
        s = score(X, p); o = np.argsort(-s)
        t1 += o[0] == i; t3 += i in o[:3]
    return t1/len(data), t3/len(data)
if __name__ == "__main__":
    tr = prep(sys.argv[1]); te = prep(sys.argv[2])
    reach = sum(i >= 0 for i, X in te if X is not None)/len(te)
    print("in candidates (test):", reach)
    rng = random.Random(1)
    best = None
    base = [20, 2, 1, 1, 3, 4, 0.05, 7]
    for it in range(3000):
        if best is None or rng.random() < 0.3:
            p = [b*np.exp(rng.gauss(0, 1.0)) for b in base]; p[6] = rng.uniform(-0.1, 0.3)
        else:
            p = [b*np.exp(rng.gauss(0, 0.3)) for b in best[1]]; p[6] = best[1][6] + rng.gauss(0, 0.05)
        a = acc(tr, p)
        if best is None or a > best[0]:
            best = (a, p); print(it, a, [round(x, 3) for x in p], flush=True)
    print("test:", acc(te, best[1]))
