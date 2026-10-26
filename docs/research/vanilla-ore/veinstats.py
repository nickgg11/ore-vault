import sys, random, statistics, collections, json
sys.path.insert(0, sys.argv[1])
from orefeature import vein
rnd = random.Random(1)
out = {}
for size in [3,4,7,8,9,10,12,17,20,33,64]:
    N = 20000
    counts = []; off = collections.Counter()
    for _ in range(N):
        ys = vein(size, rnd); counts.append(len(ys)); off.update(ys)
    cs = sorted(counts)
    out[size] = {"mean": statistics.mean(counts), "p10": cs[N//10], "median": cs[N//2], "p90": cs[9*N//10], "max": cs[-1],
                 "offsets": {k: v / N for k, v in sorted(off.items())}}
    print(size, round(out[size]["mean"],2), cs[N//10], cs[N//2], cs[9*N//10], cs[-1], min(off), max(off))
json.dump(out, open(sys.argv[2], "w"))
