#!/usr/bin/env python3
"""Generates the plugin items.yml, the ItemsAdder pack (configs + placeholder textures).

Run from the project root:  python3 tools/generate_items.py
Replace the placeholder PNGs in itemsadder/growops/textures/item/ with your own art any time.
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "growops"

# key, display name (MiniMessage), fallback material, lore, base colour, shape
ITEMS = [
    # placeables
    ("pot", "<green>Grow Pot", "FLOWER_POT", ["<gray>Place it, then plant a seed."], (150, 90, 50), "pot"),
    ("tent", "<green>Grow Tent", "GREEN_STAINED_GLASS", ["<gray>Pots nearby grow in optimal conditions."], (40, 120, 60), "tent"),
    ("waterer", "<aqua>Auto-Waterer", "CAULDRON", ["<gray>Waters pots around it.", "<gray>Fill it with water buckets."], (60, 130, 200), "bucket"),
    ("processing_table", "<gold>Processing Table", "SMITHING_TABLE", ["<gray>Bag, grind and roll."], (160, 120, 60), "table"),
    ("press", "<gray>Weed Press", "STONECUTTER", ["<gray>Presses ground weed into bricks."], (110, 110, 120), "press"),
    ("lockup", "<dark_gray>Secret Lockup", "BARREL", ["<gray>Sneak + right-click to open."], (80, 60, 40), "box"),
    # tools / consumables
    ("watering_can", "<aqua>Watering Can", "BUCKET", ["<gray>Right-click water to refill."], (70, 150, 210), "can"),
    ("fertilizer_yield", "<yellow>Yield Fertilizer", "BONE_MEAL", ["<gray>Apply early. More buds."], (230, 200, 60), "bag"),
    ("fertilizer_speed", "<aqua>Speed Fertilizer", "BONE_MEAL", ["<gray>Apply early. Faster growth."], (80, 200, 230), "bag"),
    ("fertilizer_resist", "<green>Resist Fertilizer", "BONE_MEAL", ["<gray>Apply early. Fewer pests."], (90, 200, 90), "bag"),
    ("pesticide", "<red>Pesticide Spray", "POTION", ["<gray>Cures an infestation."], (200, 70, 70), "spray"),
    # processing chain
    ("bud", "<green>Bud", "GREEN_DYE", [], (70, 160, 70), "bud"),
    ("bag_empty", "<white>Empty Bag", "PAPER", [], (220, 220, 220), "bagflat"),
    ("weed_bag", "<green>Bagged Weed", "SUGAR", [], (200, 230, 200), "bagfull"),
    ("ground", "<dark_green>Ground Weed", "LIME_DYE", [], (60, 120, 50), "pile"),
    ("rolling_paper", "<white>Rolling Paper", "PAPER", [], (240, 240, 230), "paper"),
    ("joint", "<green>Joint", "STICK", [], (200, 190, 150), "joint"),
    ("brick", "<dark_green>Weed Brick", "MOSS_BLOCK", [], (50, 100, 45), "brick"),
    # plant models
    ("plant_stage_0", "<green>Seedling", "WHEAT_SEEDS", [], (120, 80, 40), "stage0"),
    ("plant_stage_1", "<green>Sprout", "SHORT_GRASS", [], (80, 170, 70), "stage1"),
    ("plant_stage_2", "<green>Young Plant", "FERN", [], (60, 160, 60), "stage2"),
    ("plant_stage_3", "<green>Growing Plant", "LARGE_FERN", [], (50, 150, 55), "stage3"),
    ("plant_stage_4", "<green>Ready Plant", "SWEET_BERRY_BUSH", [], (50, 140, 50), "stage4"),
    ("plant_dead", "<dark_gray>Withered Plant", "DEAD_BUSH", [], (120, 90, 50), "dead"),
]

STRAINS = [
    ("og_kush", "OG Kush", (76, 175, 80)),
    ("white_widow", "White Widow", (224, 224, 224)),
    ("purple_haze", "Purple Haze", (156, 39, 176)),
]
for sid, sname, col in STRAINS:
    ITEMS.append((f"seed_{sid}", f"<white>{sname} Seeds", "WHEAT_SEEDS", ["<gray>Plant in a grow pot."], col, "seed"))

W = H = 16


def canvas():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def px(c, x, y, col):
    if 0 <= x < W and 0 <= y < H:
        c[y][x] = (col[0], col[1], col[2], 255)


def rect(c, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(c, x, y, col)


def shade(col, f):
    return tuple(max(0, min(255, int(v * f))) for v in col)


def outline(c):
    out = [row[:] for row in c]
    for y in range(H):
        for x in range(W):
            if c[y][x][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and c[ny][nx][3] != 0:
                        out[y][x] = (20, 20, 20, 255)
                        break
    return out


def plant(c, height, leaves, top=None, dead=False):
    stem = (110, 80, 40) if dead else (40, 110, 40)
    leaf = (130, 100, 55) if dead else (60, 170, 60)
    for i in range(height):
        px(c, 7, 14 - i, stem)
        px(c, 8, 14 - i, stem)
    for k in range(leaves):
        y = 13 - k * 3
        px(c, 6, y, leaf); px(c, 5, y - 1, leaf); px(c, 9, y, leaf); px(c, 10, y - 1, leaf)
        if k > 1:
            px(c, 4, y - 1, leaf); px(c, 11, y - 1, leaf)
    if top:
        rect(c, 6, 14 - height - 1, 9, 14 - height + 1, top)
    rect(c, 5, 15, 10, 15, (100, 70, 40))


def draw(shape, col):
    c = canvas()
    d, l = shade(col, 0.7), shade(col, 1.25)
    if shape == "pot":
        rect(c, 3, 8, 12, 14, col); rect(c, 2, 6, 13, 8, l); rect(c, 4, 6, 11, 7, (70, 50, 30))
    elif shape == "tent":
        for i in range(7):
            rect(c, 7 - i, 3 + i, 8 + i, 3 + i, col if i % 2 == 0 else d)
        rect(c, 6, 10, 9, 13, (20, 60, 30))
    elif shape in ("bucket", "can"):
        rect(c, 4, 6, 11, 13, col); rect(c, 3, 5, 12, 6, l); rect(c, 5, 7, 10, 8, d)
        if shape == "can":
            rect(c, 12, 7, 14, 8, d)
    elif shape == "table":
        rect(c, 2, 5, 13, 7, l); rect(c, 3, 8, 4, 14, col); rect(c, 11, 8, 12, 14, col); rect(c, 5, 8, 10, 9, d)
    elif shape == "press":
        rect(c, 3, 3, 12, 5, d); rect(c, 7, 6, 8, 9, l); rect(c, 2, 10, 13, 13, col)
    elif shape == "box":
        rect(c, 2, 5, 13, 13, col); rect(c, 2, 8, 13, 8, d); rect(c, 7, 7, 8, 9, l)
    elif shape in ("bag", "bagflat", "bagfull"):
        rect(c, 4, 4, 11, 13, col); rect(c, 4, 4, 11, 5, d); rect(c, 6, 7, 9, 10, l if shape != "bagflat" else col)
    elif shape == "spray":
        rect(c, 5, 6, 10, 13, col); rect(c, 6, 3, 9, 5, d); rect(c, 10, 3, 13, 4, l)
    elif shape == "bud":
        for (x, y) in ((7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9), (8, 9)):
            px(c, x, y, col if (x + y) % 2 else l)
        rect(c, 7, 10, 8, 12, (110, 80, 40))
    elif shape == "pile":
        rect(c, 3, 10, 12, 12, col); rect(c, 5, 8, 10, 10, l); rect(c, 7, 6, 8, 8, col)
    elif shape == "paper":
        rect(c, 3, 5, 12, 11, col); rect(c, 3, 8, 12, 8, d)
    elif shape == "joint":
        for i in range(10):
            px(c, 3 + i, 12 - i // 2, col); px(c, 3 + i, 11 - i // 2, l)
        rect(c, 12, 6, 13, 7, (230, 120, 40))
    elif shape == "brick":
        rect(c, 2, 5, 13, 12, col); rect(c, 2, 8, 13, 8, d); rect(c, 7, 5, 7, 12, d)
    elif shape == "seed":
        for (x, y) in ((6, 6), (7, 6), (6, 7), (7, 7), (9, 9), (10, 9), (9, 10), (10, 10), (5, 10), (6, 10)):
            px(c, x, y, col);
        px(c, 6, 6, l); px(c, 9, 9, l)
    elif shape.startswith("stage"):
        n = int(shape[-1])
        plant(c, [2, 4, 7, 10, 12][n], [0, 1, 2, 3, 4][n], top=(180, 60, 200) if n == 4 else None)
    elif shape == "dead":
        plant(c, 6, 2, dead=True)
    return outline(c)


def write_png(path, canvas_):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in canvas_)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def q(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def main():
    tex_dir = os.path.join(ROOT, "itemsadder", NS, "textures", "item")
    cfg_dir = os.path.join(ROOT, "itemsadder", NS, "configs")
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(cfg_dir, exist_ok=True)

    plugin_lines = [
        "# Logical item keys used by GrowOps.",
        "# 'itemsadder' = the ItemsAdder id (namespace:id). If ItemsAdder is missing or the id does not exist,",
        "# the plugin falls back to 'material' with the name and lore below.",
        "# Change any 'itemsadder' value to point at your own ItemsAdder items.",
        "items:",
    ]
    ia_lines = [
        "info:",
        f"  namespace: {NS}",
        "items:",
    ]
    for key, name, mat, lore, col, shape in ITEMS:
        plugin_lines.append(f"  {key}:")
        plugin_lines.append(f"    itemsadder: {q(NS + ':' + key)}")
        plugin_lines.append(f"    material: {mat}")
        plugin_lines.append(f"    name: {q(name)}")
        if lore:
            plugin_lines.append("    lore:")
            for ln in lore:
                plugin_lines.append(f"      - {q(ln)}")
        plain = name
        for tag in ("<green>", "<aqua>", "<gold>", "<gray>", "<dark_gray>", "<yellow>", "<red>", "<white>", "<dark_green>"):
            plain = plain.replace(tag, "")
        ia_lines += [
            f"  {key}:",
            f"    display_name: {q(plain)}",
            "    resource:",
            "      material: PAPER",
            "      generate: true",
            "      textures:",
            f"        - item/{key}.png",
        ]
        write_png(os.path.join(tex_dir, key + ".png"), draw(shape, col))

    with open(os.path.join(ROOT, "src", "main", "resources", "items.yml"), "w") as f:
        f.write("\n".join(plugin_lines) + "\n")
    with open(os.path.join(cfg_dir, "items.yml"), "w") as f:
        f.write("\n".join(ia_lines) + "\n")
    print(f"{len(ITEMS)} items written")


if __name__ == "__main__":
    main()
