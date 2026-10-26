# Port of vanilla OreFeature.place/doPlace (26.1, unchanged since 1.18) into an all-solid volume.
# Returns the list of y offsets (relative to origin y) of blocks placed by one vein.
import math, random

def ceil(x): return math.ceil(x)
def floor(x): return math.floor(x)

def vein(size, rnd):
    f = rnd.random() * math.pi
    g = size / 8.0
    i = ceil((size / 16.0 * 2.0 + 1.0) / 2.0)
    ox = oy = oz = 0
    d = ox + math.sin(f) * g; e = ox - math.sin(f) * g
    h = oz + math.cos(f) * g; j = oz - math.cos(f) * g
    l = oy + rnd.randrange(3) - 2; m = oy + rnd.randrange(3) - 2
    n0 = ox - ceil(g) - i; o0 = oy - 2 - i; p0 = oz - ceil(g) - i
    ds = []
    for q in range(size):
        r = q / size
        s = d + (e - d) * r; t = l + (m - l) * r; u = h + (j - h) * r
        v = rnd.random() * size / 16.0
        w = ((math.sin(math.pi * r) + 1.0) * v + 1.0) / 2.0
        ds.append([s, t, u, w])
    for q in range(size - 1):
        if ds[q][3] > 0:
            for x in range(q + 1, size):
                if ds[x][3] > 0:
                    a = ds[q][0]-ds[x][0]; b = ds[q][1]-ds[x][1]; c = ds[q][2]-ds[x][2]; vv = ds[q][3]-ds[x][3]
                    if vv*vv > a*a + b*b + c*c:
                        if vv > 0: ds[x][3] = -1.0
                        else: ds[q][3] = -1.0
    seen = set(); ys = []
    for s, t, u, w in ds:
        if w < 0: continue
        x0 = max(floor(s - w), n0); y0 = max(floor(t - w), o0); z0 = max(floor(u - w), p0)
        x1 = max(floor(s + w), x0); y1 = max(floor(t + w), y0); z1 = max(floor(u + w), z0)
        for X in range(x0, x1 + 1):
            ag = (X + 0.5 - s) / w
            if ag*ag >= 1: continue
            for Y in range(y0, y1 + 1):
                ai = (Y + 0.5 - t) / w
                if ag*ag + ai*ai >= 1: continue
                for Z in range(z0, z1 + 1):
                    ak = (Z + 0.5 - u) / w
                    if ag*ag + ai*ai + ak*ak < 1 and (X, Y, Z) not in seen:
                        seen.add((X, Y, Z)); ys.append(Y)
    return ys
