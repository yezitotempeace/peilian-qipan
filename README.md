# 陪练棋盘 / Peilian Qipan

陪练棋盘是一款 Android 棋类练习应用，一个 App 里包含围棋、五子棋、象棋、军棋四种棋，外加 2048；全部对局都在本机离线完成。

Peilian Qipan is an Android board-game practice app. It bundles four board games — Go, Gomoku, Xiangqi (Chinese chess) and Junqi (Army chess) — plus 2048, and runs entirely offline on the device.

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
- 棋盘按实体军棋画：5 列 × 12 行 60 个点位、棋子 2:1 横块、黑白铁路、行营圆圈、大本营黑框、中线山界。
- 铁路、行营、大本营等规则；暗棋中只有交战过的格子才会亮出兵种。
- 新局、认输、难度、返回。

- The board follows the physical game: 5×12 = 60 points, 2:1 horizontal pieces, black/white railways, circular camps, black HQ boxes and the central mountain boundary.
- You play Red against the computer. Start in open or hidden mode; you can switch during the game.
- Railway, camp and headquarters rules; in hidden mode a piece is only revealed after a battle.
- New game, resign, difficulty and back.
- Two board themes: dark uses the "app" style (dark navy with red/black rounded pieces), light uses a classic wood board; camps and HQ draw as outlines only.
- In hidden mode, after a capture the defeated enemy piece type is now revealed (previously it vanished without showing what it was).
- Main menu now has a full themed decorative background (faded Go board + stones, a Gomoku five-in-a-row, "楚河 汉界" + 帅/将, semi-transparent 2048 tiles, a Junqi railway line) in dark and light variants instead of a flat color.
- Junqi animation: pieces slide in with a scale pop, a red burst marks captures, and a defeated piece flips face-up (back → front) to reveal its rank before shrinking away.
- Standard Junqi rules: in hidden mode you now see your own pieces (only the opponent's are hidden until a battle); camps start empty (they are safe zones); the flip no longer leaves a ghost over the capturing piece.

## 2048

- 4×4；上下左右滑动，相同数字合并，合并得分；每次有效移动生成一个 2 或 4。
- 旧块有滑动动画，合并/新子会弹一下。
- 深色模式：棋盘底/空格换深灰，夜里不发白。
- 达到 2048 可以继续冲更高分；没有可走的格子才结束。
- 得分、最高分、重开；关掉再进恢复上一局。

- 4×4; swipe in any direction, equal tiles merge and score; every effective move spawns a 2 or 4.
- Reaching 2048 wins, and you can keep playing; the game ends only when no move is left.
- Score, best score and restart; the current game is restored when you come back.

## 通用 / General

- 启动页选择棋种；内置公告可查看每一版更新；左下角显示版本号。
- 深浅色外观跟随系统；横竖屏、大小屏自动适配。
- 对局离线；只有「更新」会联网（INTERNET 权限）。
- 仅 arm64-v8a；minSdk 26（Android 8.0）/ targetSdk 34。

- Pick a game on the launch screen; the in-app changelog lists every release; the version is shown at the bottom-left.
- Follows the system light/dark theme; adapts to orientation and screen size.
- Play stays offline; only the update checker uses the network (INTERNET permission).
- arm64-v8a only; minSdk 26 (Android 8.0) / targetSdk 34.

## 更新 / Updates

- GitHub Releases 与 App 内公告只保留最近两个版本；更早的版本本地仍保留。
- GitHub Releases and the in-app changelog keep only the two most recent versions; older ones stay local.


- 启动页右上角「更新」：直连 GitHub 仓库最新 Release，手动检查；国内走 gh-proxy.com 镜像，GitHub API 403/超时自动回退。
- 每 2 小时自动检查（前台计时 + 系统闹钟）；发现新版本可下载。
- 下载后交系统安装器；安装成功/失败都会删掉缓存安装包，下次启动也清残留。

- The "Update" button at the top-right of the launch screen checks the latest GitHub Release directly.
- It also checks every 2 hours (foreground timer + system alarm), and can download a new APK.
- The download is handed to the system installer; the cached APK is deleted after install (or on next launch).

## 安装 / Install

从 Releases 下载安装（约 122 MB，内含三个 KataGo 网络与 Rapfi 引擎）：

Download from Releases (about 122 MB, bundling the three KataGo networks and the Rapfi engine):

https://github.com/yezitotempeace/peilian-qipan/releases/tag/v188

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

当前 v188 · versionCode 188 · versionName 1.20261020.188

- 每个版本一个 GitHub Release，旧版本保留、不删除；想回旧版直接去 Releases 下载对应的 APK。
- App 启动页左下角显示当前 versionName 与 versionCode（设置页也能看到）。
- Each version gets its own GitHub Release; older releases are kept. The app shows the current versionName / versionCode at the bottom-left of the launch screen (and on the settings page).
