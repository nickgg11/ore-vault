"""E3-S13 (#401) door preview renders. Stylised oblique mock-ups built from the shipped 16x16 textures.

Run with: py -3.12 -I door_preview.py <repo root>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(sys.argv[1])
TEX = REPO / "src/main/resources/assets/orevault/textures/block"
OUT = REPO / "docs/previews/e3-s13"
OUT.mkdir(parents=True, exist_ok=True)

T = 16                      # texels per block
DX, DY = 0.85, 0.35         # oblique offset per block of depth (z, away from viewer)
DORMANT = (0x74, 0x76, 0x7C)
TIERS = {
    "T1": {"portal": (0x0D, 0x70, 0x26), "rune": (0x14, 0x80, 0x31)},
    "T2": {"portal": (0x31, 0x6C, 0xFF), "rune": (0x31, 0x6C, 0xFF)},
    "T3": {"portal": (0xA0, 0x38, 0xD0), "rune": (0xA0, 0x38, 0xD0)},
    "T4": {"portal": (0xFF, 0x80, 0x00), "rune": (0xFF, 0x80, 0x00)},
}
SKY_TOP, SKY_LOW = (0x7E, 0xA6, 0xE0), (0xB9, 0xD3, 0xF2)
GRASS, DIRT = (0x5D, 0x8C, 0x3A), (0x86, 0x60, 0x43)
INK, PAPER = (0x22, 0x22, 0x28), (0xF3, 0xF1, 0xEA)


def font(size, bold=False):
    name = "arialbd.ttf" if bold else "arial.ttf"
    try:
        return ImageFont.truetype("C:/Windows/Fonts/" + name, size)
    except OSError:
        return ImageFont.load_default()


def tex(name):
    return Image.open(TEX / (name + ".png")).convert("RGBA")


def tint(img, rgb):
    r, g, b, a = img.split()
    r = r.point(lambda v: v * rgb[0] // 255)
    g = g.point(lambda v: v * rgb[1] // 255)
    b = b.point(lambda v: v * rgb[2] // 255)
    return Image.merge("RGBA", (r, g, b, a))


def shade(img, f):
    if f >= 0.999:
        return img
    r, g, b, a = img.split()
    lut = lambda v: int(v * f)
    return Image.merge("RGBA", (r.point(lut), g.point(lut), b.point(lut), a))


# ---------------------------------------------------------------- door, as DoorPart lays it out

def strap_rows(height):
    top = height - 1
    bands = max(1, round_half_even(top / 5.0))
    return {round_half_even(b * top / bands) for b in range(bands + 1)}


def round_half_even(v):
    return int(round(v))  # Python's round is half-to-even, as Math.rint


def part(col, row, w, h):
    seat = row == 1
    strap = row in strap_rows(h)
    if w % 2 == 1 and col == w // 2:
        return ("door_seat_c" if seat else "door_strap_seam_c" if strap else "door_seam_c"), False
    if w % 2 == 0 and col == w // 2 - 1:
        return ("door_seat_l", False) if seat else (("door_strap_seam" if strap else "door_seam"), False)
    if w % 2 == 0 and col == w // 2:
        return ("door_seat_r", False) if seat else (("door_strap_seam" if strap else "door_seam"), True)
    left = col * 2 < w - 1
    return ("door_strap" if strap else "door_field"), not left


GEM_SHAPE = [  # signet gem pressed into the seat's pocket: 1 highlight, 2 base, 3 shadow, 4 silver band
    "4444",
    "4124",
    "4223",
    "4433",
]


def gem_colours(rgb):
    def mix(c, t, k):
        return tuple(int(c[i] + (t[i] - c[i]) * k) for i in range(3))
    warm = (255, 236, 196)
    cool = (24, 22, 48)
    return {"1": mix(rgb, warm, 0.55), "2": rgb, "3": mix(rgb, cool, 0.45), "4": (0xC8, 0xCC, 0xD4)}


def door_image(w, h, gem=None):
    img = Image.new("RGBA", (w * T, h * T))
    for row in range(h):
        for col in range(w):
            name, mirror = part(col, row, w, h)
            t = tex("vault_" + name)
            if mirror:
                t = t.transpose(Image.FLIP_LEFT_RIGHT)
            img.paste(t, (col * T, (h - 1 - row) * T))
    if gem is not None:
        cols = gem_colours(gem)
        cx = w * T // 2 - 2           # pocket centred on the seam (even) or the centre column (odd)
        cy = (h - 2) * T + 4          # seat row, pocket rows 4..7 of the tile
        px = img.load()
        for j, line in enumerate(GEM_SHAPE):
            for i, ch in enumerate(line):
                px[cx + i, cy + j] = cols[ch] + (255,)
    return img


# ---------------------------------------------------------------- frame ring

def frame_tile(x, y, w, h, rune_rgb):
    """Connected frame texture for ring cell (x, y), x in -1..w, y in -1..h; corners included."""
    conn = ""
    def ring(cx, cy):
        return (cx in (-1, w) and -1 <= cy <= h) or (cy in (-1, h) and -1 <= cx <= w)
    # texture names are written u, d, l, r; "u" means the neighbour above on screen
    if ring(x, y + 1): conn += "u"
    if ring(x, y - 1): conn += "d"
    if ring(x - 1, y): conn += "l"
    if ring(x + 1, y): conn += "r"
    name = "vault_frame_" + conn if conn else "vault_frame"
    base = tex(name).copy()
    base.alpha_composite(tint(tex(name + "_strand"), rune_rgb))
    return base


def plain_frame(rune_rgb):
    base = tex("vault_frame").copy()
    base.alpha_composite(tint(tex("vault_frame_strand"), rune_rgb))
    return base


# ---------------------------------------------------------------- oblique renderer

class Scene:
    def __init__(self, x0, x1, y0, y1, zmin=-0.6, zmax=0.6, scale=3, ground=True):
        self.x0, self.y1, self.zmin, self.zmax = x0, y1, zmin, zmax
        W = int(((x1 - x0) + DX * (zmax - zmin)) * T) + 1
        H = int(((y1 - y0) + DY * (zmax - zmin)) * T) + 1
        self.img = Image.new("RGBA", (W, H))
        self.scale = scale
        d = ImageDraw.Draw(self.img)
        for yy in range(H):
            k = yy / max(1, H)
            c = tuple(int(SKY_TOP[i] + (SKY_LOW[i] - SKY_TOP[i]) * k) for i in range(3))
            d.line([(0, yy), (W, yy)], fill=c + (255,))
        if ground:
            d.rectangle([0, int(self.sy(0, 0, zmax)), W, H], fill=(0x6E, 0x9E, 0x48, 255))
            d.rectangle([0, int(self.sy(0, 0, -0.5)), W, H], fill=GRASS + (255,))

    def sx(self, x, y, z):
        return (x - self.x0 + DX * (z - self.zmin)) * T

    def sy(self, x, y, z):
        return (self.y1 - y + DY * (self.zmax - z)) * T

    def quad(self, img, p0, u, v, f=1.0):
        """Draw img with its top-left at world p0; u spans its width, v its height (downward in texture)."""
        img = shade(img, f)
        tw, th = img.size
        ox, oy = self.sx(*p0), self.sy(*p0)
        ux = (self.sx(p0[0] + u[0], p0[1] + u[1], p0[2] + u[2]) - ox) / tw
        uy = (self.sy(p0[0] + u[0], p0[1] + u[1], p0[2] + u[2]) - oy) / tw
        vx = (self.sx(p0[0] + v[0], p0[1] + v[1], p0[2] + v[2]) - ox) / th
        vy = (self.sy(p0[0] + v[0], p0[1] + v[1], p0[2] + v[2]) - oy) / th
        det = ux * vy - vx * uy
        if abs(det) < 1e-6:
            return
        a, b = vy / det, -vx / det
        c, d = -uy / det, ux / det
        coeffs = (a, b, -(a * ox + b * oy), c, d, -(c * ox + d * oy))
        layer = img.transform(self.img.size, Image.AFFINE, coeffs, Image.NEAREST)
        # mask out the wrap-around: transform fills outside with transparent already
        self.img.alpha_composite(layer)

    def final(self):
        return self.img.resize((self.img.width * self.scale, self.img.height * self.scale), Image.NEAREST)


def render_portal(w, h, angle_deg, *, tier=None, rune=None, closed_gem=True, scale=3, toward=True):
    """One opening of w x h. angle 0 = closed. toward: leaves swing to the viewer's side (recommended)."""
    rune_rgb = rune if rune is not None else (TIERS[tier]["rune"] if tier else DORMANT)
    portal_rgb = TIERS[tier]["portal"] if tier else None
    th = math.radians(angle_deg)
    L = w / 2
    reach = L * math.sin(th)
    sgn = -1 if toward else 1
    zmin = min(-0.6, -reach - 0.1) if toward else -0.6
    zmax = 0.6 if toward else max(0.6, reach + 0.1)
    sc = Scene(-1, w + 1, -1, h + 1, zmin=zmin, zmax=zmax, scale=scale)

    side = plain_frame(rune_rgb)
    ring = [(x, y) for x in range(-1, w + 1) for y in range(-1, h + 1) if x in (-1, w) or y in (-1, h)]
    inner = lambda x, y: (y == -1 and 0 <= x < w, x == -1 and 0 <= y < h)
    for x, y in ring:  # the sill's top and the left jamb's inner face, seen through the opening
        top_in, right_in = inner(x, y)
        if top_in:
            sc.quad(side, (x, y + 1, 0.5), (1, 0, 0), (0, 0, -1), 1.0)
        if right_in:
            sc.quad(side, (x + 1, y + 1, -0.5), (0, 0, 1), (0, -1, 0), 0.6)

    gem = TIERS[tier]["portal"] if (tier and closed_gem) else None
    door = door_image(w, h, gem=gem)
    half = w * T // 2
    left_img = door.crop((0, 0, half, h * T))
    right_flip = door.crop((half, 0, w * T, h * T)).transpose(Image.FLIP_LEFT_RIGHT)

    def leaf(hinge_x, dirx, front_img, back_img, part):
        """part 'all', 'inner' (inside the frame's depth) or 'outer' (beyond it). Images run hinge to free end."""
        u = (dirx * L * math.cos(th), 0, sgn * L * math.sin(th))
        usx = u[0] + DX * u[2]
        front = (usx > 0) if dirx > 0 else (usx < 0)
        img = front_img if front else back_img
        f = 0.8 - 0.15 * math.sin(th) if front else 0.6
        cut = L if th == 0 else min(L, 0.5 / math.sin(th))
        k = cut / L
        cw = max(1, int(round(img.width * k)))
        if part in ("all", "inner"):
            piece = img if part == "all" else img.crop((0, 0, cw, img.height))
            kk = 1 if part == "all" else cw / img.width
            sc.quad(piece, (hinge_x, h, 0), tuple(c * kk for c in u), (0, -h, 0), f)
        elif cw < img.width:
            kk = cw / img.width
            p0 = (hinge_x + u[0] * kk, h, u[2] * kk)
            sc.quad(img.crop((cw, 0, img.width, img.height)), p0, tuple(c * (1 - kk) for c in u), (0, -h, 0), f)

    def leaves(part):
        if angle_deg < 0.5:
            if part != "outer":
                sc.quad(door, (0, h, 0), (w, 0, 0), (0, -h, 0), 0.8)
            return
        leaf(0, 1, left_img, right_flip, part)
        leaf(w, -1, right_flip, left_img, part)

    def portal():
        if portal_rgb is not None and angle_deg > 0:
            frame0 = Image.open(TEX / "vault_portal.png").convert("RGBA").crop((0, 0, 16, 16))
            ptile = tint(frame0, portal_rgb)
            for x in range(w):
                for y in range(h):
                    sc.quad(ptile, (x, y + 1, 0), (1, 0, 0), (0, -1, 0), 1.0)

    def rest_of_frame():
        for x, y in ring:
            top_in, right_in = inner(x, y)
            if not top_in:
                sc.quad(side, (x, y + 1, 0.5), (1, 0, 0), (0, 0, -1), 1.0)
            if not right_in:
                sc.quad(side, (x + 1, y + 1, -0.5), (0, 0, 1), (0, -1, 0), 0.6)

    def fronts():
        for x, y in ring:
            sc.quad(frame_tile(x, y, w, h, rune_rgb), (x, y + 1, -0.5), (1, 0, 0), (0, -1, 0), 0.8)

    if toward:
        portal(); rest_of_frame(); leaves("inner"); fronts(); leaves("outer")
    else:
        leaves("all"); portal(); rest_of_frame(); fronts()
    return sc.final()


# ---------------------------------------------------------------- sheets

def sheet(panels, title, notes, cols=None, pad=24, bg=PAPER):
    cols = cols or len(panels)
    rows = math.ceil(len(panels) / cols)
    colw = [max(panels[i][0].width for i in range(c, len(panels), cols)) for c in range(cols)]
    cw = max(colw)
    ch = max(p[0].height for p in panels)
    colx = [pad + sum(colw[:c]) + pad * c for c in range(cols)]
    ft, fl, fn = font(26, True), font(18, True), font(16)
    head = 56
    foot = 24 + 22 * len(notes)
    W = pad + sum(colw) + pad * cols
    W = max(W, 900)
    H = head + rows * (ch + 34 + pad) + foot
    out = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(out)
    d.text((pad, 16), title, font=ft, fill=INK)
    for i, (img, label) in enumerate(panels):
        r, c = divmod(i, cols)
        x = colx[c] + (colw[c] - img.width) // 2
        y = head + r * (ch + 34 + pad) + (ch - img.height)
        out.paste(img, (x, y), img)
        d.text((colx[c], head + r * (ch + 34 + pad) + ch + 6), label, font=fl, fill=INK)
    y = H - foot + 8
    for n in notes:
        d.text((pad, y), n, font=fn, fill=INK)
        y += 22
    return out


def main():
    # 1. states: closed, mid-swing, open; 5x7, Tier 2 lit; plus the rejected swing-away for comparison
    states = [
        (with_plan(render_portal(5, 7, 0, tier="T2"), 5, 0), "Closed (far)"),
        (with_plan(render_portal(5, 7, 45, tier="T2"), 5, 45), "Mid-swing, 45 degrees"),
        (with_plan(render_portal(5, 7, 90, tier="T2"), 5, 90), "Open (near)"),
        (with_plan(render_portal(5, 7, 90, tier="T2", toward=False), 5, 90, toward=False),
         "Not recommended: swinging away"),
    ]
    sheet(states, "E3-S13 door states: 5x7 interior, Tier 2 portal",
          ["Leaves hinge on the jambs and swing 90 degrees towards the viewer, over 10 ticks with an ease-out.",
           "Open shows the tier-tinted portal strip. The door is drawn by each client for its own player only.",
           "Swinging away hides the leaves behind the portal (alpha 196), so the swing, the point of the feature, is lost.",
           "Closed and lit: runes tinted and the signet's gem sits in the seat."], cols=4
          ).save(OUT / "01_states_5x7.png")

    # 2. sizes, same block scale
    for ang, fname, lab in ((0, "02_sizes_closed.png", "closed"), (90, "03_sizes_open.png", "open")):
        sizes = [
            (render_portal(2, 3, ang, tier="T2", scale=2), "2x3"),
            (render_portal(5, 7, ang, tier="T2", scale=2), "5x7"),
            (render_portal(21, 21, ang, tier="T2", scale=2), "21x21"),
        ]
        sheet(sizes, f"E3-S13 one door at three sizes, {lab}, same scale",
              ["One door, scaled by structure, never stretched or tiled: two leaves always fill the opening, a strap",
               "about every 5 rows, one seat on the row above the bottom strap. 16 texels per block (DoorPart, shipped).",
               "Open, each leaf is half the width: a 21x21 leaf reaches 10.5 blocks out, so the open distance",
               "grows with width (6 blocks, or half the width + 2 if more: 12.5 at 21 wide) and never sweeps the player."]
              ).save(OUT / fname)

    # 3. tiers: sealed + T1..T4, closed and open, 4x5
    closed = [(render_portal(4, 5, 0, tier=None, scale=3), "Sealed, unlit")]
    opened = [(render_portal(4, 5, 0, tier=None, scale=3), "Sealed: never swings")]
    for t in TIERS:
        closed.append((render_portal(4, 5, 0, tier=t, scale=3), f"{t} lit, closed"))
        opened.append((render_portal(4, 5, 90, tier=t, scale=3), f"{t} open"))
    sheet(closed + opened, "E3-S13 tier tints with the door (4x5)",
          ["Closed: the tier shows in the frame runes (T1 runes #148031) and in the signet gem set in the seat.",
           "Open: the portal strip in the tier colour (T1 #0D7026, T2 #316CFF, T3 #A038D0, T4 #FF8000).",
           "Sealed (no signet yet): grey runes #74767C, empty seat. That is how a far closed lit door differs from a sealed one."],
          cols=5).save(OUT / "04_tiers.png")
    g = Image.open(OUT / "04_tiers.png").convert("L").convert("RGB")
    g.save(OUT / "05_tiers_greyscale.png")

    # 4. plan view diagram: distance, client side, both sides, return portal
    plan_diagram().save(OUT / "06_plan_distance_and_sides.png")

    # 5. swing animation
    frames = []
    for a in [0] * 6 + [9 * i for i in range(1, 11)] + [90] * 8 + [90 - 9 * i for i in range(1, 11)]:
        frames.append(render_portal(5, 7, a, tier="T3", scale=3).convert("RGB"))
    frames[0].save(OUT / "07_swing_5x7_t3.gif", save_all=True, append_images=frames[1:], duration=50, loop=0)

    # 6. return portal: plain, no door
    rp = render_portal(2, 3, 90, tier="T2", scale=3)
    rp_plain = return_portal_render()
    sheet([(rp_plain, "Return portal: no door"), (rp, "Not this")],
          "E3-S13 return portal in the Vault",
          ["The player arrives 3 blocks south of it, inside the open distance: a door would always be open",
           "on arrival, so it would only ever be seen swinging. It is built lit, so it is never sealed and has no press.",
           "It is the way out when things go wrong; it stays a plain portal that reads at a glance."]
          ).save(OUT / "08_return_portal.png")


def with_plan(img, w, angle, toward=True):
    """Adds a small top-down sketch of the leaves under a render: the viewer is at the bottom."""
    S = 22
    ph = 2 * int(w / 2 * S) + 50
    out = Image.new("RGBA", (img.width, img.height + ph), PAPER + (255,))
    out.paste(img, (0, 0))
    d = ImageDraw.Draw(out)
    cx = img.width // 2
    y0 = img.height + ph // 2 - 10
    stone, wood, blue = (0x9A, 0x93, 0x80), (0x76, 0x56, 0x38), (0x31, 0x6C, 0xFF)
    x0, x1 = cx - w / 2 * S, cx + w / 2 * S
    d.rectangle([x0 - S, y0 - S / 2, x0, y0 + S / 2], fill=stone)
    d.rectangle([x1, y0 - S / 2, x1 + S, y0 + S / 2], fill=stone)
    if angle > 0:
        d.line([(x0, y0), (x1, y0)], fill=blue, width=4)
    t = math.radians(angle)
    L = w / 2 * S
    sg = 1 if toward else -1
    d.line([(x0, y0), (x0 + L * math.cos(t), y0 + sg * L * math.sin(t))], fill=wood, width=6)
    d.line([(x1, y0), (x1 - L * math.cos(t), y0 + sg * L * math.sin(t))], fill=wood, width=6)
    d.text((x1 + S + 8, y0 - 10), "seen from above;\nviewer below", font=font(14), fill=INK)
    return out


def return_portal_render():
    w, h = 2, 3
    sc = Scene(-1, w + 1, -1, h + 1, zmax=0.6, scale=3)
    rune = TIERS["T2"]["rune"]
    side = plain_frame(rune)
    for x in range(-1, w + 1):
        for y in range(-1, h + 1):
            if (x in (-1, w)) or (y in (-1, h)):
                sc.quad(side, (x, y + 1, 0.5), (1, 0, 0), (0, 0, -1), 1.0)
                sc.quad(side, (x + 1, y + 1, -0.5), (0, 0, 1), (0, -1, 0), 0.6)
    frame0 = Image.open(TEX / "vault_portal.png").convert("RGBA").crop((0, 0, 16, 16))
    ptile = tint(frame0, TIERS["T2"]["portal"])
    for x in range(w):
        for y in range(h):
            sc.quad(ptile, (x, y + 1, 0), (1, 0, 0), (0, -1, 0), 1.0)
    for x in range(-1, w + 1):
        for y in range(-1, h + 1):
            if (x in (-1, w)) or (y in (-1, h)):
                sc.quad(frame_tile(x, y, w, h, rune), (x, y + 1, -0.5), (1, 0, 0), (0, -1, 0), 0.8)
    return sc.final()


def plan_diagram():
    S = 22  # px per block
    W, H = 1180, 760
    out = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(out)
    ft, fl, fn = font(26, True), font(18, True), font(16)
    d.text((24, 16), "E3-S13 seen from above: open distance, both sides, client-side only", font=ft, fill=INK)

    # left: 5-wide opening, two players on opposite sides
    cx, cy = 300, 380
    grid = (0xDD, 0xDA, 0xD0)
    for i in range(-13, 14):
        d.line([(cx + i * S, cy - 13 * S), (cx + i * S, cy + 13 * S)], fill=grid)
        d.line([(cx - 13 * S, cy + i * S), (cx + 13 * S, cy + i * S)], fill=grid)
    # rings measured from the nearest door cell: draw stadium shapes
    for r, col, lab in ((8, (0xB0, 0x40, 0x40), "closes beyond 8"), (6, (0x2E, 0x7D, 0x32), "opens within 6")):
        x0, x1 = cx - 2.5 * S, cx + 2.5 * S
        d.rounded_rectangle([x0 - r * S, cy - r * S, x1 + r * S, cy + r * S], radius=r * S, outline=col, width=3)
        d.text((cx - 70, cy + r * S + 4), lab, font=fl, fill=col)
    # frame and door line
    d.rectangle([cx - 3.5 * S, cy - 0.5 * S, cx - 2.5 * S, cy + 0.5 * S], fill=(0x9A, 0x93, 0x80))
    d.rectangle([cx + 2.5 * S, cy - 0.5 * S, cx + 3.5 * S, cy + 0.5 * S], fill=(0x9A, 0x93, 0x80))
    d.rectangle([cx - 2.5 * S, cy - 0.12 * S, cx + 2.5 * S, cy + 0.12 * S], fill=(0x31, 0x6C, 0xFF))
    # player A south (below) at 4 blocks: sees leaves swing north, away from A
    ax, ay = cx - 1 * S, cy + 4 * S
    bx, by = cx + 2 * S, cy - 11 * S
    for (px, py, name) in ((ax, ay, "A"), (bx, by, "B")):
        d.ellipse([px - 8, py - 8, px + 8, py + 8], fill=INK)
        d.text((px + 12, py - 10), name, font=fl, fill=INK)
    wood = (0x76, 0x56, 0x38)
    d.line([(cx - 2.5 * S, cy), (cx - 2.5 * S, cy + 2.5 * S)], fill=wood, width=6)
    d.line([(cx + 2.5 * S, cy), (cx + 2.5 * S, cy + 2.5 * S)], fill=wood, width=6)
    d.text((cx - 4.5 * S, cy + 5 * S), "A's screen: open, towards A", font=fn, fill=wood)
    d.text((bx - 200, by - 34), "B, 10.5 blocks off: B's screen shows it closed", font=fn, fill=INK)

    # right column text
    tx = 650
    lines = [
        ("Open distance", True),
        ("Opens when the local player is within 6 blocks of the", False),
        ("nearest door cell, or half the width + 2 if that is", False),
        ("more (12.5 at 21 wide), so a leaf never sweeps through", False),
        ("the player. Closes 2 blocks further out (no flicker).", False),
        ("Swing 10 ticks each way.", False),
        ("", False),
        ("Client side only", True),
        ("Each client works out its own player's distance and", False),
        ("draws its own swing. Nothing is sent, nothing is", False),
        ("stored, the block state never changes. A and B see", False),
        ("different doors at the same moment. The server only", False),
        ("knows the lit portal.", False),
        ("", False),
        ("Either side", True),
        ("Leaves swing towards whoever is looking, in front of", False),
        ("the portal where the swing can be seen. A closed", False),
        ("door shows the door on both faces (back shows the", False),
        ("mirrored tiles, DoorPart#back); the portal is hidden", False),
        ("behind it from both sides. Open, the portal strip is", False),
        ("what you see, with the far side faintly through it.", False),
        ("", False),
        ("Entry", True),
        ("Sealed door (no signet): solid, as shipped. Lit portal:", False),
        ("the drawn door has no collision; by 6 blocks it is", False),
        ("open anyway. 80-tick charge and Tier 3 instant travel", False),
        ("unchanged; the door stays open while you are inside.", False),
    ]
    y = 70
    for text, bold in lines:
        d.text((tx, y), text, font=fl if bold else fn, fill=INK)
        y += 26 if bold else 22
    return out


if __name__ == "__main__":
    main()
