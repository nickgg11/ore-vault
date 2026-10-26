import json, sys, collections
res = json.load(open(sys.argv[1]))
ORES = ['coal','iron','copper','gold','redstone','lapis','diamond','emerald']
def layers(biome_set):
    L = collections.defaultdict(collections.Counter)
    for pf, r in res.items():
        b = r['biome']
        ok = b == 'all' or (b == 'all except dripstone caves' and 'dripstone' not in biome_set) or \
             (b == 'dripstone caves only' and 'dripstone' in biome_set) or (b == 'badlands only' and 'badlands' in biome_set) or \
             (b == 'mountain biomes only' and 'mountain' in biome_set) or (b == 'lush caves only' and 'lush' in biome_set)
        if ok:
            for y, n in r['layers'].items(): L[r['ore']][int(y)] += n
    return L
bands = [(-64,-1),(0,31),(32,63),(64,127),(128,319),(-64,63)]
for name, bs in [('plains (default biome)', set()), ('mountain', {'mountain'}), ('badlands', {'badlands'}), ('dripstone caves', {'dripstone'})]:
    L = layers(bs)
    print(f"\n## {name}: ore blocks per chunk by band, solid rock")
    print("band        " + " ".join(f"{o:>8s}" for o in ORES) + "    total  density")
    for lo, hi in bands:
        row = [sum(L[o][y] for y in range(lo, hi+1)) for o in ORES]
        tot = sum(row); vol = 256*(hi-lo+1)
        print(f"{lo:4d}..{hi:4d}  " + " ".join(f"{x:8.1f}" for x in row) + f"  {tot:7.1f}  {100*tot/vol:5.2f}%")
    # peak Y per ore
    print("peak Y: " + ", ".join(f"{o} {max(L[o], key=L[o].get) if L[o] else '-'}" for o in ORES))
    if name.startswith('plains'):
        json.dump({o: {str(y): L[o][y] for y in range(-64,320)} for o in ORES+['gravel']}, open(sys.argv[2],'w'))
