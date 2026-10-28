#!/usr/bin/env python3
"""Generate AutoTool mod icon: cyan swap arrows on dark plate."""
import struct, zlib, os

def chunk(tag, data):
    c = struct.pack('>I', len(data)) + tag + data
    return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

def png(path, w, h, pixel):
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            raw += bytes(pixel(x, y))
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

BG    = (22, 28, 38, 255)
CYAN  = (80, 225, 255, 255)
GREEN = (90, 232, 130, 255)

def tex(x, y):
    if x < 6 or x > 121 or y < 6 or y > 121:
        return (10, 14, 20, 255)
    base = BG
    # top arrow (pointing right): bar + head
    if 40 <= y <= 54:
        if 24 <= x <= 92:
            base = CYAN
        if 76 <= x <= 92 and (28 <= y <= 40 or 54 <= y <= 66):
            base = CYAN
    # bottom arrow (pointing left)
    if 74 <= y <= 88:
        if 36 <= x <= 104:
            base = GREEN
        if 36 <= x <= 52 and (62 <= y <= 74 or 88 <= y <= 100):
            base = GREEN
    return base

base = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'autotool')
png(os.path.join(base, 'icon.png'), 128, 128, tex)
print('icon written')
