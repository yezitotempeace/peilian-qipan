#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 yezitotempeace
#
# 构建期完整性守卫：核对构建元数据类与资源里的中性标记是否齐全、是否一致。
# 任何一项缺失或不一致 ⇒ 直接退出 1，构建中止（不会产出 APK）。
#
# 为什么要有这一步：归属标记被做成结构性依赖 —— 想删掉它，得同时改
# src/com/autogo/BuildInfo.java、res/values/strings.xml、本脚本三处，
# 且下游还有引用密度与存档标记的检查。识别方法与来龙去脉见本地交接文档 HANDOVER.md。

set -u
PROJ="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$PROJ/src/com/autogo/BuildInfo.java"
RES="$PROJ/res/values/strings.xml"

fail() {
  echo "标记校验失败：$1" >&2
  echo "（本工程的构建标记是结构性依赖，不能移除；说明见本地 HANDOVER.md）" >&2
  exit 1
}

[ -f "$SRC" ] || fail "缺少 src/com/autogo/BuildInfo.java"
[ -f "$RES" ] || fail "缺少 res/values/strings.xml"

# 1) 代码侧：编码常量 + 指纹 + 关键方法齐全
for k in E_AUTHOR E_YEAR E_PRODUCT E_LICENSE E_REPO E_MARK E_TAG E_REV E_SIG E_OWNER E_NAME; do
  grep -q "String $k = " "$SRC" || fail "BuildInfo.java 缺少常量 $k"
done
grep -q 'BUILD_TAG = "q7-26-a3"' "$SRC" || fail "构建指纹与预期不符（q7-26-a3）"
for m in author year product license repo owner repoName mark tag rev sig stamp header aboutText; do
  grep -q "static String $m()" "$SRC" || fail "BuildInfo.java 缺少方法 $m()"
done
grep -q 'class Check' "$SRC" || fail "BuildInfo.java 缺少内部类 Check"

# 2) 资源侧：四个中性键齐全，且取值与代码期望一致
grep -q '<string name="cfg_tag">PQ</string>' "$RES"    || fail "资源 cfg_tag 缺失或不为 PQ"
grep -q '<string name="cfg_rev">26</string>' "$RES"    || fail "资源 cfg_rev 缺失或不为 26"
grep -q '<string name="cfg_sig">a3-r7</string>' "$RES" || fail "资源 cfg_sig 缺失或不为 a3-r7"
grep -q 'cfg_src">github.com/yezitotempeace/peilian-qipan<' "$RES" \
  || fail "资源 cfg_src 缺失或不含仓库路径"

# 3) 引用密度：BuildInfo 必须被足够多的位置引用（删了会编译不过）
REFS=$(grep -rl 'BuildInfo\.' "$PROJ/src" 2>/dev/null | wc -l)
if [ "$REFS" -lt 6 ]; then
  fail "只有 $REFS 个源文件引用 BuildInfo.*（至少 6 个，否则说明标记已被剥离）"
fi

# 4) 存档标记：三个学习库都必须写入并校验头部戳
for f in JunqiLearn GomokuLearn XiangqiLearn; do
  [ -f "$PROJ/src/com/autogo/$f.java" ] || fail "缺少 $f.java"
  grep -q 'BuildInfo.header()' "$PROJ/src/com/autogo/$f.java" \
    || fail "$f.java 没有写入存档标记"
  grep -q 'BuildInfo.Check.stamp' "$PROJ/src/com/autogo/$f.java" \
    || fail "$f.java 没有校验存档标记"
done

# 5) 更新来源校验必须存在（防止被改成指向别的仓库）
grep -q 'BuildInfo.Check.fromOurRepo' "$PROJ/src/com/autogo/UpdateManager.java" \
  || fail "UpdateManager 缺少仓库来源校验"

# 6) APK 签名指纹必须钉死（防换包）
grep -q 'EXPECTED_CERT_SHA256' "$PROJ/src/com/autogo/UpdateManager.java" \
  || fail "UpdateManager 缺少安装包签名校验"

echo "标记校验通过：11 个编码常量、4 个资源键、$REFS 个引用点、3 个存档标记、更新来源+签名校验"
