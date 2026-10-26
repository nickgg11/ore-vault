import json, sys
res = json.load(open(sys.argv[1])); L = json.load(open(sys.argv[2]))
print("veins attempted per chunk with origin in -64..63, and blocks landing in -64..63 (plains, solid rock)")
for pf, r in res.items():
    lo, hi = r['range']; span = hi - lo + 1
    # fraction of origins inside -64..63 (approx, uniform/trapezoid ignored -> recompute from layers share)
    blocks = sum(v for y, v in r['layers'].items() if -64 <= int(y) <= 63)
    print(f"{pf:22s} veins~{blocks / r['blocks_per_vein'] if r['blocks_per_vein'] else 0:6.1f}  blocks {blocks:7.1f}")
print("\npeak single-layer density per ore (plains)")
for o, d in L.items():
    y = max(d, key=lambda k: d[k]); print(f"{o:8s} Y={y:>4s} {d[y]:6.2f} blocks/layer = {100*d[y]/256:.2f}%  | best 16-layer window:", end=" ")
    best = max(range(-64, 305), key=lambda s: sum(d[str(t)] for t in range(s, s+16)))
    tot = sum(d[str(t)] for t in range(best, best+16)); print(f"Y{best}..{best+15} {100*tot/4096:.2f}%")
print("\ngravel per band:", {f"{a}..{b}": round(sum(L['gravel'][str(y)] for y in range(a,b+1)),1) for a,b in [(-64,-1),(0,63),(64,319)]})
