"""
Regenerate all Play Store graphics from source assets.

Run this whenever the icon, brand colors, or screenshots change.
It writes into playstore/graphics/ and is idempotent.

    python playstore/generate_graphics.py

Requirements:
    - Python 3.10+
    - Pillow (already present in the repo's dev env)
    - A TTF font available on the system (falls back to default)
"""

from __future__ import annotations

import os
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "graphics"
SHOT_SRC = ROOT.parent / "docs" / "assets" / "screenshots"
SHOT_OUT = OUT / "phone-screenshots"
OUT.mkdir(parents=True, exist_ok=True)
SHOT_OUT.mkdir(parents=True, exist_ok=True)

# Brand palette (matches ic_launcher_background.xml and docs/index.html).
INDIGO = (99, 102, 241)
VIOLET = (139, 92, 246)
DARK = (26, 19, 48)
OFF_WHITE = (245, 243, 255)
CAPTION_PURPLE = (196, 181, 253)


def _font(name: str, size: int) -> ImageFont.FreeTypeFont:
    """Find a system TTF. Falls back to Pillow's bitmap font if none match."""
    candidates = [
        Path("/c/Windows/Fonts") / name,
        Path("C:/Windows/Fonts") / name,
        Path("/usr/share/fonts/truetype/dejavu") / name,
    ]
    for c in candidates:
        try:
            return ImageFont.truetype(str(c), size)
        except OSError:
            continue
    return ImageFont.load_default()


def _vertical_gradient(w: int, h: int, top, bottom) -> Image.Image:
    """Linear top-to-bottom gradient. Cheap and good enough for a brand asset."""
    base = Image.new("RGB", (w, h), top)
    px = base.load()
    for y in range(h):
        t = y / max(1, h - 1)
        r = int(top[0] * (1 - t) + bottom[0] * t)
        g = int(top[1] * (1 - t) + bottom[1] * t)
        b = int(top[2] * (1 - t) + bottom[2] * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return base


def _diagonal_gradient(w: int, h: int, c1, c2, c3) -> Image.Image:
    """Three-stop diagonal gradient matching ic_launcher_background.xml."""
    base = Image.new("RGB", (w, h))
    px = base.load()
    max_d = math.hypot(w, h)
    for y in range(h):
        for x in range(w):
            t = math.hypot(x, y) / max_d
            if t < 0.5:
                k = t / 0.5
                r = int(c1[0] * (1 - k) + c2[0] * k)
                g = int(c1[1] * (1 - k) + c2[1] * k)
                b = int(c1[2] * (1 - k) + c2[2] * k)
            else:
                k = (t - 0.5) / 0.5
                r = int(c2[0] * (1 - k) + c3[0] * k)
                g = int(c2[1] * (1 - k) + c3[1] * k)
                b = int(c2[2] * (1 - k) + c3[2] * k)
            px[x, y] = (r, g, b)
    return base


def draw_icon(size: int) -> Image.Image:
    """Speech bubble with three caption bars, on the brand gradient."""
    img = _diagonal_gradient(size, size, INDIGO, VIOLET, DARK)
    d = ImageDraw.Draw(img)

    # Speech bubble, centered, occupying ~60% of the icon.
    pad = int(size * 0.18)
    bubble_w = size - 2 * pad
    bubble_h = int(bubble_w * 0.72)
    top = (size - bubble_h) // 2 - int(size * 0.03)
    left = pad
    right = left + bubble_w
    bottom = top + bubble_h
    radius = int(bubble_h * 0.28)
    d.rounded_rectangle(
        (left, top, right, bottom),
        radius=radius,
        fill=OFF_WHITE,
    )

    # Bubble tail — triangle hanging off the lower-left.
    tail_w = int(bubble_w * 0.18)
    tail_h = int(bubble_h * 0.22)
    tx = left + int(bubble_w * 0.22)
    d.polygon(
        [
            (tx, bottom - 2),
            (tx + tail_w, bottom - 2),
            (tx + int(tail_w * 0.25), bottom + tail_h),
        ],
        fill=OFF_WHITE,
    )

    # Three caption bars.
    bar_h = int(bubble_h * 0.13)
    bar_gap = int(bubble_h * 0.09)
    bars_total_h = bar_h * 3 + bar_gap * 2
    start_y = top + (bubble_h - bars_total_h) // 2
    bar_left = left + int(bubble_w * 0.12)
    bar_widths = [0.76, 0.60, 0.40]
    bar_colors = [INDIGO, VIOLET, CAPTION_PURPLE]
    for i, (w_ratio, col) in enumerate(zip(bar_widths, bar_colors)):
        bar_top = start_y + i * (bar_h + bar_gap)
        bar_right = bar_left + int(bubble_w * 0.76 * w_ratio)
        d.rounded_rectangle(
            (bar_left, bar_top, bar_right, bar_top + bar_h),
            radius=bar_h // 2,
            fill=col,
        )

    return img


def draw_feature_graphic(w: int = 1024, h: int = 500) -> Image.Image:
    """Wide brand banner for the Play Store feature graphic slot."""
    bg = _diagonal_gradient(w, h, INDIGO, VIOLET, DARK)

    # Soft blurred circles so the banner doesn't feel flat.
    blob = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(blob)
    bd.ellipse((w - 400, -180, w + 120, 320), fill=(139, 92, 246, 120))
    bd.ellipse((-160, h - 260, 260, h + 180), fill=(99, 102, 241, 110))
    blob = blob.filter(ImageFilter.GaussianBlur(80))
    bg.paste(blob, (0, 0), blob)

    # Icon on the left, scaled down from the 512 renderer so lines stay crisp.
    icon = draw_icon(768).resize((360, 360), Image.LANCZOS)
    bg.paste(icon, (60, (h - 360) // 2))

    d = ImageDraw.Draw(bg)
    title_font = _font("arialbd.ttf", 82)
    tag_font = _font("arial.ttf", 32)
    hint_font = _font("arial.ttf", 24)

    text_left = 450
    d.text((text_left, 138), "LiveCaptionN", fill=OFF_WHITE, font=title_font)
    d.text(
        (text_left, 238),
        "Live captions & translation",
        fill=CAPTION_PURPLE,
        font=tag_font,
    )
    d.text(
        (text_left, 280),
        "floating over any Android app",
        fill=CAPTION_PURPLE,
        font=tag_font,
    )
    d.text(
        (text_left, 344),
        "100% offline  |  ~59 languages  |  open source",
        fill=(220, 215, 255),
        font=hint_font,
    )
    return bg


def pad_screenshot(src: Path, dst: Path, target_ratio: float = 2.0) -> None:
    """
    Play Store phone screenshots must be 24-bit (no alpha) and cannot exceed
    2:1 aspect. We pad the sides with the brand dark so the original
    content isn't cropped.
    """
    im = Image.open(src).convert("RGB")
    w, h = im.size
    ratio = h / w
    if ratio > target_ratio:
        new_w = int(round(h / target_ratio))
        canvas = Image.new("RGB", (new_w, h), DARK)
        canvas.paste(im, ((new_w - w) // 2, 0))
    else:
        canvas = im
    canvas.save(dst, "PNG", optimize=True)


def main() -> None:
    icon = draw_icon(512)
    icon.save(OUT / "icon-512.png", "PNG", optimize=True)
    print("wrote", OUT / "icon-512.png")

    feature = draw_feature_graphic()
    feature.save(OUT / "feature-graphic-1024x500.png", "PNG", optimize=True)
    print("wrote", OUT / "feature-graphic-1024x500.png")

    order = [
        ("overlay_home.png", "01_overlay_home.png"),
        ("overlay_listening.png", "02_overlay_listening.png"),
        ("main.png", "03_main.png"),
        ("history.png", "04_history.png"),
    ]
    for src_name, dst_name in order:
        src = SHOT_SRC / src_name
        if not src.exists():
            print(f"!! missing source screenshot {src}")
            continue
        dst = SHOT_OUT / dst_name
        pad_screenshot(src, dst)
        print("wrote", dst)


if __name__ == "__main__":
    main()
