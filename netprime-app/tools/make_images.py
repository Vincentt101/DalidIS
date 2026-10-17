"""Draws the app icons and the TV banner."""
from PIL import Image, ImageDraw, ImageFont
import os, sys
root = sys.argv[1]
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
BG = (20, 20, 24, 255); RED = (229, 9, 20, 255)

def centered(d, text, size, box):
    f = ImageFont.truetype(F, size)
    x0, y0, x1, y1 = d.textbbox((0, 0), text, font=f)
    d.text(((box[0] + box[2] - (x1 - x0)) / 2 - x0, (box[1] + box[3] - (y1 - y0)) / 2 - y0), text, font=f, fill=RED)

for name, s in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    os.makedirs(f'{root}/res/mipmap-{name}', exist_ok=True)
    im = Image.new('RGBA', (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, s - 1, s - 1), radius=s // 6, fill=BG)
    centered(d, 'N', int(s * 0.7), (0, 0, s, s))
    im.save(f'{root}/res/mipmap-{name}/ic_launcher.png')

os.makedirs(f'{root}/res/drawable-xhdpi', exist_ok=True)
b = Image.new('RGB', (320, 180), BG[:3]); d = ImageDraw.Draw(b)
centered(d, 'NetPrime', 44, (0, 0, 320, 180))
b.save(f'{root}/res/drawable-xhdpi/tv_banner.png')
