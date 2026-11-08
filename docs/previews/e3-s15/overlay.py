"""E3-S15 charging overlay candidate: an 8-frame near-white strip, tinted per tier, and its previews.
Pure Python (zlib PNG), no external libraries."""
import math, os, random, struct, zlib

OUT = r"E:/Github/Ore-Vault_2/docs/previews/e3-s15"
S = 4  # texels per unit of the first draft (160x90); Nick asked for higher resolution, 2026-11-08
W, H, FRAMES = 160 * S, 90 * S, 8
TIERS = {"T1": (0x0D, 0x70, 0x26), "T2": (0x31, 0x6C, 0xFF), "T3": (0xA0, 0x38, 0xD0), "T4": (0xFF, 0x80, 0x00)}
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def write_png(path, w, h, rows):  # rows: list of lists of (r,g,b,a)
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)


def edge_dist(x, y, r):
    """Distance in units from the screen edge to a rounded rectangle of corner radius r units."""
    dx, dy = min(x, W - 1 - x) / S, min(y, H - 1 - y) / S
    if dx < r and dy < r:
        return max(0.0, r - math.hypot(r - dx, r - dy))
    return min(dx, dy)


def along(x, y):
    dx, dy = min(x, W - 1 - x), min(y, H - 1 - y)
    return (x if dy <= dx else y) / S


# alpha, grey: outer band to inner band. Eight steps, dithered at each boundary.
VEIL = [(0.70, 0xA6), (0.58, 0xAE), (0.47, 0xB6), (0.37, 0xBE), (0.28, 0xC6), (0.20, 0xCE), (0.13, 0xD6), (0.07, 0xDE)]


def stud(x, y, d_u, centre_d, spacing, radius, corner):
    """A rune stud: a diamond with a darker core, on a line at centre_d, every `spacing` units.
    A stud whose centre falls inside a rounded corner is left out, so none is drawn cut in half."""
    dx, dy = min(x, W - 1 - x), min(y, H - 1 - y)
    a_u, side = (x / S, W / S) if dy <= dx else (y / S, H / S)
    c = (a_u // spacing) * spacing + spacing / 2
    if c - radius < corner or c + radius > side - corner:
        return None
    u = a_u - c
    v = d_u - centre_d
    m = abs(u) + abs(v)
    if m <= radius * 0.45:
        return 0.95, 0xC8
    if m <= radius:
        return 0.95, 0xFF
    return None


def frame(f):
    p = (f + 1) / FRAMES
    R = 4 + p * 18
    rr = R + 8
    px = [[(0, 0, 0, 0)] * W for _ in range(H)]
    for y in range(H):
        row = px[y]
        for x in range(W):
            d = edge_dist(x, y, rr)
            a, g = 0.0, 0
            if d < R:
                band = d / R * len(VEIL)
                i = int(band)
                frac = band - i
                if frac > 0.6 and BAYER[y & 3][x & 3] / 16 < (frac - 0.6) / 0.4 and i + 1 < len(VEIL):
                    i += 1
                a, g = VEIL[min(i, len(VEIL) - 1)]
            dx, dy = min(x, W - 1 - x) / S, min(y, H - 1 - y) / S
            off = abs(d - R)
            if off < 0.75:
                a, g = max(a, 0.30), 0xE8          # soft edge either side of the front line
            if off < 0.26:
                a, g = 0.70, 0xF6                  # the front line, two texels wide
            st = stud(x, y, d, R, 8, 1.6, rr)
            if st:
                a, g = st
            de = edge_dist(x, y, 12)
            if 1.0 <= de < 1.5:
                a, g = max(a, 0.9), 0xE6          # fixed inlay near the screen edge
            st = stud(x, y, de, 2.75, 16, 1.25, 12)
            if st:
                a, g = st
            if a > 0:
                row[x] = (g, g, g, int(round(a * 255)))
    return px


frames = [frame(f) for f in range(FRAMES)]
write_png(os.path.join(OUT, f"overlay_candidate_strip_{W}x{H * FRAMES}.png"), W, H * FRAMES,
          [row for fr in frames for row in fr])

# ---------- backdrop: 320x180 logical pixels, a plain in-game-like view ----------
BW, BH = 320, 180
rnd = random.Random(7)
def backdrop():
    img = [[(0, 0, 0)] * BW for _ in range(BH)]
    horizon = 92
    for y in range(BH):
        for x in range(BW):
            if y < horizon:
                t = y / horizon
                c = (int(0x78 + (0xC3 - 0x78) * t), int(0xA7 + (0xD7 - 0xA7) * t), 0xFF)
                # distant hills
                hill = horizon - 10 - 8 * math.sin(x / 23.0) - 4 * math.sin(x / 9.0 + 1)
                if y > hill:
                    c = (0x5E, 0x8F, 0x4A) if y > hill + 2 else (0x6E, 0x9F, 0x58)
                img[y][x] = c
            else:
                bx, by = x // 16, (y - horizon) // 16
                ly = (y - horizon) % 16
                n = rnd.randint(-10, 10)
                if by == 0 and ly < 3:
                    base = (0x5D, 0x9B, 0x3A)  # grass top
                elif by == 0:
                    base = (0x86, 0x60, 0x43)  # dirt
                else:
                    base = (0x7F, 0x7F, 0x7F)  # stone
                    if (bx * 7 + by * 3) % 11 == 0 and rnd.random() < 0.18:
                        base = (0xD8, 0xAF, 0x93)  # iron ore fleck
                img[y][x] = tuple(max(0, min(255, v + n)) for v in base)
    # hotbar and crosshair
    hx, hy = (BW - 182) // 2, BH - 24
    for y in range(hy, hy + 22):
        for x in range(hx, hx + 182):
            edge = y in (hy, hy + 21) or x in (hx, hx + 181) or (x - hx) % 20 == 0
            img[y][x] = (0x30, 0x30, 0x30) if edge else (0x8B, 0x8B, 0x8B)
    cx, cy = BW // 2, BH // 2
    for i in range(-4, 5):
        img[cy][cx + i] = (0xEE, 0xEE, 0xEE)
        img[cy + i][cx] = (0xEE, 0xEE, 0xEE)
    return img

BG = backdrop()



def composite(fr, tint, alpha_scale, out_w, out_h):
    """Backdrop and overlay both sampled nearest to the output size; tint multiplies grey; source-over."""
    out = []
    for oy in range(out_h):
        by, fy = BG[oy * BH // out_h], fr[oy * H // out_h]
        row = []
        for ox in range(out_w):
            r, g, b = by[ox * BW // out_w]
            g0, _, _, a8 = fy[ox * W // out_w]
            a = a8 / 255 * alpha_scale
            tr, tg, tb = tint[0] * g0 // 255, tint[1] * g0 // 255, tint[2] * g0 // 255
            row.append((int(r + (tr - r) * a), int(g + (tg - g) * a), int(b + (tb - b) * a)))
        out.append(row)
    return out


def alpha_for(p):
    return 0.35 + 0.55 * p


def to_rgba(rows):
    return [[(*px, 255) for px in row] for row in rows]


def to_grey(rows):
    return [[(int(0.2126 * r + 0.7152 * g + 0.0722 * b),) * 3 + (255,) for r, g, b in row] for row in rows]


STATES = [("mid", 3, 0.5), ("near_full", 7, 0.95)]
GAP = (0x20, 0x20, 0x20)
grid = []
for name, tint in TIERS.items():
    cells = [composite(frames[fi], tint, alpha_for(p), 640, 360) for _, fi, p in STATES]
    grid.extend(cells[0][y] + [GAP] * 8 + cells[1][y] for y in range(360))
    grid.extend([[GAP] * (1288)] * 8)
    for res, (ow, oh) in (("1080p", (1920, 1080)),):
        full = composite(frames[7], tint, alpha_for(0.95), ow, oh)
        write_png(os.path.join(OUT, f"overlay_preview_{name.lower()}_near_full.png"), ow, oh, to_rgba(full))
write_png(os.path.join(OUT, "overlay_preview_grid.png"), 1288, len(grid), to_rgba(grid))
write_png(os.path.join(OUT, "overlay_preview_grid_greyscale.png"), 1288, len(grid), to_grey(grid))


# contact sheet: frames 1 to 8 at half size on a checker (2x4), then frame 4 and frame 8 at 1x
def checker(w, h):
    return [[(0x9A,) * 3 if ((x // 8 + y // 8) & 1) else (0x6A,) * 3 for x in range(w)] for y in range(h)]


def over(bg, fr, ox, oy, k_num=1, k_den=1):
    w, h = W * k_num // k_den, H * k_num // k_den
    for y in range(h):
        fy = fr[y * k_den // k_num]
        for x in range(w):
            g0, _, _, a8 = fy[x * k_den // k_num]
            a = a8 / 255
            r, g, b = bg[oy + y][ox + x]
            bg[oy + y][ox + x] = (int(r + (g0 - r) * a), int(g + (g0 - g) * a), int(b + (g0 - b) * a))


SW = W + 8 + 8
sheet = checker(4 * (W // 2 + 8) + 8, 2 * (H // 2 + 8) + 2 * (H + 8) + 8)
for f in range(FRAMES):
    over(sheet, frames[f], 8 + (f % 4) * (W // 2 + 8), 8 + (f // 4) * (H // 2 + 8), 1, 2)
oy = 8 + 2 * (H // 2 + 8) + 8
over(sheet, frames[3], 8, oy)
over(sheet, frames[7], 8 + W + 8, oy)
write_png(os.path.join(OUT, "overlay_candidate_contact_sheet.png"), len(sheet[0]), len(sheet), to_rgba(sheet))
print("done", W, H * FRAMES)
