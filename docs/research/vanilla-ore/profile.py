# Expected ore blocks per chunk per Y layer, vanilla 26.1.2 overworld placed features, assuming solid rock
# (no caves, no aquifers, no air exposure discard). Y range -64..319.
import json, sys, collections
mc, vs_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
vs = json.load(open(vs_path))
MIN, MAX = -64, 319
def resolve(a):
    if 'absolute' in a: return a['absolute']
    if 'above_bottom' in a: return MIN + a['above_bottom']
    if 'below_top' in a: return MAX - a['below_top']
def origin_dist(h):
    lo, hi = resolve(h['min_inclusive']), resolve(h['max_inclusive'])
    if h['type'] == 'minecraft:uniform':
        return {y: 1/(hi-lo+1) for y in range(lo, hi+1)}
    k = hi - lo; plateau = h.get('plateau', 0)
    if plateau >= k: return {y: 1/(hi-lo+1) for y in range(lo, hi+1)}
    l = (k - plateau)//2; m = k - l
    d = collections.Counter()
    for a in range(m+1):
        for b in range(l+1): d[lo+a+b] += 1/((m+1)*(l+1))
    return d
FEATURES = {  # placed feature -> (ore, configured size, biomes)
 'ore_coal_upper':('coal',17,'all'),'ore_coal_lower':('coal',17,'all'),
 'ore_iron_upper':('iron',9,'all'),'ore_iron_middle':('iron',9,'all'),'ore_iron_small':('iron',4,'all'),
 'ore_copper':('copper',10,'all except dripstone caves'),'ore_copper_large':('copper',20,'dripstone caves only'),
 'ore_gold':('gold',9,'all'),'ore_gold_lower':('gold',9,'all'),'ore_gold_extra':('gold',9,'badlands only'),
 'ore_redstone':('redstone',8,'all'),'ore_redstone_lower':('redstone',8,'all'),
 'ore_lapis':('lapis',7,'all'),'ore_lapis_buried':('lapis',7,'all'),
 'ore_diamond':('diamond',4,'all'),'ore_diamond_medium':('diamond',8,'all'),'ore_diamond_large':('diamond',12,'all'),'ore_diamond_buried':('diamond',8,'all'),
 'ore_emerald':('emerald',3,'mountain biomes only'),
 'ore_gravel':('gravel',33,'all'),'ore_clay':('clay',33,'lush caves only'),
}
res = {}
for pf,(ore,size,biome) in FEATURES.items():
    p = json.load(open(f"{mc}/p/{pf}.json"))['placement']
    count = 1.0
    for st in p:
        if st['type']=='minecraft:count':
            c = st['count']; count = c if isinstance(c,(int,float)) else (c['min_inclusive']+c['max_inclusive'])/2
        if st['type']=='minecraft:rarity_filter': count = 1/st['chance']
        if st['type']=='minecraft:height_range': od = origin_dist(st['height']); hdesc = st['height']
    v = vs[str(size)]; layer = collections.Counter()
    for y0, py in od.items():
        for off, n in v['offsets'].items():
            y = y0 + int(off)
            if MIN <= y <= MAX: layer[y] += count*py*n
    res[pf] = {'ore':ore,'size':size,'biome':biome,'count':count,'height':hdesc,
               'range':[min(od),max(od)],'blocks_per_vein':v['mean'],
               'blocks_per_chunk':sum(layer.values()),'layers':dict(layer)}
    print(f"{pf:22s} {ore:8s} size {size:2d} count {count:6.3f} range {min(od):5d}..{max(od):4d} blk/vein {v['mean']:6.2f} blk/chunk {sum(layer.values()):7.1f}  {biome}")
json.dump(res, open(out_path,'w'))
