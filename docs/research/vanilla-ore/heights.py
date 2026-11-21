# "Each ore picks its own height" (Nick, playtest of #422, 2026-11-21), modelled per 16-layer slab for the Vault
# layouts under discussion. Derived, unplaytested: closed form from the baseline counts and the repo's port of vanilla's
# OreFeature (orefeature.py), no placer run, no overlap except where stated.
#
# The rule modelled: a vein's rarity comes from the band's count (OreBaseline, unchanged); its ore is drawn from that
# rarity's ores in proportion to weight x the ore's mapped profile mass inside the band; its box middle layer is drawn
# from that ore's mapped profile inside the band (folded mass, where a layout folds, uniform over the lowest slab); the
# box may overhang the band and only blocks inside are placed; each rarity's count is raised by its own edge factor so
# the band's closed-form blocks are unchanged.
#
# Usage: python3 -I heights.py [--quick]     (run from anywhere; imports orefeature.py beside this file)
import math, os, random, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from orefeature import vein

QUICK = '--quick' in sys.argv
CHUNKS = 81   # the S12 run

# --- Vanilla 26.1.2, the same numbers as src/test/.../core/ore/VanillaOres.java (FEATURES, BIOME_SHARE) -------------
FLOOR, LAYERS = -64, 384
U, T = 'uniform', 'triangle'
FEATURES = [  # ore, shape, low, high, expected blocks a chunk
    ('coal', U, 136, 320, 664.6), ('coal', T, 0, 192, 443.0),
    ('iron', T, 80, 384, 558.0), ('iron', T, -24, 56, 67.9), ('iron', U, -64, 72, 19.7),
    ('copper', T, -16, 112, 134.3),
    ('gold', T, -64, 32, 27.2), ('gold', U, -64, -48, 3.1),
    ('redstone', U, -64, 15, 22.4), ('redstone', T, -96, -32, 21.4),
    ('lapis', T, -32, 32, 9.9), ('lapis', U, -64, 64, 19.6),
    ('diamond', T, -144, 16, 6.8), ('diamond', U, -64, -4, 11.1), ('diamond', T, -144, 16, 0.6), ('diamond', T, -144, 16, 11.1),
    ('emerald', T, -16, 480, 36.1)]
BIOME_SHARE = {'emerald': 0.18}
RARITY = {'coal': 'C', 'iron': 'C', 'copper': 'C', 'gold': 'U', 'redstone': 'U', 'lapis': 'U', 'diamond': 'R', 'emerald': 'R'}
ORES = list(RARITY)
RES = {'C': 2.0, 'U': 5.0, 'R': 12.5}  # §4.2 base Resonance

def profiles():
    mass, weight = collections.defaultdict(lambda: [0.0] * LAYERS), collections.Counter()
    for ore, shape, lo, hi, blocks in FEATURES:
        s = [0.0] * LAYERS
        for y in range(max(lo, FLOOR), min(hi, FLOOR + LAYERS - 1) + 1):
            s[y - FLOOR] = 1.0 if shape == U else min(y - lo + 1, hi - y + 1)
        tot = sum(s)
        for i in range(LAYERS):
            mass[ore][i] += blocks * s[i] / tot
        weight[ore] += blocks * BIOME_SHARE.get(ore, 1.0)
    return {o: [m / sum(v) for m in v] for o, v in mass.items()}, weight
PROFILE, WEIGHT = profiles()

# --- The baseline (OreBaseline.BASE, VeinShapeTables.MEAN_BLOCKS) -------------------------------------------------
MEAN = [0, 0, 0, 0.450, 1.999, 3.135, 4.054, 4.969, 5.677, 6.798, 8.387, 10.035, 11.632]
SIZES = {'C': range(9, 13), 'U': range(7, 10), 'R': range(5, 8)}
COUNTS = {'stone': {'C': 141, 'U': 82, 'R': 33}, 'deepslate': {'C': 65, 'U': 52, 'R': 25}}
BASE_LAYERS = {'stone': 245, 'deepslate': 63}
def mean_blocks(r):
    return sum(MEAN[s] for s in SIZES[r]) / len(SIZES[r])
def band_blocks(band, layers):
    """Closed-form blocks a chunk per rarity, the density held: counts scale with the band's height."""
    k = layers / BASE_LAYERS[band]
    return {r: COUNTS[band][r] * mean_blocks(r) * k for r in 'CUR'}

# --- Vertical spread of a vein about its box middle, from the port ------------------------------------------------
B2 = {}   # E[blocks^2] a vein per rarity, empty veins included, for the per-slab spread

def kernels():
    rnd, out = random.Random(7), {}
    n = 1500 if QUICK else 6000
    for r, sizes in SIZES.items():
        k = collections.Counter(); tot = 0; sq = 0; nv = 0
        for size in sizes:
            for _ in range(n):
                ys = vein(size, rnd)
                sq += len(ys) ** 2; nv += 1
                if not ys:
                    continue
                lo, hi = min(ys), max(ys); mid = lo + (hi - lo) // 2
                for y in ys:
                    k[y - mid] += 1
                tot += len(ys)
        out[r] = {o: c / tot for o, c in k.items()}
        B2[r] = sq / nv
    return out
KERNEL = kernels()

# --- Mapping a profile onto a band --------------------------------------------------------------------------------
def mapped(ore, col_floor, col_top, b_floor, b_top, fold_below, fold_above=False):
    """Mass per band layer (index 0 = b_floor), linear map of Y-64..319 onto col_floor..col_top, exact overlap.
    Folded mass is spread evenly over the lowest (or top) 16 layers. Returns (per-layer mass, in-band total)."""
    n = b_top - b_floor + 1
    out = [0.0] * n
    scale = (col_top + 1.0 - col_floor) / LAYERS
    below = above = 0.0
    for i, m in enumerate(PROFILE[ore]):
        if m == 0:
            continue
        a = col_floor + i * scale; b = a + scale; d = m / scale
        if a < b_floor:
            below += d * (min(b, b_floor) - a)
        if b > b_top + 1:
            above += d * (b - max(a, b_top + 1))
        for y in range(max(int(math.floor(a)), b_floor), min(int(math.ceil(b)), b_top + 1)):
            ov = min(b, y + 1) - max(a, y)
            if ov > 0:
                out[y - b_floor] += d * ov
    if fold_below and below > 0:
        for j in range(min(16, n)):
            out[j] += below / min(16, n)
    if fold_above and above > 0:
        for j in range(max(0, n - 16), n):
            out[j] += above / min(16, n)
    return out, sum(out)

def slabs(b_floor, b_top):
    n = b_top - b_floor + 1; c = max(1, n // 16)
    return [(b_floor + 16 * s, b_top if s == c - 1 else b_floor + 16 * s + 15) for s in range(c)]

def model(name, col, b_floor, b_top, band, fold_below=False, fold_above=False):
    n = b_top - b_floor + 1
    blocks = band_blocks(band, n)
    per_ore = {}       # ore -> placed blocks a chunk per band layer
    pick, keptf = {}, {}
    for r in 'CUR':
        ores = [o for o in ORES if RARITY[o] == r]
        maps = {o: mapped(o, col[0], col[1], b_floor, b_top, fold_below, fold_above) for o in ores}
        tot = sum(WEIGHT[o] * maps[o][1] for o in ores)
        if tot == 0:  # no ore of the rarity maps into the band: by weight alone, flat (today's fallback)
            maps = {o: ([1.0 / n] * n, 1.0) for o in ores}; tot = sum(WEIGHT[o] for o in ores)
        raw = {}
        for o in ores:
            p = WEIGHT[o] * maps[o][1] / tot
            pick[o] = p
            prof = [m / maps[o][1] for m in maps[o][0]] if maps[o][1] else [0] * n
            lay = [0.0] * n
            for j, m in enumerate(prof):
                if m:
                    for off, f in KERNEL[r].items():
                        if 0 <= j + off < n:
                            lay[j + off] += m * f
            keptf[o] = sum(lay)
            raw[o] = [p * x for x in lay]
        kept = sum(sum(v) for v in raw.values())   # blocks kept per unit of requested blocks: 1/edge factor
        for o in ores:
            per_ore[o] = [x * blocks[r] / kept for x in raw[o]]
        keptf['edge_' + r] = 1 / kept
    vol = 256
    rows = []
    for lo, hi in slabs(b_floor, b_top):
        L = hi - lo + 1
        ob = {o: sum(per_ore[o][y - b_floor] for y in range(lo, hi + 1)) for o in ORES}
        rb = {r: sum(v for o, v in ob.items() if RARITY[o] == r) for r in 'CUR'}
        t = sum(rb.values())
        res = sum(rb[r] * RES[r] for r in 'CUR') / t if t else 0
        var = sum(rb[r] / mean_blocks(r) * B2[r] for r in 'CUR') * CHUNKS
        sd = math.sqrt(var) / (t * CHUNKS) if t else 0
        rows.append((lo, hi, t / (vol * L), {r: rb[r] / t if t else 0 for r in 'CUR'}, res, ob, sd))
    tot_blocks = sum(blocks.values())
    avg_d = tot_blocks / (vol * n)
    avg_res = sum(blocks[r] * RES[r] for r in 'CUR') / tot_blocks
    # best 2-layer tunnel (a 1x2 strip mine) anywhere in the band
    layer_tot = [sum(per_ore[o][j] for o in ORES) for j in range(n)]
    layer_res = [sum(per_ore[o][j] * RES[RARITY[o]] for o in ORES) for j in range(n)]
    best2 = max(range(n - 1), key=lambda j: layer_res[j] + layer_res[j + 1])
    t2 = (layer_tot[best2] + layer_tot[best2 + 1]) / (vol * 2)
    r2 = (layer_res[best2] + layer_res[best2 + 1]) / (layer_tot[best2] + layer_tot[best2 + 1])
    return dict(name=name, band=band, floor=b_floor, top=b_top, rows=rows, avg_d=avg_d, avg_res=avg_res,
                pick=pick, keptf=keptf, best2=(b_floor + best2, t2, r2), per_ore=per_ore, blocks=blocks)

def report(m, curve_d, curve_res):
    print(f"\n### {m['name']}: {m['band']} band Y{m['floor']}..{m['top']} ({m['top'] - m['floor'] + 1} layers), "
          f"band {100 * m['avg_d']:.2f}% at {m['avg_res']:.3f} Resonance an ore")
    print(" slab          density  vs band  C/U/R %          Res/ore  rate x  sd/81ch  ores present (blocks a chunk; * = under 1)")
    base_rate = curve_d * curve_res
    for lo, hi, d, mix, res, ob, sd in m['rows']:
        present = ', '.join(f"{o} {v:.0f}" if v >= 1 else f"{o}*" for o, v in ob.items() if v >= 0.05)
        print(f" Y{lo:5d}..{hi:4d}  {100 * d:6.2f}%  {d / m['avg_d']:5.2f}  "
              f"{100 * mix['C']:4.1f}/{100 * mix['U']:4.1f}/{100 * mix['R']:4.1f}   {res:6.3f}  {d * res / base_rate:5.2f}  {100 * sd:5.1f}%  {present}")
    y, d2, r2 = m['best2']
    print(f" best 1x2 tunnel: Y{y}..{y + 1} {100 * d2:.2f}% at {r2:.3f} -> {d2 * r2 / base_rate:.2f}x the curve's rate")
    print(" pick share by ore: " + ', '.join(f"{o} {100 * p:.1f}%" for o, p in m['pick'].items()))
    print(" edge factor: " + ', '.join(f"{r} {m['keptf']['edge_' + r]:.4f}" for r in 'CUR')
          + " | kept per ore: " + ', '.join(f"{o} {m['keptf'][o]:.3f}" for o in ORES if m['keptf'][o] < 0.995))

CURVE_HOURS = 100.0
def hours(m, curve_d, curve_res):
    best = max(m['rows'], key=lambda r: r[2] * r[4])
    x = best[2] * best[4] / (curve_d * curve_res)
    y, d2, r2 = m['best2']; x2 = d2 * r2 / (curve_d * curve_res)
    return best, x, x2

if __name__ == '__main__':
    print("Weights (blocks a chunk x biome share):", {o: round(WEIGHT[o], 1) for o in ORES})
    print("Mean blocks a vein:", {r: round(mean_blocks(r), 3) for r in 'CUR'})
    old = (-63, 245)       # the column today: Y-64..319 linear onto Y-63..245, both types
    one = (-64, 319)       # 1:1 vanilla Y
    CASES = [
        ('NOW base (rule of 2026-11-21, current heights, linear map, fold below Y1)', old, 1, 245, 'stone', True, False),
        ('NOW expanded stone', old, 0, 245, 'stone', False, False),
        ('NOW expanded deepslate', old, -63, -1, 'deepslate', False, False),
        ('A64 base (surface Y=64, stone Y1..59, 1:1, mass outside dropped)', one, 1, 59, 'stone', False, False),
        ('A64 base, variant: folded below and above', one, 1, 59, 'stone', True, True),
        ('A64 expanded stone', one, 0, 59, 'stone', False, False),
        ('A64 expanded deepslate (also B100)', one, -63, -1, 'deepslate', False, False),
        ('B100 base (surface Y=100, stone Y1..95, 1:1, dropped)', one, 1, 95, 'stone', False, False),
        ('B100 expanded stone', one, 0, 95, 'stone', False, False),
    ]
    out = {}
    for name, col, lo, hi, band, fb, fa in CASES:
        m = model(name, col, lo, hi, band, fb, fa)
        # the curve is priced on the base stone band: 3.04% at 3.485 Resonance an ore (§4.3)
        report(m, 0.0304, 3.485)
        out[name] = m
    print("\n## Floor-mining against the curve (solo 100 h; the team-of-five curve moves by the same factor)")
    print(" case                                        best slab        rate x  curve h  | best 1x2 tunnel  rate x  curve h")
    for name, m in out.items():
        best, x, x2 = hours(m, 0.0304, 3.485)
        print(f" {name[:43]:43s} Y{best[0]:4d}..{best[1]:4d}  {x:6.2f}  {CURVE_HOURS / x:6.1f}  | Y{m['best2'][0]:4d}..{m['best2'][0] + 1:4d}       {x2:6.2f}  {CURVE_HOURS / x2:6.1f}")
    # Mitigation option: each ore's height drawn half from its profile and half evenly through the band
    m = out['A64 base (surface Y=64, stone Y1..59, 1:1, mass outside dropped)']
    lo, hi, d, mix, res, ob, sd = m['rows'][0]
    rb = {r: 0.5 * sum(v for o, v in ob.items() if RARITY[o] == r) + 0.5 * m['blocks'][r] * 16 / 59 for r in 'CUR'}
    t = sum(rb.values()); rr = sum(rb[r] * RES[r] for r in 'CUR') / t
    print(f"\nA64 lowest slab with heights half profile, half even: {100 * t / 4096:.2f}% at {rr:.3f} -> {t / 4096 * rr / (0.0304 * 3.485):.2f}x")
    print("The old column shifted down by 192 (min_y -192, grass at Y=58) is the NOW rows with 192 taken off every Y.")
    print("\nNo slab in any case is above 60% ore, so the 40% floor clip never fires at the baseline (max slab above).")
    print("Max slab density:", max((r[2], n) for n, m in out.items() for r in m['rows']))
