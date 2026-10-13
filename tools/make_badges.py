"""Build the rank badge glyph textures from the Saturn logo.

    python tools/make_badges.py <logo.png>

Writes src/main/resources/assets/solarclient/textures/font/badge_<role>.png
(nametags) and badge_<role>_tab.png (tab list). The glyph cell is 48 texels
tall for an 8px font line (6 texels per pixel), matching font/badges.json
(height 8, ascent 7). The logo is drawn SIZES[...] texels tall and centred
on the cap height of the name (the visual middle is 3.5px down the line).
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

CELL_H = 48
SIZES = {           # logo height in texels (6 per on-screen px), per file suffix
    '':     28,     # nametags
    '_tab': 34,     # tab list
}
CENTER_Y = 21       # 3.5px * 6 texels: middle of the capital letters
PAD_X = 2
THICKEN = 11        # outline growth (source px) so it survives the downscale
ROLES = {
    'member':    (255, 255, 255),
    'solarplus': (255, 204, 51),
    'verified':  (96, 170, 255),
    'admin':     (255, 72, 72),
    'owner':     (168, 110, 255),
}
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                   'assets', 'solarclient', 'textures', 'font')


def dilate(mask, r):
    out = mask.copy()
    h, w = mask.shape
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r:
                continue
            src = mask[max(0, -dy):h - max(0, dy), max(0, -dx):w - max(0, dx)]
            out[max(0, dy):h - max(0, -dy), max(0, dx):w - max(0, -dx)] |= src
    return out


def label(free):
    im = Image.fromarray(np.where(free, 1, 0).astype(np.int32), 'I')
    nxt = 2
    while True:
        idx = np.argwhere(np.asarray(im) == 1)
        if not len(idx):
            break
        y, x = idx[0]
        ImageDraw.floodfill(im, (int(x), int(y)), nxt)
        nxt += 1
    arr = np.asarray(im).copy()
    arr[arr > 0] -= 1
    return arr, nxt - 2


def main(src_path):
    src = Image.open(src_path).convert('RGBA')
    flat = Image.new('RGBA', src.size, (255, 255, 255, 255))
    flat.alpha_composite(src)
    lum = np.asarray(flat.convert('L')).astype(np.float32)
    black = dilate(lum < 128, THICKEN)

    # White regions: the outside and the gaps inside the ring become
    # transparent; the ring band stays white; the planet takes the role colour.
    labels, n = label(~black)
    h, w = black.shape
    border = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))) - {0}
    comps = []
    for i in range(1, n + 1):
        if i in border:
            continue
        ys, xs = np.where(labels == i)
        if len(ys) >= 200:
            comps.append(dict(id=i, area=len(ys), x0=xs.min(), x1=xs.max() + 1))
    ring = max(comps, key=lambda c: c['x1'] - c['x0'])
    rest = [c for c in comps if c is not ring]
    top = max(rest, key=lambda c: c['area'])
    planet = [c for c in rest if c is top or
              (top['x0'] <= (c['x0'] + c['x1']) / 2 <= top['x1'] and c['area'] > 0.02 * top['area'])]
    planet_mask = np.isin(labels, [c['id'] for c in planet])
    ring_mask = labels == ring['id']
    ys, xs = np.where(black | planet_mask | ring_mask)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1

    for suffix, content_h in SIZES.items():
        for role, col in ROLES.items():
            rgba = np.zeros((h, w, 4), np.float32)
            rgba[ring_mask] = (255, 255, 255, 255)
            rgba[planet_mask] = (*col, 255)
            rgba[black] = (0, 0, 0, 255)
            crop = rgba[y0:y1, x0:x1]
            crop[..., :3] *= crop[..., 3:4] / 255.0
            ch, cw = crop.shape[:2]
            tw = max(1, round(cw * content_h / ch))
            small = np.stack([np.asarray(Image.fromarray(crop[..., k]).resize((tw, content_h), Image.BOX))
                              for k in range(4)], axis=2)
            a = small[..., 3:4]
            small[..., :3] = np.where(a > 0, small[..., :3] * 255.0 / np.maximum(a, 1e-3), 0)
            small[..., 3] = np.where(small[..., 3] < 40, 0, np.where(small[..., 3] > 200, 255, small[..., 3]))
            cell = np.zeros((CELL_H, tw + PAD_X * 2, 4), np.float32)
            top_pad = CENTER_Y - content_h // 2
            cell[top_pad:top_pad + content_h, PAD_X:PAD_X + tw] = small
            Image.fromarray(np.clip(cell, 0, 255).astype(np.uint8), 'RGBA').save(
                os.path.join(OUT, f'badge_{role}{suffix}.png'))
        print(f'badges{suffix or ""}: {tw}x{content_h} logo in a {tw + PAD_X * 2}x{CELL_H} cell, top {top_pad}')


if __name__ == '__main__':
    main(sys.argv[1])
