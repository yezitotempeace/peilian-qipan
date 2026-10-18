#!/usr/bin/env python3
"""第 103/104 轮审计：暗棋「已暴露」表只能由 JunqiReveal 改。

规则（除 `JunqiReveal.java` 外，扫描 `src/com/autogo` 下所有 .java，递归）：
 A. `revealed[...]` 之后紧跟 `=`（非 `==`）、`|=`、`+=`、`-=`、`*=`、`/=`、`++`、`--` ⇒ 违规；
 B. `revealed` 作为「裸标识符」出现（后面是 `)`/`,`/`;`/`=` 等，即整体传参或赋值），
    只允许出现在两个白名单调用里：`JunqiReveal.resolve(revealed`、`JunqiReveal.reset(revealed`；
    其他（传给别的 helper、`Arrays.fill(revealed,…)`、`System.arraycopy(…,revealed,…)`、别名 `rr = revealed`、
    `(revealed)`）⇒ 违规；
 C. 允许的只读用法：`revealed.length`、`revealed.clone()`、`revealed[...]` 读（不带赋值）。

实现细节（按第 104 轮审计的反馈加固）：
 - Java 的 `\\uXXXX` 转义在词法分析前就被解码 ⇒ 扫描前先解码（否则 `revealed\\u005B0\\u005D = true` 会漏）；
 - 递归子目录；一个源文件都没扫到时按错误退出（避免「0 个源文件 ✓」的假绿）。
"""
import os
import re
import sys

SKIP = {"JunqiReveal.java"}
ALLOW_TAIL = (r"JunqiReveal\s*\.\s*(?:resolve|reset)\s*\(\s*$")
ASSIGN_OPS = ("=", "|=", "&=", "^=", "+=", "-=", "*=", "/=", "++", "--")

def decode_unicode_escapes(s):
    return re.sub(r"\\u+([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)

def strip_comments_and_strings(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            end = n if j < 0 else j + 2
            out.append(" " * (end - i))
            i = end
            continue
        if c in "\"'":
            q = c
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == q:
                    i += 1
                    break
                i += 1
            out.append('""')
            continue
        out.append(c)
        i += 1
    return "".join(out)

def line_col(s, pos):
    return s.count("\n", 0, pos) + 1, pos - (s.rfind("\n", 0, pos) + 1) + 1

def check_text(s, fname):
    bad = []
    for m in re.finditer(r"\brevealed\b", s):
        before = s[max(0, m.start() - 120):m.start()]
        j = m.end()
        while j < len(s) and s[j] in " \t":
            j += 1
        if s.startswith(".length", j) or s.startswith(".clone", j):
            continue
        if j < len(s) and s[j] == "[":
            depth, k = 0, j
            while k < len(s):
                if s[k] == "[":
                    depth += 1
                elif s[k] == "]":
                    depth -= 1
                    if depth == 0:
                        k += 1
                        break
                k += 1
            kk = k
            while kk < len(s) and s[kk].isspace():
                kk += 1
            op = next((o for o in ASSIGN_OPS if s.startswith(o, kk)), None)
            if op == "=" and s.startswith("==", kk):
                op = None
            if op:
                bad.append((m.start(), f"内联修改 revealed[...]（{op}）"))
            continue
        # 声明（字段/参数）：类型紧挨在名字前 ⇒ 允许
        if re.search(r"boolean\s*\[\s*\]\s+$", before) or re.search(r"boolean\s*\[\s*\]\s+\w+\s*,\s*$", before):
            continue
        # 只读比较
        if s.startswith("==", j) or s.startswith("!=", j):
            continue
        # 只读用法：增强 for（冒号后）、return、synchronized、对数组取方法/类
        prev = s[:m.start()].rstrip()
        if prev.endswith(":"):
            continue
        if re.search(r"\breturn\s*$", prev):
            continue
        if re.search(r"\bsynchronized\s*\(\s*$", prev):
            continue
        if re.match(r"\s*\.\s*[A-Za-z_$][\w$]*\s*\(", s[m.end():]):
            continue
        # 找到「包住这个实参的调用」的callee；白名单里的读函数放行
        callee = None
        depth2, k2 = 0, m.start() - 1
        while k2 >= 0:
            ch = s[k2]
            if ch == ")":
                depth2 += 1
            elif ch == "(":
                if depth2 == 0:
                    k2 -= 1
                    while k2 >= 0 and s[k2] in " \t":
                        k2 -= 1
                    end = k2 + 1
                    while k2 >= 0 and (s[k2].isalnum() or s[k2] in "_.$"):
                        k2 -= 1
                    callee = s[k2 + 1:end]
                    break
                depth2 -= 1
            elif ch in ";{}":
                break
            k2 -= 1
        allow = ("JunqiReveal.resolve", "JunqiReveal.reset", "JunqiAI.masked", "JunqiAI.genmove",
                 "JunqiAI.evaluate", "java.util.Arrays.toString", "Arrays.toString",
                 "java.util.Arrays.copyOf", "Arrays.copyOf", "Arrays.equals", "java.util.Arrays.equals")
        # JunqiAI 自己内部调用 masked/genmove/evaluate（裸名）是合法读用法；其他文件里的同名函数不放行
        if callee in allow or (fname == "JunqiAI.java" and callee in ("masked", "genmove", "evaluate")):
            continue
        if callee in ("System.arraycopy", "java.lang.System.arraycopy"):
            # arraycopy(src, srcPos, dst, dstPos, len)：只有第 3 个实参是写目标
            lp = s.rfind("(", 0, m.start())
            head = s[lp + 1:m.start()]
            depth = 0
            idx = 0
            for ch in head:
                if ch in "([{":
                    depth += 1
                elif ch in ")]}":
                    depth -= 1
                elif ch == "," and depth == 0:
                    idx += 1
            if idx != 2:
                continue
            bad.append((m.start(), "让 System.arraycopy 把 revealed 当写目标"))
            continue
        if callee:
            bad.append((m.start(), f"把 revealed 交给 {callee}(...) 去动（规则应只在 JunqiReveal 里）"))
        else:
            bad.append((m.start(), "把 revealed 传出去/赋给别的变量（可能是别名或 helper 写入）"))
    return bad

def main():
    roots = sys.argv[1:] or ["src/com/autogo"]
    total = files = 0
    for root in roots:
        for dirpath, _dirs, names in os.walk(root):
            for name in sorted(names):
                if not name.endswith(".java") or name in SKIP:
                    continue
                path = os.path.join(dirpath, name)
                raw = open(path, encoding="utf-8", errors="replace").read()
                s = strip_comments_and_strings(decode_unicode_escapes(raw))
                files += 1
                for pos, why in check_text(s, name):
                    line, col = line_col(s, pos)
                    print(f"{path}:{line}:{col}: {why}")
                    total += 1
    if files == 0:
        print("错误：一个 .java 都没扫到（目录写错了？）—— 这不算通过")
        return 2
    if total:
        print(f"错误：除 JunqiReveal.java 外，发现 {total} 处违规动 revealed 的地方（规则应只在 JunqiReveal 里）")
        return 1
    print(f"军棋暴露规则接线：{files} 个源文件里没有内联改动、没有别名、也没有把 revealed 交给别的方法 ✓")
    return 0

if __name__ == "__main__":
    sys.exit(main())
