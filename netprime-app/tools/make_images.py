"""Draws the app icon (adaptive + legacy), the loading-screen logo and the Google TV banner."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
import os, sys

root = sys.argv[1]
INTER = '/usr/share/fonts/opentype/inter/'
BLACK = INTER + 'InterDisplay-Black.otf'
BOLD = INTER + 'InterDisplay-Bold.otf'
MED = INTER + 'Inter-Medium.otf'
BG1, BG2 = (24, 24, 32), (8, 8, 12)
RED_TOP, RED_BOT = (255, 72, 92), (178, 7, 16)


def vgrad(w, h, top, bot):
    g = Image.new('RGB', (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        g.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return g.resize((w, h))


def dgrad(w, h, a, b):
    """Diagonal gradient, top-left a -> bottom-right b."""
    g = Image.new('RGB', (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2)
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return g


def letter_mask(size, box, text='N', font=BLACK, scale=0.74):
    m = Image.new('L', size, 0)
    d = ImageDraw.Draw(m)
    f = ImageFont.truetype(font, int((box[3] - box[1]) * scale))
    x0, y0, x1, y1 = d.textbbox((0, 0), text, font=f)
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    d.text((cx - (x1 - x0) / 2 - x0, cy - (y1 - y0) / 2 - y0), text, font=f, fill=255)
    return m


def mark(s, rounded=True, pad=0.0, bg=True):
    """The N logo: red gradient letter with a soft glow on a dark tile."""
    S = s * 4  # draw large, scale down for smooth edges
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    if bg:
        tile = dgrad(S, S, BG1, BG2).convert('RGBA')
        m = Image.new('L', (S, S), 0)
        if rounded:
            ImageDraw.Draw(m).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.23), fill=255)
        else:
            m.paste(255, (0, 0, S, S))
        img.paste(tile, (0, 0), m)
        # subtle top highlight border
        hl = Image.new('L', (S, S), 0)
        if rounded:
            ImageDraw.Draw(hl).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.23), outline=40, width=max(2, S // 96))
        img = Image.alpha_composite(img, Image.merge('RGBA', [Image.new('L', (S, S), 255)] * 3 + [hl]))
    p = int(S * pad)
    lm = letter_mask((S, S), (p, p, S - p, S - p))
    glow = lm.filter(ImageFilter.GaussianBlur(S * 0.06)).point(lambda v: int(v * 0.55))
    glow_img = Image.new('RGBA', (S, S), (229, 9, 20, 0))
    glow_img.putalpha(glow)
    img = Image.alpha_composite(img, glow_img)
    red = vgrad(S, S, RED_TOP, RED_BOT).convert('RGBA')
    red.putalpha(lm)
    img = Image.alpha_composite(img, red)
    return img.resize((s, s), Image.LANCZOS)


dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, k in dens.items():
    d = f'{root}/res/mipmap-{name}'
    os.makedirs(d, exist_ok=True)
    mark(int(48 * k), pad=0.12).save(f'{d}/ic_launcher.png')
    # Adaptive icon foreground: 108dp canvas, letter inside the 66dp safe zone.
    mark(int(108 * k), bg=False, pad=0.3).save(f'{d}/ic_launcher_foreground.png')

os.makedirs(f'{root}/res/mipmap-anydpi-v26', exist_ok=True)
open(f'{root}/res/mipmap-anydpi-v26/ic_launcher.xml', 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@color/icon_bg"/>\n'
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
    '</adaptive-icon>\n')

# Loading-screen logo (104dp shown, drawn at xxhdpi)
os.makedirs(f'{root}/res/drawable-xxhdpi', exist_ok=True)
mark(312, pad=0.12).save(f'{root}/res/drawable-xxhdpi/logo_mark.png')

# Google TV banner: 320x180dp, drawn at xhdpi (640x360 px)
W, H = 640, 360
b = dgrad(W, H, (22, 22, 30), (6, 6, 9)).convert('RGBA')
glow = Image.new('L', (W, H), 0)
ImageDraw.Draw(glow).ellipse((-60, 40, 260, 320), fill=70)
glow = glow.filter(ImageFilter.GaussianBlur(70))
gl = Image.new('RGBA', (W, H), (229, 9, 20, 0)); gl.putalpha(glow)
b = Image.alpha_composite(b, gl)
m = mark(150, pad=0.1)
b.alpha_composite(m, (52, (H - 150) // 2))
d = ImageDraw.Draw(b)
f1 = ImageFont.truetype(BOLD, 64)
f2 = ImageFont.truetype(MED, 26)
d.text((228, 126), 'NetPrime', font=f1, fill=(255, 255, 255))
d.text((232, 208), 'Movies & Shows', font=f2, fill=(161, 161, 170))
os.makedirs(f'{root}/res/drawable-xhdpi', exist_ok=True)
b.convert('RGB').save(f'{root}/res/drawable-xhdpi/tv_banner.png')
