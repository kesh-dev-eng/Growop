#!/usr/bin/env python3
"""Generates everything data-driven for GrowOps:
  - src/main/resources/items.yml   (plugin item definitions, with ItemsAdder ids)
  - src/main/resources/strains.yml (the strains and their effects)
  - itemsadder/growops/configs/items.yml + 64px textures (ItemsAdder pack)

Run from the project root:  python3 tools/generate_items.py
Textures are drawn procedurally. Replace any PNG in itemsadder/growops/textures/item/ with your
own art (same file name) at any time; do not rerun this script afterwards or it overwrites them.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "growops"
S = 256   # drawing size
OUT = 64  # texture size

# id, name, colour, grow min, water/min, yield (min,max), seeds (min,max), mutation (chance, strain),
# decay min, needs tent, base quality, effects
STRAINS = [
    ("blue_dream", "Blue Dream", "4F86F7", 25, 3.5, (4, 7), (0, 2), (0.06, "azure_haze"), 28, False, 62,
     ["NIGHT_VISION", "SLOW_FALLING", "REGENERATION"]),
    ("bubba_kush", "Bubba Kush", "6A3F8C", 28, 3.0, (5, 8), (0, 1), (0.05, "og_kush"), 30, False, 64,
     ["RESISTANCE", "SLOWNESS", "SATURATION"]),
    ("super_lemon_haze", "Super Lemon Haze", "D4E157", 22, 3.0, (3, 6), (0, 2), (0.07, "pineapple_express"), 24, False, 60,
     ["SPEED", "HASTE", "JUMP_BOOST"]),
    ("azure_haze", "Azure Haze", "26C6DA", 30, 4.0, (3, 6), (0, 1), (0.05, "blue_dream"), 22, True, 70,
     ["WATER_BREATHING", "DOLPHINS_GRACE", "NIGHT_VISION"]),
    ("whitethorn_rose", "Whitethorn Rose", "F06292", 32, 3.5, (3, 5), (0, 1), (0.04, "purple_haze"), 22, True, 74,
     ["REGENERATION", "ABSORPTION", "HEALTH_BOOST"]),
    ("og_kush", "OG Kush", "4CAF50", 20, 3.0, (3, 6), (0, 2), (0.08, "bubba_kush"), 30, False, 55,
     ["STRENGTH", "RESISTANCE", "HUNGER"]),
    ("panama_red", "Panama Red", "D84315", 26, 3.2, (4, 7), (0, 2), (0.06, "super_lemon_haze"), 26, False, 58,
     ["FIRE_RESISTANCE", "STRENGTH", "SPEED"]),
    ("purple_haze", "Purple Haze", "9C27B0", 30, 4.0, (4, 8), (0, 1), (0.05, "whitethorn_rose"), 20, True, 68,
     ["LEVITATION", "NIGHT_VISION", "NAUSEA"]),
    ("ghost_train_haze", "Ghost Train Haze", "CFD8DC", 35, 4.5, (3, 6), (0, 1), (0.03, "purple_haze"), 18, True, 80,
     ["INVISIBILITY", "SPEED:2", "NIGHT_VISION"]),
    ("pineapple_express", "Pineapple Express", "FFB300", 27, 3.5, (4, 7), (0, 2), (0.06, "panama_red"), 26, False, 63,
     ["LUCK", "HASTE", "JUMP_BOOST"]),
]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


GREEN = (62, 142, 52)
WHITE = (245, 245, 240)

# ---------------------------------------------------------------- drawing helpers


def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def finish(img):
    a = img.split()[3]
    ol = a.filter(ImageFilter.MaxFilter(7))
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    base.paste(Image.new("RGBA", (S, S), (12, 22, 12, 210)), (0, 0), ol)
    base.alpha_composite(img)
    return base.resize((OUT, OUT), Image.LANCZOS)


K = 4          # supersample factor for the hi-res plant textures
OUT_HI = 256   # plant texture size
JR = random.Random(11)


class Scaled:
    """Draws with the 256-space coordinates onto a K times larger canvas."""

    def __init__(self, img, k):
        self.d = ImageDraw.Draw(img)
        self.k = k

    def _pts(self, pts):
        return [(x * self.k, y * self.k) for x, y in pts]

    def ellipse(self, box, fill=None, outline=None, width=1):
        self.d.ellipse([v * self.k for v in box], fill=fill, outline=outline, width=max(1, int(width * self.k)))

    def polygon(self, pts, fill=None):
        self.d.polygon(self._pts(pts), fill=fill)

    def line(self, xy, fill=None, width=1):
        self.d.line([v * self.k for v in xy], fill=fill, width=max(1, int(width * self.k)))

    def rectangle(self, box, fill=None, outline=None, width=1):
        self.d.rectangle([v * self.k for v in box], fill=fill, outline=outline, width=max(1, int(width * self.k)))


def finish_hi(img, grain=7):
    """Soft outline, leaf-surface grain, then downsample to the final plant texture size."""
    a = img.split()[3]
    ol = a.filter(ImageFilter.MaxFilter(5))
    base = Image.new("RGBA", img.size, (0, 0, 0, 0))
    base.paste(Image.new("RGBA", img.size, (10, 20, 10, 190)), (0, 0), ol)
    base.alpha_composite(img)
    arr = np.array(base).astype(np.float32)
    rng = np.random.default_rng(3)
    raw = np.clip(rng.normal(128, 40, arr.shape[:2]), 0, 255).astype(np.uint8)
    blurred = np.array(Image.fromarray(raw, "L").filter(ImageFilter.GaussianBlur(0.8))).astype(np.float32)
    noise = (blurred - 128.0) / 40.0 * grain * 2.2
    arr[..., :3] += noise[..., None]
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    return out.resize((OUT_HI, OUT_HI), Image.LANCZOS)


def overlay(img, fn):
    """Draw translucent shapes on a separate layer so they blend instead of overwriting pixels."""
    layer = new()
    fn(ImageDraw.Draw(layer))
    img.alpha_composite(layer)


def blob(d, cx, cy, rx, ry, dark, light, steps=9):
    """Ellipse with a soft highlight toward the upper left."""
    for i in range(steps):
        t = i / (steps - 1)
        k = 1 - 0.7 * t
        ox = -rx * 0.25 * t
        oy = -ry * 0.3 * t
        c = lerp(dark, light, t)
        d.ellipse((cx + ox - rx * k, cy + oy - ry * k, cx + ox + rx * k, cy + oy + ry * k), fill=c + (255,))


def shaded_poly(d, pts, dark, light, steps=6):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    for i in range(steps):
        t = i / (steps - 1)
        k = 1 - 0.55 * t
        c = lerp(dark, light, t)
        d.polygon([(cx + (x - cx) * k - 2 * t, cy + (y - cy) * k - 3 * t) for x, y in pts], fill=c + (255,))


def rrect(d, box, r, dark, light, vertical=True):
    x0, y0, x1, y1 = box
    n = int((y1 - y0) if vertical else (x1 - x0))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, r, fill=255)
    grad = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for i in range(n):
        t = i / max(1, n - 1)
        c = lerp(light, dark, t) + (255,)
        if vertical:
            gd.line((x0, y0 + i, x1, y0 + i), fill=c)
        else:
            gd.line((x0 + i, y0, x0 + i, y1), fill=c)
    return grad, mask


def paste_rrect(img, box, r, dark, light, vertical=True):
    grad, mask = rrect(ImageDraw.Draw(img), box, r, dark, light, vertical)
    img.paste(grad, (0, 0), mask)


def leaflet(d, bx, by, ang, length, width, dark, light):
    """One serrated lanceolate cannabis leaflet starting at (bx, by) pointing along ang (degrees)."""
    n = 14
    left, right = [], []
    for i in range(n + 1):
        t = i / n
        w = width * math.sin(math.pi * (t ** 0.75)) * (1.0 + (0.16 if i % 2 else 0.0))
        left.append((t * length, -w))
        right.append((t * length, w))
    pts = left + right[::-1]
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    j = JR.uniform(0.88, 1.12)
    dark, light = shade(dark, j), shade(light, j)
    rot = [(bx + x * ca - y * sa, by + x * sa + y * ca) for x, y in pts]
    shaded_poly(d, rot, dark, light)
    vein = shade(light, 1.12) + (255,)
    for i in range(1, 9):
        t = i / 9
        vx, vy = bx + t * length * ca, by + t * length * sa
        vl = width * math.sin(math.pi * (t ** 0.75)) * 0.8
        for sg in (-1, 1):
            va = a + sg * math.radians(52)
            d.line((vx, vy, vx + vl * math.cos(va), vy + vl * math.sin(va)), fill=vein, width=1)
    d.line((bx, by, bx + length * 0.92 * ca, by + length * 0.92 * sa), fill=shade(light, 1.2) + (255,), width=2)


def fan_leaf(d, cx, cy, size, n, tilt, dark, light):
    mid = -90 + tilt
    step = 27 if n >= 7 else 34
    for k in range(-(n // 2), n // 2 + 1):
        L = size * (1.0 - 0.16 * abs(k))
        W = size * (0.13 - 0.012 * abs(k))
        leaflet(d, cx, cy, mid + k * step, L, W, dark, light)


def cola(d, cx, cy, h, color, rnd):
    """Flowering top: many small calyx clusters, thin orange pistils and fine frost."""
    dark, light = shade(color, 0.5), lerp(color, (255, 255, 255), 0.3)
    n = max(5, int(h / 8))
    for i in range(n):
        t = i / (n - 1)
        r = 13 * (1 - 0.6 * t) + 4
        y = cy - h * t
        for dx in (-1.0, -0.5, 0.0, 0.5, 1.0):
            jitter = rnd.uniform(-2.5, 2.5)
            c1 = lerp(dark, color, rnd.uniform(0.0, 0.6))
            blob(d, cx + dx * r * 0.9 + jitter, y + rnd.uniform(-3, 3), r * 0.55, r * 0.65, c1, light, 5)
    for _ in range(int(h * 1.3)):
        x = cx + rnd.uniform(-15, 15) * (1 - 0.0)
        y = cy - rnd.uniform(0, h)
        a = rnd.uniform(0, 6.28)
        ln = rnd.uniform(4, 8)
        d.line((x, y, x + math.cos(a) * ln, y + math.sin(a) * ln), fill=(rnd.randint(215, 245), rnd.randint(110, 150), rnd.randint(30, 60), 255), width=1)
    for _ in range(int(h * 1.6)):
        x = cx + rnd.uniform(-15, 15)
        y = cy - rnd.uniform(0, h)
        d.ellipse((x - 0.9, y - 0.9, x + 0.9, y + 0.9), fill=(255, 255, 255, 210))


# ---------------------------------------------------------------- plant stages


def plant(stage, color, dead=False):
    img = Image.new("RGBA", (S * K, S * K), (0, 0, 0, 0))
    d = Scaled(img, K)
    rnd = random.Random(color[0] * 7 + color[1] * 3 + color[2] + stage)
    leaf_c = lerp(GREEN, color, 0.30)
    if dead:
        leaf_c = (140, 110, 60)
    dark, light = shade(leaf_c, 0.55), shade(leaf_c, 1.2)
    stem = (110, 130, 60) if not dead else (110, 85, 50)
    base_y = 244
    heights = [60, 105, 140, 172, 195]
    if dead:
        heights = [150]
    h = heights[0 if dead else stage]
    x = 128
    sw = 4.5 if stage > 0 or dead else 3
    shaded_poly(d, [(x - sw, base_y), (x + sw, base_y), (x + sw * 0.45, base_y - h), (x - sw * 0.45, base_y - h)],
                shade(stem, 0.7), shade(stem, 1.25), 5)
    for k in range(1, 5 if stage > 1 else 1):
        ny = base_y - h * k / 5
        d.ellipse((x - sw * 0.9, ny - 2, x + sw * 0.9, ny + 2), fill=shade(stem, 0.6) + (255,))
    if dead:
        for i, ang in enumerate((-150, -30, -120, -60)):
            leaflet(d, x, base_y - 60 - i * 22, ang + 90 * (i % 2) - 90 * 0, 46, 8, dark, light)
        return finish_hi(img)
    if stage == 0:
        for sx in (-1, 1):
            blob(d, x + sx * 20, base_y - h, 22, 11, dark, light)
        leaflet(d, x, base_y - h, -90, 26, 6, dark, light)
        return finish_hi(img)
    tiers = [(0.35, 54, 3), (0.55, 64, 5), (0.75, 74, 5), (0.92, 80, 7)]
    count = {1: 1, 2: 2, 3: 3, 4: 4}[stage]
    scale = {1: 0.55, 2: 0.8, 3: 1.0, 4: 1.0}[stage]
    for i in range(count):
        frac, size, n = tiers[i] if stage > 1 else (1.0, 54, 3)
        y = base_y - h * frac if stage > 1 else base_y - h
        s = size * scale
        for side in (-1, 1):
            fan_leaf(d, x, y, s, max(3, n), side * 62, dark, light)
        if stage >= 3 and i == count - 1:
            fan_leaf(d, x, y - 4, s * 0.8, n, 0, dark, light)
    if stage == 4:
        cola(d, x, base_y - h + 18, 62, color, rnd)
        for fy in (0.45, 0.6, 0.75):
            for side in (-1, 1):
                ox = x + side * 14
                oy = base_y - h * fy
                cola(d, ox, oy, 22, color, rnd)
    return finish_hi(img)


# ---------------------------------------------------------------- products


def bud(color, seed, scale=1.0, cx=128, cy=140, tilt=0):
    img = new()
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    dark, light = shade(color, 0.55), lerp(color, (255, 255, 255), 0.28)
    pts = []
    for i in range(12):
        t = i / 11
        r = (30 - 14 * abs(t - 0.4) * 2) * scale
        y = cy + 70 * scale - 150 * scale * t
        pts += [(cx - r * 0.5 + rnd.uniform(-5, 5), y), (cx + r * 0.5 + rnd.uniform(-5, 5), y), (cx + rnd.uniform(-4, 4), y + 4)]
    for x, y in sorted(pts, key=lambda p: p[1], reverse=True):
        blob(d, x, y, 17 * scale, 20 * scale, dark, light, 6)
    # sugar leaf
    leaflet(d, cx - 6 * scale, cy + 60 * scale, 200, 46 * scale, 9 * scale, shade(GREEN, 0.5), shade(GREEN, 1.1))
    for _ in range(46):
        x, y = cx + rnd.uniform(-34, 34) * scale, cy + rnd.uniform(-70, 65) * scale
        a = rnd.uniform(0, 6.28)
        d.line((x, y, x + math.cos(a) * 12, y + math.sin(a) * 12), fill=(235, 125, 35, 255), width=2)
    for _ in range(80):
        x, y = cx + rnd.uniform(-34, 34) * scale, cy + rnd.uniform(-72, 66) * scale
        d.ellipse((x - 1.6, y - 1.6, x + 1.6, y + 1.6), fill=(255, 255, 255, 235))
    if tilt:
        img = img.rotate(tilt, resample=Image.BICUBIC, center=(cx, cy))
    return img


def img_bud(color, seed):
    return finish(bud(color, seed, 1.0, tilt=-12))


def img_ground(color, seed):
    img = new()
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    dark, light = shade(color, 0.45), lerp(color, (255, 255, 255), 0.1)
    for _ in range(900):
        px = rnd.gauss(128, 40)
        py = 190 - abs(rnd.gauss(0, 30)) * (1 - abs(px - 128) / 120) * 1.4 + rnd.uniform(0, 36)
        if abs(px - 128) > 85:
            continue
        r = rnd.uniform(2.5, 6)
        c = lerp(dark, light, rnd.random())
        d.polygon([(px + rnd.uniform(-r, r), py + rnd.uniform(-r, r)) for _ in range(5)], fill=c + (255,))
    for _ in range(18):
        px, py = rnd.gauss(128, 30), 190 + rnd.uniform(-20, 10)
        d.line((px, py, px + rnd.uniform(-10, 10), py + rnd.uniform(-8, 0)), fill=(110, 90, 50, 255), width=2)
    return finish(img)


def bag_shape(img, tint=(255, 255, 255)):
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle((52, 60, 204, 230), 12, fill=tint + (70,), outline=(235, 240, 245, 210), width=4)
    d.rectangle((52, 60, 204, 82), fill=(230, 235, 240, 140))
    d.line((56, 74, 200, 74), fill=(120, 190, 120, 255), width=4)
    d.line((52, 232, 204, 232), fill=(235, 240, 245, 200), width=3)


def img_bag_empty():
    img = new()
    bag_shape(img)
    return finish(img)


def img_weed_bag(color, seed):
    img = new()
    bag_shape(img)
    b = bud(color, seed, 0.7, cx=128, cy=170)
    img.alpha_composite(b)
    def front(f):
        f.rounded_rectangle((52, 60, 204, 230), 12, fill=(255, 255, 255, 36), outline=(235, 240, 245, 210), width=4)
        f.rectangle((92, 98, 164, 126), fill=(250, 250, 245, 235), outline=(60, 60, 60, 255), width=2)
        f.rectangle((92, 98, 164, 108), fill=color + (255,))
    overlay(img, front)
    return finish(img)


def img_joint(color, lit=False):
    img = new()
    d = ImageDraw.Draw(img, "RGBA")
    a = math.radians(-38)
    ca, sa = math.cos(a), math.sin(a)

    def tr(x, y):
        return (60 + x * ca - y * sa + 0, 210 + x * sa + y * ca)
    L = 190
    pts = [tr(0, -4), tr(40, -5), tr(L, -13), tr(L + 4, -9), tr(L + 4, 9), tr(L, 13), tr(40, 5), tr(0, 4)]
    paper = lerp(WHITE, color, 0.10)
    shaded_poly(d, [tr(0, -4), tr(L, -13), tr(L, 13), tr(0, 4)], shade(paper, 0.78), paper, 7)
    # crutch / filter
    shaded_poly(d, [tr(0, -4), tr(34, -5), tr(34, 5), tr(0, 4)], (150, 110, 70), (200, 160, 110), 4)
    # seam and wrinkles
    d.line((*tr(36, 2), *tr(L - 8, 9)), fill=(200, 200, 195, 255), width=2)
    for t in (60, 95, 130, 160):
        d.line((*tr(t, -7 - t * 0.02), *tr(t + 4, 5)), fill=(210, 210, 205, 200), width=2)
    # tip twist
    if lit:
        d.polygon([tr(L - 4, -13), tr(L + 10, -12), tr(L + 10, 12), tr(L - 4, 13)], fill=(95, 92, 90, 255))
        d.polygon([tr(L + 8, -12), tr(L + 18, -8), tr(L + 18, 8), tr(L + 8, 12)], fill=(210, 60, 20, 255))
        ex, ey = tr(L + 16, 0)
        d.ellipse((ex - 9, ey - 9, ex + 9, ey + 9), fill=(255, 150, 40, 255))
        d.ellipse((ex - 4, ey - 4, ex + 4, ey + 4), fill=(255, 235, 150, 255))

        def smoke(f):
            for i in range(6):
                f.ellipse((ex + 6 + i * 3 - 7 - i, ey - 24 - i * 14 - 7 - i, ex + 6 + i * 3 + 7 + i, ey - 24 - i * 14 + 7 + i),
                          fill=(215, 215, 220, 130 - i * 18))
        overlay(img, smoke)
    else:
        d.polygon([tr(L, -13), tr(L + 22, -3), tr(L + 22, 3), tr(L, 13)], fill=shade(paper, 0.92) + (255,))
        d.line((*tr(L + 4, -12), *tr(L + 20, 0)), fill=(200, 200, 195, 255), width=2)
    return finish(img)


def img_brick(color, seed):
    img = new()
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    top = [(40, 100), (150, 80), (222, 100), (112, 124)]
    front = [(40, 100), (112, 124), (112, 200), (40, 176)]
    side = [(112, 124), (222, 100), (222, 176), (112, 200)]
    dark = shade(color, 0.4)
    shaded_poly(d, top, shade(color, 0.7), lerp(color, (255, 255, 255), 0.1), 4)
    shaded_poly(d, front, shade(dark, 0.9), shade(dark, 1.3), 4)
    shaded_poly(d, side, shade(dark, 0.7), shade(dark, 1.1), 4)
    for _ in range(260):
        x, y = rnd.uniform(60, 200), rnd.uniform(90, 120)
        d.point((x, y), fill=lerp(dark, color, rnd.random()) + (255,))
        d.ellipse((x - 2, y - 1, x + 2, y + 1), fill=lerp(dark, color, rnd.random()) + (255,))
    # plastic wrap and tape
    overlay(img, lambda f: (f.polygon(front, fill=(200, 220, 230, 40)), f.polygon(side, fill=(200, 220, 230, 30))))
    d.polygon([(100, 118), (130, 111), (130, 190), (100, 196)], fill=(150, 105, 55, 235))
    d.polygon([(170, 112), (200, 106), (200, 168), (170, 176)], fill=(150, 105, 55, 235))
    return finish(img)


def img_rolling_paper():
    img = new()
    d = ImageDraw.Draw(img)
    paint = rrect(d, (70, 70, 186, 196), 8, (205, 200, 185), (250, 248, 238))
    img.paste(paint[0], (0, 0), paint[1])
    d.rectangle((70, 70, 186, 96), fill=(60, 140, 200, 255))
    d.rectangle((70, 150, 186, 160), fill=(230, 120, 40, 255))
    for yy in range(108, 142, 8):
        d.line((88, yy, 168, yy), fill=(150, 150, 150, 255), width=3)
    d.polygon([(186, 70), (206, 82), (206, 190), (186, 196)], fill=(215, 210, 195, 255))
    return finish(img)


def img_joint_box():
    img = new()
    d = ImageDraw.Draw(img)
    # joints poking out of the open box
    for i, (x, ang) in enumerate(((98, -14), (122, -4), (146, 6), (170, 16))):
        a = math.radians(ang - 90)
        x0, y0 = x, 118
        x1, y1 = x0 + math.cos(a) * 60, y0 + math.sin(a) * 60
        d.line((x0, y0, x1, y1), fill=(240, 238, 228, 255), width=11)
        d.line((x0 - 3, y0 - 2, x1 - 3, y1 - 2), fill=(255, 255, 250, 255), width=3)
        d.ellipse((x1 - 6, y1 - 6, x1 + 6, y1 + 6), fill=(225, 222, 210, 255))
    paste_rrect(img, (44, 112, 212, 222), 10, (24, 70, 44), (52, 130, 84))
    d.rectangle((44, 112, 212, 126), fill=(18, 52, 34, 255))
    d.rectangle((44, 124, 212, 128), fill=(214, 176, 70, 255))
    d.rectangle((44, 206, 212, 210), fill=(214, 176, 70, 255))
    d.rounded_rectangle((100, 146, 156, 190), 8, outline=(214, 176, 70, 255), width=4)
    fan_leaf(d, 128, 186, 22, 5, 0, (214, 176, 70), (240, 210, 110))
    d.ellipse((120, 128, 136, 144), fill=(214, 176, 70, 255))
    return finish(img)


def img_lighter():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (92, 96, 164, 226), 12, (130, 20, 20), (235, 60, 50))
    paste_rrect(img, (92, 68, 164, 108), 6, (110, 110, 118), (225, 228, 235))
    d.rectangle((110, 52, 146, 68), fill=(90, 90, 96, 255))
    d.polygon([(120, 52), (128, 22), (138, 52)], fill=(255, 160, 40, 255))
    d.polygon([(125, 52), (129, 34), (134, 52)], fill=(255, 235, 140, 255))
    d.rectangle((108, 150, 148, 170), fill=(255, 255, 255, 210))
    return finish(img)


def img_seed(color, seed):
    img = new()
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    paste_rrect(img, (44, 70, 160, 224), 6, (190, 175, 140), (245, 235, 205))
    d.rectangle((44, 70, 160, 110), fill=color + (255,))
    fan_leaf(d, 102, 190, 36, 5, 0, shade(GREEN, 0.6), shade(GREEN, 1.1))
    d.polygon([(44, 70), (160, 70), (152, 58), (52, 58)], fill=shade(color, 0.7) + (255,))
    for i, (sx, sy) in enumerate(((190, 190), (214, 214), (196, 228))):
        blob(d, sx, sy, 17, 11, (90, 65, 35), (170, 135, 90), 6)
        d.line((sx - 12, sy - 2, sx + 12, sy + 2), fill=(60, 45, 25, 255), width=2)
    return finish(img)


def img_fert(label, kind):
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (62, 58, 194, 226), 10, (200, 195, 180), (245, 240, 225))
    d.rectangle((62, 58, 194, 78), fill=(180, 175, 160, 255))
    d.rectangle((62, 112, 194, 186), fill=label + (255,))
    d.ellipse((104, 126, 152, 172), fill=(255, 255, 255, 230))
    if kind == "yield":
        d.polygon([(128, 128), (134, 142), (150, 144), (138, 154), (142, 170), (128, 160), (114, 170), (118, 154), (106, 144), (122, 142)], fill=label + (255,))
    elif kind == "speed":
        d.polygon([(134, 128), (116, 152), (128, 152), (122, 172), (144, 144), (130, 144)], fill=label + (255,))
    else:
        d.polygon([(128, 130), (146, 142), (146, 160), (128, 172), (110, 160), (110, 142)], fill=label + (255,))
    return finish(img)


def img_pesticide():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (82, 100, 168, 228), 14, (150, 30, 30), (230, 80, 70))
    d.rectangle((92, 140, 158, 196), fill=(250, 245, 235, 255))
    d.ellipse((111, 150, 139, 178), fill=(30, 30, 30, 255))
    for a in range(0, 360, 60):
        d.line((125, 164, 125 + math.cos(math.radians(a)) * 22, 164 + math.sin(math.radians(a)) * 22), fill=(30, 30, 30, 255), width=3)
    paste_rrect(img, (100, 70, 150, 106), 6, (200, 200, 205), (250, 250, 255))
    d.polygon([(150, 76), (206, 70), (206, 88), (150, 92)], fill=(235, 235, 240, 255))
    d.polygon([(150, 100), (178, 112), (170, 132), (150, 120)], fill=(30, 30, 35, 255))
    return finish(img)


def img_watering_can():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (66, 100, 170, 214), 12, (40, 100, 170), (110, 170, 230))
    d.polygon([(170, 124), (226, 70), (236, 82), (176, 160)], fill=(70, 130, 200, 255))
    d.rounded_rectangle((218, 56, 244, 88), 6, fill=(150, 195, 240, 255))
    d.arc((46, 90, 118, 190), 90, 270, fill=(30, 80, 140, 255), width=12)
    d.ellipse((72, 90, 164, 114), fill=(20, 60, 110, 255))
    d.ellipse((80, 94, 156, 110), fill=(80, 150, 220, 255))
    return finish(img)


def img_pot():
    img = new()
    d = ImageDraw.Draw(img)
    d.polygon([(52, 108), (204, 108), (184, 226), (72, 226)], fill=(176, 92, 54, 255))
    paste = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    paste_rrect(paste, (44, 84, 212, 122), 8, (150, 75, 42), (214, 126, 84))
    img.alpha_composite(paste)
    d.ellipse((58, 86, 198, 106), fill=(60, 40, 25, 255))
    return finish(img)


def img_tent():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (50, 40, 206, 228), 8, (14, 14, 16), (52, 54, 58))
    d.rectangle((74, 70, 182, 206), fill=(185, 190, 196, 255), outline=(30, 30, 30, 255), width=4)
    d.line((128, 70, 128, 206), fill=(60, 60, 62, 255), width=4)
    for yy in range(80, 200, 14):
        d.line((124, yy, 132, yy), fill=(200, 200, 200, 255), width=3)
    d.rectangle((60, 48, 196, 60), fill=(20, 20, 22, 255))
    for xx in (80, 100, 120, 140, 160):
        d.rectangle((xx, 50, xx + 12, 56), fill=(90, 90, 94, 255))
    return finish(img)


def img_waterer():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (62, 86, 194, 218), 14, (36, 96, 170), (100, 170, 235))
    d.rectangle((108, 60, 148, 90), fill=(160, 160, 168, 255))
    d.rectangle((120, 40, 136, 62), fill=(120, 120, 128, 255))
    d.rectangle((70, 150, 186, 164), fill=(25, 70, 125, 255))
    d.line((194, 190, 224, 190), fill=(150, 150, 158, 255), width=10)
    d.ellipse((218, 184, 232, 206), fill=(120, 200, 255, 255))
    return finish(img)


def img_table():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (30, 110, 226, 138), 6, (110, 72, 36), (190, 140, 84))
    for xx in (46, 194):
        paste_rrect(img, (xx, 138, xx + 16, 226), 3, (90, 58, 28), (150, 105, 60))
    paste_rrect(img, (54, 82, 114, 112), 8, (150, 150, 158), (230, 232, 238))
    d.ellipse((66, 68, 102, 84), fill=(90, 90, 98, 255))
    paste_rrect(img, (132, 90, 200, 112), 4, (24, 26, 30), (70, 72, 78))
    d.rectangle((142, 96, 176, 106), fill=(120, 220, 130, 255))
    return finish(img)


def img_press():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (46, 200, 210, 230), 6, (60, 62, 68), (140, 144, 152))
    d.rectangle((70, 70, 90, 204), fill=(100, 104, 112, 255))
    d.rectangle((166, 70, 186, 204), fill=(100, 104, 112, 255))
    paste_rrect(img, (62, 52, 194, 80), 6, (70, 72, 78), (150, 154, 162))
    d.rectangle((118, 80, 138, 150), fill=(170, 172, 180, 255))
    paste_rrect(img, (92, 150, 164, 168), 4, (90, 92, 98), (190, 194, 200))
    d.rectangle((40, 58, 74, 68), fill=(200, 60, 40, 255))
    return finish(img)


def img_lockup():
    img = new()
    d = ImageDraw.Draw(img)
    paste_rrect(img, (40, 80, 216, 220), 8, (40, 44, 50), (110, 116, 126))
    d.rectangle((40, 140, 216, 150), fill=(30, 32, 36, 255))
    for xx in (56, 190):
        d.rectangle((xx, 84, xx + 8, 216), fill=(70, 74, 82, 255))
    d.rounded_rectangle((108, 120, 148, 170), 6, fill=(220, 180, 50, 255), outline=(120, 90, 20, 255), width=3)
    d.arc((112, 92, 144, 134), 180, 360, fill=(190, 192, 198, 255), width=8)
    d.ellipse((122, 138, 134, 150), fill=(60, 40, 10, 255))
    return finish(img)


# ---------------------------------------------------------------- item table

BASE_ITEMS = [
    # key, display name, fallback material, lore, texture function
    ("pot", "<green>Grow Pot", "FLOWER_POT", ["<gray>Place it, then plant a seed."], lambda: img_pot()),
    ("tent", "<green>Grow Tent", "GREEN_STAINED_GLASS", ["<gray>Pots nearby grow in optimal conditions."], lambda: img_tent()),
    ("waterer", "<aqua>Auto-Waterer", "CAULDRON", ["<gray>Waters pots around it.", "<gray>Fill it with water buckets."], lambda: img_waterer()),
    ("processing_table", "<gold>Processing Table", "SMITHING_TABLE", ["<gray>Bag, grind and roll."], lambda: img_table()),
    ("press", "<gray>Weed Press", "STONECUTTER", ["<gray>Presses ground weed into bricks."], lambda: img_press()),
    ("lockup", "<dark_gray>Secret Lockup", "BARREL", ["<gray>Sneak + right-click to open."], lambda: img_lockup()),
    ("watering_can", "<aqua>Watering Can", "BUCKET", ["<gray>Right-click water to refill."], lambda: img_watering_can()),
    ("fertilizer_yield", "<yellow>Yield Fertilizer", "BONE_MEAL", ["<gray>Apply early. More buds."], lambda: img_fert((230, 180, 40), "yield")),
    ("fertilizer_speed", "<aqua>Speed Fertilizer", "BONE_MEAL", ["<gray>Apply early. Faster growth."], lambda: img_fert((60, 170, 220), "speed")),
    ("fertilizer_resist", "<green>Resist Fertilizer", "BONE_MEAL", ["<gray>Apply early. Fewer pests."], lambda: img_fert((70, 170, 80), "resist")),
    ("pesticide", "<red>Pesticide Spray", "POTION", ["<gray>Cures an infestation."], lambda: img_pesticide()),
    ("rolling_paper", "<white>Rolling Paper", "PAPER", [], lambda: img_rolling_paper()),
    ("joint_box", "<gold>Joint Box", "CHEST", ["<gray>Holds up to 64 joints.", "<gray>Right-click to open.", "<gray>Sneak + right-click to take one out."], lambda: img_joint_box()),
    ("lighter", "<red>Lighter", "FLINT", ["<gray>Needed to light a joint."], lambda: img_lighter()),
    ("bag_empty", "<white>Empty Bag", "PAPER", [], lambda: img_bag_empty()),
    ("plant_dead", "<dark_gray>Withered Plant", "DEAD_BUSH", [], lambda: plant(0, GREEN, dead=True)),
]
# generic (no strain) versions of the product items; strain variants are added below
PRODUCTS = [
    ("bud", "<green>Bud", "GREEN_DYE", img_bud),
    ("weed_bag", "<green>Bagged Weed", "SUGAR", img_weed_bag),
    ("ground", "<dark_green>Ground Weed", "LIME_DYE", img_ground),
    ("joint", "<green>Joint", "STICK", lambda c, s: img_joint(c)),
    ("joint_lit", "<gold>Lit Joint", "BLAZE_ROD", lambda c, s: img_joint(c, True)),
    ("brick", "<dark_green>Weed Brick", "MOSS_BLOCK", img_brick),
]
STAGE_NAMES = ["Seedling", "Sprout", "Young Plant", "Growing Plant", "Flowering Plant"]
STAGE_MATS = ["WHEAT_SEEDS", "SHORT_GRASS", "FERN", "LARGE_FERN", "SWEET_BERRY_BUSH"]


def q(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def plain(name):
    out = name
    while "<" in out:
        i = out.index("<")
        j = out.index(">", i)
        out = out[:i] + out[j + 1:]
    return out


def main():
    tex = os.path.join(ROOT, "itemsadder", NS, "textures", "item")
    cfg = os.path.join(ROOT, "itemsadder", NS, "configs")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(cfg, exist_ok=True)
    for f in os.listdir(tex):
        if f.endswith(".png"):
            os.remove(os.path.join(tex, f))

    items = []  # key, name, material, lore, base, image

    for key, name, mat, lore, fn in BASE_ITEMS:
        items.append((key, name, mat, lore, None, fn()))
    for key, name, mat, fn in PRODUCTS:
        items.append((key, name, mat, [], None, fn(GREEN, 1)))
    for i in range(5):
        items.append((f"plant_stage_{i}", f"<green>{STAGE_NAMES[i]}", STAGE_MATS[i], [], None, plant(i, GREEN)))

    strain_stage_keys = {}
    for sid, sname, col, *_rest in STRAINS:
        c = rgb(col)
        seed = sum(ord(ch) for ch in sid)
        items.append((f"seed_{sid}", f"<white>{sname} Seeds", "WHEAT_SEEDS", ["<gray>Plant in a grow pot."], None, img_seed(c, seed)))
        for key, name, mat, fn in PRODUCTS:
            items.append((f"{key}_{sid}", name, mat, [], key, fn(c, seed)))
        keys = []
        for i in range(5):
            k = f"plant_{sid}_stage_{i}"
            keys.append(k)
            items.append((k, f"<green>{sname} {STAGE_NAMES[i]}", STAGE_MATS[i], [], f"plant_stage_{i}", plant(i, c)))
        strain_stage_keys[sid] = keys

    plugin = [
        "# Logical item keys used by GrowOps.",
        "# 'itemsadder' = the ItemsAdder id (namespace:id). If ItemsAdder is missing or the id does not exist,",
        "# the plugin falls back to 'material' with the name and lore below.",
        "# 'base' marks a visual variant (per-strain model) of another item. Change any 'itemsadder' value",
        "# to point at your own ItemsAdder items.",
        "items:",
    ]
    ia = ["info:", f"  namespace: {NS}", "items:"]
    for key, name, mat, lore, base, image in items:
        plugin += [f"  {key}:", f"    itemsadder: {q(NS + ':' + key)}", f"    material: {mat}", f"    name: {q(name)}"]
        if base:
            plugin.append(f"    base: {base}")
        if lore:
            plugin.append("    lore:")
            plugin += [f"      - {q(l)}" for l in lore]
        ia += [f"  {key}:", f"    display_name: {q(plain(name))}", "    resource:", "      material: PAPER",
               "      generate: true", "      textures:", f"        - item/{key}.png"]
        image.save(os.path.join(tex, key + ".png"))

    res = os.path.join(ROOT, "src", "main", "resources")
    with open(os.path.join(res, "items.yml"), "w") as f:
        f.write("\n".join(plugin) + "\n")
    with open(os.path.join(cfg, "items.yml"), "w") as f:
        f.write("\n".join(ia) + "\n")

    sy = [
        "# Each strain automatically gets a seed item \"seed_<id>\" and per-strain bud/bag/ground/joint/brick/plant models.",
        "# effects: potion effects given when a joint of this strain is smoked. Format NAME or NAME:level.",
        "#   Duration = joint.duration-seconds x quality (see config.yml). Optional third part caps seconds: NAME:level:maxSeconds",
        "# stage-items: the 5 plant models (seed ... flowering). Remove the line to use the generic plant models.",
        "strains:",
    ]
    for sid, sname, col, grow, water, yld, sds, mut, decay, opt, qual, eff in STRAINS:
        sy += [
            f"  {sid}:",
            f"    name: {q(sname)}",
            f"    color: {q(col)}",
            f"    grow-minutes: {grow}",
            f"    water-per-minute: {water}",
            f"    yield: {{ min: {yld[0]}, max: {yld[1]} }}",
            f"    seeds: {{ min: {sds[0]}, max: {sds[1]} }}",
            f"    mutation: {{ chance: {mut[0]}, strain: {mut[1]} }}",
            f"    decay-minutes: {decay}",
            f"    requires-optimal: {'true' if opt else 'false'}",
            f"    base-quality: {qual}",
            "    effects: [" + ", ".join(q(e) for e in eff) + "]",
            "    stage-items: [" + ", ".join(strain_stage_keys[sid]) + "]",
        ]
    with open(os.path.join(res, "strains.yml"), "w") as f:
        f.write("\n".join(sy) + "\n")
    print(f"{len(items)} items, {len(STRAINS)} strains written")


if __name__ == "__main__":
    main()
