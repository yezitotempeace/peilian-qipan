#!/usr/bin/env python3
"""生成 App 启动图标（纯标准库写 PNG，不依赖 PIL）。

图案：「陪练棋盘」—— 一块圆角木色棋盘，一黑一白两颗子（你一手、对手一手），
中间一支双向箭头表示来回对弈/陪练。用 3x 超采样做抗锯齿。

用法：python3 make_icons.py      # 直接覆盖 res/mipmap-*/ic_launcher.png
"""
import os
import struct
import zlib

WOOD_TOP = (236, 196, 138)
WOOD_BOTTOM = (208, 160, 100)
RIM = (146, 104, 58)
LINE = (58, 42, 21)
ARROW = (26, 92, 128)
SS = 3  # 超采样倍数


def write_png(path, w, h, rows):
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)


def render(size):
    """返回 size x size 的 RGBA 行列表。"""
    n = size * SS
    buf = [[(0, 0, 0, 0) for _ in range(n)] for _ in range(n)]
    feather = max(1.0, n * 0.004)

    def clamp01(v):
        return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)

    def paint(x, y, color, cov):
        if cov <= 0 or not (0 <= x < n and 0 <= y < n):
            return
        if cov > 1.0:
            cov = 1.0
        bg = buf[y][x]
        a = cov + bg[3] / 255.0 * (1.0 - cov)
        if a <= 0:
            return
        out = []
        for i in range(3):
            v = (color[i] * cov + bg[i] * (bg[3] / 255.0) * (1.0 - cov)) / a
            out.append(int(round(v)))
        buf[y][x] = (out[0], out[1], out[2], int(round(a * 255)))

    # ---------- 覆盖度函数（都带羽化边） ----------
    def cov_circle(x, y, cx, cy, r):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        return clamp01((r - d) / feather + 0.5)

    def cov_seg(x, y, ax, ay, bx, by, hw):
        vx, vy = bx - ax, by - ay
        wx, wy = x - ax, y - ay
        L2 = vx * vx + vy * vy
        t = 0.0 if L2 == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / L2))
        px, py = ax + t * vx, ay + t * vy
        d = ((x - px) ** 2 + (y - py) ** 2) ** 0.5
        return clamp01((hw - d) / feather + 0.5)

    def cov_tri(x, y, p0, p1, p2):
        # 三角形：三条边的有向距离同号才算在里面（顶点顺序顺/逆时针都支持）
        pts = (p0, p1, p2)
        ds = []
        for i in range(3):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % 3]
            cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            ds.append(cross / max(1e-6, ((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5))
        if min(ds) >= 0:
            inside = min(ds)
        elif max(ds) <= 0:
            inside = -max(ds)
        else:
            return 0.0
        return clamp01(inside / feather + 0.5)

    def cov_round_rect(x, y, x0, y0, x1, y1, r):
        cx = min(max(x, x0 + r), x1 - r)
        cy = min(max(y, y0 + r), y1 - r)
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if x0 + r <= x <= x1 - r or y0 + r <= y <= y1 - r:
            inside = (x0 - feather <= x <= x1 + feather) and (y0 - feather <= y <= y1 + feather)
            if inside and (x0 <= x <= x1) and (y0 <= y <= y1):
                return 1.0
        return clamp01((r - d) / feather + 0.5)

    def stone(cx, cy, r, dark):
        for y in range(max(0, int(cy - r - 2)), min(n, int(cy + r + 3))):
            for x in range(max(0, int(cx - r - 2)), min(n, int(cx + r + 3))):
                cov = cov_circle(x, y, cx, cy, r)
                if cov <= 0:
                    continue
                # 左上高光
                t = clamp01(((cx - r * 0.38 - x) + (cy - r * 0.38 - y)) / (r * 2.2))
                if dark:
                    base = int(24 + 74 * t)
                    col = (base, base, base)
                else:
                    base = int(206 + 49 * t)
                    col = (base, base, min(255, base))
                paint(x, y, col, cov)
                edge = clamp01(((r - 0.0) - ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5) / feather + 0.5)
                ring = clamp01((cov_circle(x, y, cx, cy, r * 0.90) - cov) / 1.0)
                if ring > 0:
                    paint(x, y, LINE, ring * 0.35 * edge)

    # ---------- 画 ----------
    x0, y0, x1, y1 = n * 0.03, n * 0.03, n * 0.97, n * 0.97
    rr = n * 0.155
    for y in range(n):
        for x in range(n):
            cov = cov_round_rect(x, y, x0, y0, x1, y1, rr)
            if cov <= 0:
                continue
            t = y / float(n - 1)
            wood = tuple(int(WOOD_TOP[i] + (WOOD_BOTTOM[i] - WOOD_TOP[i]) * t) for i in range(3))
            paint(x, y, wood, cov)

    # 木板边缘描一圈，浅色桌面上也看得清
    for y in range(n):
        for x in range(n):
            outer = cov_round_rect(x, y, x0, y0, x1, y1, rr)
            inner = cov_round_rect(x, y, x0 + n * 0.018, y0 + n * 0.018,
                                   x1 - n * 0.018, y1 - n * 0.018, max(1.0, rr - n * 0.018))
            edge = clamp01(outer - inner)
            if edge > 0:
                paint(x, y, RIM, edge * 0.85)

    # 网格：5 条线，落在 0.18..0.82
    hw = max(1.0, n * 0.0055)
    for k in range(5):
        f = 0.18 + k * 0.16
        c = f * n
        for y in range(n):
            for x in range(n):
                cov = max(cov_seg(x, y, n * 0.16, c, n * 0.84, c, hw),
                          cov_seg(x, y, c, n * 0.16, c, n * 0.84, hw))
                if cov > 0:
                    paint(x, y, LINE, cov * 0.6)

    # 两颗子：左上黑、右下白
    r = n * 0.125
    stone(n * 0.285, n * 0.285, r, True)
    stone(n * 0.715, n * 0.715, r, False)

    # 中间双向箭头（表示来回对弈）
    hw2 = max(1.4, n * 0.0135)
    ax, ay = n * 0.405, n * 0.595
    bx, by = n * 0.595, n * 0.405
    for y in range(n):
        for x in range(n):
            cov = cov_seg(x, y, ax, ay, bx, by, hw2)
            if cov > 0:
                paint(x, y, ARROW, cov)
    head = n * 0.068
    for (tipx, tipy, dirx, diry) in ((ax, ay, -(bx - ax), -(by - ay)), (bx, by, bx - ax, by - ay)):
        L = (dirx * dirx + diry * diry) ** 0.5
        ux, uy = dirx / L, diry / L
        px, py = -uy, ux
        p0 = (tipx, tipy)
        p1 = (tipx + ux * head + px * head * 0.80, tipy + uy * head + py * head * 0.80)
        p2 = (tipx + ux * head - px * head * 0.80, tipy + uy * head - py * head * 0.80)
        for y in range(max(0, int(min(p0[1], p1[1], p2[1])) - 3), min(n, int(max(p0[1], p1[1], p2[1])) + 4)):
            for x in range(max(0, int(min(p0[0], p1[0], p2[0])) - 3), min(n, int(max(p0[0], p1[0], p2[0])) + 4)):
                cov = cov_tri(x, y, p0, p1, p2)
                if cov > 0:
                    paint(x, y, ARROW, cov)

    # 降采样
    out = []
    for y in range(size):
        row = []
        for x in range(size):
            acc = [0, 0, 0, 0]
            for dy in range(SS):
                for dx in range(SS):
                    p = buf[y * SS + dy][x * SS + dx]
                    a = p[3]
                    acc[0] += p[0] * a
                    acc[1] += p[1] * a
                    acc[2] += p[2] * a
                    acc[3] += a
            if acc[3] == 0:
                row.append((0, 0, 0, 0))
            else:
                k = SS * SS
                row.append((acc[0] // acc[3], acc[1] // acc[3], acc[2] // acc[3], acc[3] // k))
        out.append(row)
    return out


def main():
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res")
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in dens.items():
        d = os.path.join(base, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        write_png(os.path.join(d, "ic_launcher.png"), px, px, render(px))
        print("mipmap-%s/ic_launcher.png  %dx%d" % (name, px, px))


if __name__ == "__main__":
    main()
