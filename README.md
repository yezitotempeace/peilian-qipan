# 陪练棋盘 / Peilian Qipan

陪练棋盘是一个 Android 棋类练习应用，包含围棋、五子棋、象棋、军棋四种棋。

Peilian Qipan is an Android board-game practice app with four games: Go, Gomoku, Xiangqi (Chinese chess) and Junqi (Army chess).

## 四种棋 / The four games

### 围棋 / Go
- 9 / 13 / 19 路；内置 KataGo 引擎，人机对弈、电脑对电脑、逐手复盘。
- 9/13/19 board sizes; built-in KataGo engine; human-vs-AI, AI-vs-AI, and move-by-move review.

### 五子棋 / Gomoku
- 13 / 15 / 19 路；三档内置 AI（快、普通、强），也可接入外部 Rapfi 引擎。
- 13/15/19; three built-in AI levels, plus optional external Rapfi engine.
- 复盘带胜率曲线、失误排行和推荐点；会记住你的常用走法来陪你练。
- Review with win-rate curve, mistake ranking and suggested moves; it learns your habits.

### 象棋 / Xiangqi
- 三档内置 AI；悔棋、认输、逐手复盘。
- Three AI levels; undo, resign and move-by-move review.
- 复盘时选中某一手，棋盘上用绿圈标出推荐点。
- In review, the recommended move is marked with a green circle.

### 军棋 / Junqi
- 明棋 / 暗棋两种开局；人执红、电脑执黑。
- Open or hidden setup; you play red against the computer.
- 铁路、行营、大本营规则；暗棋中只有交战过的格子才亮出兵种。
- Railway, camp and headquarters rules; in hidden mode a piece is revealed only after a battle.

## 安装 / Install

从 Releases 下载 `peilian-qipan-v170.apk`（Android 8.0+，arm64-v8a）安装：

Download the APK from Releases (Android 8.0+, arm64-v8a):

https://github.com/yezitotempeace/peilian-qipan/releases/tag/v170

## 构建 / Build

仓库已包含 KataGo 权重（`assets/`）和 native 库（`jniLibs/arm64-v8a/`）。需要 JDK 与 Android SDK 构建工具。

The repository ships the KataGo weights and native libraries. Build with JDK and Android SDK build tools.

```bash
bash build.sh      # 生成 autogo.apk / builds autogo.apk
bash run_tests.sh  # 运行测试 / runs the test suite
```

## 版本 / Version

v170 · versionCode 170 · versionName 1.20261018.170 · minSdk 26 · targetSdk 34

## 说明 / Notes

- 安装包约 122 MB，因为打包了三个 KataGo 网络与 Rapfi 引擎。
- The APK is about 122 MB because it bundles three KataGo networks and the Rapfi engine.
