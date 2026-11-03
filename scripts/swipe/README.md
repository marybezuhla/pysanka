# Swipe tuning

How the weights in `ua/pysanka/typing/swipe/SwipeDecoder.kt` were found. Nothing here runs in the
app or the build; it needs Python 3 and numpy.

- `sim.py`: simulated swipes over the Ukrainian and English layouts: every key target missed by a
  random amount (`NOISE`, in key widths), corners rounded (`CUT`), uneven speed, small jitter.
- `dec.py`: FlorisBoard's own classifier (shape and location times frequency), for comparison.
- `feat.py`: Pysanka's six measures for every candidate word of each simulated swipe.
- `tune.py`: a random search for the weights on one set of swipes, checked on another.
- `bench2.py`: both decoders on the same swipes.

```sh
cd scripts/swipe
NOISE=0.38 CUT=0.9 python3 feat.py uk 300 1 train.pkl
NOISE=0.38 CUT=0.9 python3 feat.py uk 300 2 test.pkl
python3 tune.py train.pkl test.pkl
NOISE=0.38 CUT=0.9 python3 bench2.py uk 150 '[44.354,11.68,3.077,2.349,85.448,149.674,-0.122,26.94]'
```

Results (right word first / among the first three, 150 words from the 8000 most common):

| Swipes | FlorisBoard | Pysanka |
|---|---|---|
| Ukrainian, careful | 89 % / 98 % | 96 % / 98 % |
| Ukrainian, ordinary | 75 % / 84 % | 90 % / 97 % |
| Ukrainian, hasty | 59 % / 70 % | 78 % / 85 % |
| English, ordinary | 54 % / 72 % | 69 % / 84 % |
