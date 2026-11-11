"""Real swipes from the app's swipe practice (Settings → Typing → Swipe practice → Save to a file).

Each line of the file: the word asked, the decoder's guesses in the app, the swipe and the key centres,
all in key widths. This turns them into the same measures as feat.py, split in two halves (even and
odd lines), so tune.py can tune on one and check on the other; it also says how often the app's own
first guess was right.

    python3 real.py pysanka-swipes.jsonl train.pkl test.pkl
    python3 tune.py train.pkl test.pkl
"""
import json, pickle, sys, numpy as np
from sim import load, key_seq
from feat import Feat

CURRENT = [67.947, 1.344, 3.248, 0.598, 5.612, 14.458, -0.045, 9.853]


def main(src, train_out, test_out):
    lines = [json.loads(l) for l in open(src, encoding="utf-8") if l.strip()]
    lines = [l for l in lines if l.get("lang", "uk") == "uk"]
    d = load("uk")
    feats = {}
    data = [[], []]
    app_right = app_any = fallback = 0
    for i, l in enumerate(lines):
        keys = {c: (float(p[0]), float(p[1])) for c, p in l["keys"].items()}
        layout = tuple(sorted(keys.items()))
        if layout not in feats:
            words = {w: f for w, f in d.items() if key_seq(w, keys)}
            feats[layout] = Feat(keys, words)
        path = np.array(list(zip(l["xs"], l["ys"])), dtype=float)
        if len(path) < 2:
            continue
        data[i % 2].append((l["word"], feats[layout].features(path)))
        # The app's own guesses, only where Pysanka's decoder answered (not the fallback)
        if not l.get("decoder", True):
            fallback += 1
            continue
        g = l.get("guesses") or []
        app_right += bool(g) and g[0].lower() == l["word"].lower()
        app_any += l["word"].lower() in [x.lower() for x in g[:3]]
    n = max(len(lines) - fallback, 1)
    print(f"swipes: {len(lines)}; the app's first guess right: {app_right / n:.0%}, among its first three: {app_any / n:.0%}"
          + (f"; {fallback} answered by FlorisBoard's fallback" if fallback else ""))
    pickle.dump(data[0], open(train_out, "wb"))
    pickle.dump(data[1], open(test_out, "wb"))
    # The current weights on these swipes (as tune.py measures them)
    from tune import prep, acc
    both = prep(train_out) + prep(test_out)
    reach = sum(i >= 0 for i, X in both if X is not None) / max(len(both), 1)
    print(f"the right word among the candidates at all: {reach:.0%}")
    print("current weights, first / among three:", acc(both, CURRENT))


if __name__ == "__main__":
    main(*sys.argv[1:4])
