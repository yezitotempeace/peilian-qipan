#!/usr/bin/env python3
"""校验：包内清单声明的 activity / activity-alias（targetActivity）/ service / receiver / provider 的类，必须真在 dex 里有类定义。

用法：python3 check_activities.py <apk> [aapt2 路径]
为什么不用 grep：第 72 轮审计实测 —— ①属性值用单引号写时逐行正则一条都抓不到（还谎报数量）；
②用「dex 里有没有这段字符串」判类，源码里留一句 'Lcom/autogo/GhostActivity;' 就能骗过。
本脚本：用 aapt2 dump xmltree 解析清单（跨行、单双引号、裸名、别名都对）＋ 直接解析
classes*.dex 的 class_defs 表（真类定义，不是字符串）。
退出码：0=全部找到；1=有缺失（列出缺哪个）；2=用法/aapt2 出错。
"""
import re, subprocess, sys, zipfile


def parse_manifest(apk, aapt2):
    r = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write("aapt2 解析清单失败：%s\n" % r.stderr.strip())
        sys.exit(2)
    text = r.stdout
    m = re.search(r'A: (?:[^\s]*:)?package(?:\(0x[0-9a-f]+\))?="([^"]+)"', text)
    pkg = m.group(1) if m else "com.autogo"
    names, targets = [], []
    for blk in re.finditer(
            r'E: (activity|activity-alias|service|receiver|provider) \(line=\d+\)(.*?)(?=\n\s*E: |\Z)',
            text, re.S):
        kind, body = blk.group(1), blk.group(2)
        am = re.search(r'A: [^\s]*android:name\(0x[0-9a-f]+\)="([^"]*)"', body)
        tm = re.search(r'A: [^\s]*targetActivity\(0x[0-9a-f]+\)="([^"]*)"', body)
        if kind == "activity-alias":
            if not tm:
                sys.stderr.write("构建校验失败：<activity-alias> 声明了 name 却没有 targetActivity\n")
                sys.exit(1)
            targets.append(tm.group(1))
        elif am:
            names.append(am.group(1))

    def fq(n):
        if n.startswith("."):
            return pkg + n
        if "." not in n:
            return pkg + "." + n
        return n

    return pkg, sorted(set(fq(n) for n in names + targets))


def dex_classes(apk):
    found = set()
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not re.match(r"classes\d*\.dex$", name):
                continue
            b = z.read(name)
            if not (b[:4] == b"dex\n" and b[4:7].isdigit() and b[7:8] == b"\x00"):
                raise ValueError("%s 不是 dex 文件（头 8 字节：%r）" % (name, b[:8]))
            if len(b) >= 112:
                hdr = int.from_bytes(b[36:40], "little")
                endian = int.from_bytes(b[40:44], "little")
                if hdr != 112 or endian != 0x12345678:
                    raise ValueError("%s 的 dex 头不对劲（头长度 %d，endian 0x%08x）" % (name, hdr, endian))
            if len(b) < 112:
                # 静默跳过会让 100 字节的 classes2.dex 被判成「一切正常」（第 74 轮审计 低-2）
                raise ValueError("%s 只有 %d 字节，连 112 字节的 dex 头部都不够" % (name, len(b)))

            def u4(off, buf=b):
                # 越界要显式报错：切片越界只会静默返回空串，坏 dex 会被当成「解析成功」（第 73 轮审计 低-2）
                if off < 0 or off + 4 > len(buf):
                    raise ValueError("偏移 %d 越界（文件 %d 字节）" % (off, len(buf)))
                return int.from_bytes(buf[off:off + 4], "little")

            cd_size, cd_off = u4(96), u4(100)
            str_off, type_off = u4(60), u4(68)
            str_size, type_size = u4(56), u4(64)
            if cd_off + cd_size * 32 > len(b):
                raise ValueError("class_defs 表越界（size=%d, off=%d, 文件 %d 字节）"
                                 % (cd_size, cd_off, len(b)))
            for i in range(cd_size):
                class_idx = u4(cd_off + i * 32)
                if class_idx >= type_size:
                    raise ValueError("class_idx %d 超出 type_ids（%d）" % (class_idx, type_size))
                str_idx = u4(type_off + class_idx * 4)
                if str_idx >= str_size:
                    raise ValueError("string_idx %d 超出 string_ids（%d）" % (str_idx, str_size))
                off = u4(str_off + str_idx * 4)
                if off >= len(b):
                    raise ValueError("字符串偏移 %d 越界（文件 %d 字节）" % (off, len(b)))
                j = off
                while j < len(b) and b[j] >= 0x80:      # 跳过 ULEB128 长度前缀
                    j += 1
                j += 1
                end = b.find(0, j)
                if end < 0:
                    raise ValueError("字符串没有结束符（偏移 %d）" % off)
                found.add(b[j:end].decode("utf-8", "replace"))
    return found


def main():
    if len(sys.argv) < 2:
        sys.stderr.write("用法：check_activities.py <apk> [aapt2]\n")
        sys.exit(2)
    apk = sys.argv[1]
    aapt2 = sys.argv[2] if len(sys.argv) > 2 else "/usr/bin/aapt2"
    pkg, want = parse_manifest(apk, aapt2)
    try:
        have = dex_classes(apk)
    except Exception as e:      # 坏 dex 不再和「缺类」共用一个退出码（第 73 轮审计 低-2）
        sys.stderr.write("构建校验失败：classes*.dex 解析失败（包可能损坏）：%s: %s\n"
                         % (type(e).__name__, e))
        sys.exit(3)
    missing = [n for n in want if ("L" + n.replace(".", "/") + ";") not in have]
    if missing:
        sys.stderr.write("构建校验失败：清单里声明的组件类（activity / activity-alias / service / receiver / provider）在包内 dex 里没有类定义：\n")
        for n in missing:
            sys.stderr.write("  - %s（L%s;）\n" % (n, n.replace(".", "/")))
        sys.exit(1)
    print("清单里 %d 个组件类（activity/service/receiver/provider）都在 dex 里（包 %s）"
          % (len(want), pkg))
    sys.exit(0)


main()
