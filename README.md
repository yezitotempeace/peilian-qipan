# 陪练棋盘 / Peilian Qipan

陪练棋盘是一款 Android 棋类练习应用，一个 App 里包含围棋、五子棋、象棋、军棋四种棋，全部对局都在本机离线完成。

Peilian Qipan is an Android board-game practice app. It bundles four games — Go, Gomoku, Xiangqi (Chinese chess) and Junqi (Army chess) — and runs entirely offline on the device.

## 围棋 / Go

- 9 / 13 / 19 路；你执黑或执白，也可以让电脑自己对弈。
- 内置 KataGo 引擎，三档网络随时切换：极速（5 MB）、标准（14 MB，默认）、强力（93 MB）；每手 visits 可调。
- 逐手复盘：胜率曲线、失误排行、引擎推荐点。
- 新局、悔棋、停一手、认输；终局自动保存 SGF，也能读入 SGF 继续下或直接复盘。
- 设置：贴目、每手 visits、线程数、网络、是否允许认输；横竖屏与大小屏自适应。

- 9/13/19 board; play Black or White, or watch the engine play itself.
- Built-in KataGo engine with three switchable networks: Fast (5 MB), Standard (14 MB, default) and Strong (93 MB); visits per move are adjustable.
- Move-by-move review: win-rate curve, mistake ranking and engine suggestions.
- New game, undo, pass and resign; finished games are saved as SGF, and you can load an SGF to continue or review it.
- Settings: komi, visits per move, threads, network and resign toggle; adapts to orientation and screen size.

## 五子棋 / Gomoku

- 13 / 15 / 19 路；三档难度：快 / 普通 / 强。
- 强档在 15 路时优先调用外部 Rapfi 引擎；其他路数或找不到引擎时自动退回内置 AI。
- 新局、悔棋、认输；可切换棋盘路数、执黑/执白、禁手（黑棋长连·四四·三三判负）和手顺显示。
- 对战：电脑对电脑，可暂停 / 继续，不计入学习记录。
- 复盘：胜率曲线、失误排行和推荐点；点失误行或拖动胜率条，棋盘会跟着摆到那一手。
- 学习：记住你的常用走法和赢过的手，下一局优先从引擎候选里挑这些走法；可查看统计、一键清除。

- 13/15/19 board; three AI levels: Fast / Normal / Strong.
- On 15×15 the Strong level prefers the external Rapfi engine; other sizes or a missing engine fall back to the built-in AI.
- New game, undo and resign; switch board size, color, forbidden moves (overline, double-four and double-three lose for Black) and move numbers.
- AI vs AI mode: pause/resume; such games are not recorded by the learning feature.
- Review: win-rate curve, mistake ranking and suggestions; the board follows the selected mistake or the win-rate slider.
- Learning: remembers your common moves and winning moves, and prefers them among the engine's candidates; stats and one-tap clear are available.

## 象棋 / Xiangqi

- 三档内置 AI；你执红先行。
- 新局、悔棋、认输、难度、全屏、返回。
- 逐手复盘：胜率曲线、失误排行和推荐点；选中某一手时，棋盘上用绿圈标出该手的推荐点。

- Three built-in AI levels; you play Red and move first.
- New game, undo, resign, difficulty, fullscreen and back.
- Move-by-move review: win-rate curve, mistake ranking and suggestions; the suggested move is marked with a green circle on the board.

## 军棋 / Junqi

- 人执红、电脑执黑；开局可选明棋或暗棋，对局中也能切换。
- 铁路、行营、大本营等规则；暗棋中只有交战过的格子才会亮出兵种。
- 新局、认输、难度、返回。

- You play Red against the computer. Start in open or hidden mode; you can switch during the game.
- Railway, camp and headquarters rules; in hidden mode a piece is only revealed after a battle.
- New game, resign, difficulty and back.

## 通用 / General

- 启动页选择棋种；内置公告可查看每一版更新；左下角显示版本号。
- 深浅色外观跟随系统；横竖屏、大小屏自动适配。
- 完全离线：应用清单没有申请网络权限。
- 仅 arm64-v8a；minSdk 26（Android 8.0）/ targetSdk 34。

- Pick a game on the launch screen; the in-app changelog lists every release; the version is shown at the bottom-left.
- Follows the system light/dark theme; adapts to orientation and screen size.
- Fully offline: the app declares no network permission.
- arm64-v8a only; minSdk 26 (Android 8.0) / targetSdk 34.

## 安装 / Install

从 Releases 下载安装（约 122 MB，内含三个 KataGo 网络与 Rapfi 引擎）：

Download from Releases (about 122 MB, bundling the three KataGo networks and the Rapfi engine):

https://github.com/yezitotempeace/peilian-qipan/releases/tag/v170

## 构建 / Build

仓库已包含 KataGo 权重（`assets/`）和 native 库（`jniLibs/arm64-v8a/`）。需要 JDK 与 Android SDK 构建工具（aapt2、zipalign、apksigner），不需要 NDK。

The repository ships the KataGo weights and native libraries. You need a JDK and Android SDK build tools (aapt2, zipalign, apksigner); no NDK is required.

```bash
bash build.sh      # 生成 autogo.apk / builds autogo.apk
bash run_tests.sh  # 运行测试 / runs the test suite
```

## 目录 / Layout

- `src/com/autogo/` — 应用源码 / app sources
- `tests/` — JVM 测试 / JVM tests
- `tools/` — 检查脚本与 Rapfi / checks and Rapfi
- `res/`、`assets/`、`jniLibs/` — 资源、KataGo 权重、native 库 / resources, KataGo weights, native libraries

## 版本 / Version

v170 · versionCode 170 · versionName 1.20261018.170
