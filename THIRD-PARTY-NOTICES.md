# 第三方组件声明 / Third-Party Notices

本仓库与成品 APK 打包了以下第三方组件。各组件的版权与许可归其原作者所有；本文件用于满足其许可要求。

This repository and the built APK bundle the following third-party components. Copyright and license belong to their respective authors; this file satisfies their license requirements.

---

## 1. KataGo（围棋引擎与权重 / Go engine and networks）

- 用途 / Used for：围棋 AI 对弈 / Go AI play
- 位置 / Files in this repo：
  - `jniLibs/arm64-v8a/libkatago.so`
  - `assets/model_b6c96.txt.gz`、`assets/model_b10c128.txt.gz`、`assets/model_b18c384nbt.bin.gz`
- 上游 / Upstream：https://github.com/lightvector/KataGo
- 许可证 / License：**MIT**

### MIT License (KataGo)

```
Copyright 2025 David J Wu ("lightvector") and/or other authors of the content in this repository.
(See 'CONTRIBUTORS' file for a list of authors as well as other indirect contributors).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

> 注：KataGo 仓库中部分外部依赖（clblast、cutlass、onnx、nlohmann_json 等）各有其许可，详见其 `cpp/external/` 各子目录。
> Note: some external dependencies in the KataGo repo (clblast, cutlass, onnx, nlohmann_json, etc.) have their own licenses; see the subdirectories under `cpp/external/`.

---

## 2. Rapfi（五子棋引擎 / Gomoku-Renju engine）

- 用途 / Used for：五子棋「强档」AI / strong Gomoku AI
- 位置 / Files in this repo：
  - `jniLibs/arm64-v8a/librapfi.so`（构建时打包进 APK 的 `assets/engine/rapfi`，运行时释放执行）
  - `tools/rapfi/`（构建记录 / build notes）
- 上游 / Upstream：https://github.com/dhbloo/rapfi
- 许可证 / License：**GNU General Public License v3.0 (GPL-3.0)**
- 完整许可文本 / Full text：[`licenses/GPL-3.0.txt`](licenses/GPL-3.0.txt)（上游对应 `Copying.txt`）

本应用打包并链接了 GPL-3.0 的 Rapfi，因此本应用整体按 **AGPL-3.0** 发布（AGPL-3.0 与 GPL-3.0 相互兼容），见根目录 [`LICENSE`](LICENSE)。

Because this app bundles and links the GPL-3.0 Rapfi, the app as a whole is released under **AGPL-3.0** (AGPL-3.0 and GPL-3.0 are mutually compatible); see [`LICENSE`](LICENSE) at the repo root.

---

## 3. ElephantEye 象眼（象棋引擎 / Xiangqi engine）

- 用途 / Used for：象棋三档 AI / Xiangqi AI at all levels
- 位置 / Files in this repo：
  - `jniLibs/arm64-v8a/libeleye.so`（源码 `eleeye/` 交叉编译为 Android arm64，构建时打包进 APK 的 `assets/engine/eleeye`，运行时释放执行）
- 上游 / Upstream：http://www.xqbase.com/computer/eleeye.htm （作者 / author：黄晨 / Morning Yellow）
- 版本 / Version：ElephantEye 3.31（UCCI 3.0 协议）
- 许可证 / License：**GNU Lesser General Public License v2.1 (LGPL-2.1)**
- 完整许可文本 / Full text：[`licenses/LGPL-2.1.txt`](licenses/LGPL-2.1.txt)（上游对应 `LICENSE`）

ElephantEye 以 LGPL-2.1 发布；本应用按协议以独立可执行程序方式调用它（子进程 + UCCI 文本协议），
并随包提供其源码构建方式与本许可文本，满足 LGPL 关于「可替换/可重链接」的要求。
ElephantEye 与本应用其余部分（AGPL-3.0）通过进程边界分离，互不传染。

ElephantEye is released under LGPL-2.1; this app invokes it as a separate executable (subprocess + UCCI text protocol), ships this license text and build notes, satisfying LGPL's relink/replace requirements. It is separated from the rest of the app (AGPL-3.0) by a process boundary.

---

## 4. 其他 / Others

- Android SDK / JDK 等构建工具不随本仓库分发。
- 其余应用源码与资源采用 AGPL-3.0，见根目录 [`LICENSE`](LICENSE)。
- Build tools such as the Android SDK / JDK are not distributed with this repository.
- All other application source and resources are under AGPL-3.0; see [`LICENSE`](LICENSE).
