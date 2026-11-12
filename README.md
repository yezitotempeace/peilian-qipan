# 陪练棋盘 · Android 版

**简介 / Introduction（中英）**

陪练棋盘是一个 Android 六合一棋类练习 App：**围棋**（KataGo 真引擎，9/13/19 路）、**五子棋**（Rapfi 强引擎，13/15/19 路；15 路吃满外部引擎，其余退回内置 AI）、**象棋**（ElephantEye「象眼」强引擎 + 学习库 + 逐手复盘）、**军棋**（明棋 / 暗棋，人执红、电脑执黑，三档 AI + 学习库）、**2048**（4×4 数字合并）、**数独**（9×9，简单 / 普通 / 困难三档，困难档极难）。

Peilian Qipan (Board-Game Practice) is an all-in-one Android app for six board games: **Go** (real KataGo engine, 9/13/19), **Gomoku** (Rapfi engine, 13/15/19; strong engine on 15×15, falls back to built-in AI otherwise), **Xiangqi** (Chinese chess; ElephantEye engine + experience learning + move-by-move review), **Junqi** (Army chess; open or hidden mode, three AI levels + learning), **2048** (4×4 merge) and **Sudoku** (9×9, three levels).

- 版本 / Version: **v197**（`1.20261108.197`，versionCode 197）
- 构建 / Build: `bash build.sh`（需要 `assets/` 里三个 KataGo 权重、`jniLibs/arm64-v8a/` 的 `libkatago.so` / `librapfi.so` / `libeleye.so`）
- 测试 / Test: `bash run_tests.sh`（JVM 用例 **1706 项**，最近一次全过）
- 安装包 / APK: 构建产物 `autogo.apk`（约 122 MB，已签名，minSdk 26 / targetSdk 34）；同版本 APK 见仓库 **Releases**

> **外部引擎装载方式（v197 起）**：五子棋 Rapfi、象棋 ElephantEye 两个引擎二进制打包进 APK 的 `assets/engine/`，
> 首次运行时由 `NativeExe` 释放到应用私有目录（`filesDir`）再 `chmod 700` 执行，绕开系统对
> 安装目录（`nativeLibraryDir`）的 exec 限制；启用前会真的起一次进程自检，失败则退回内置 AI。
> 围棋 KataGo 仍走 `nativeLibraryDir/libkatago.so`（已在真机验证可执行）。

---

把 KataGo 真正装进 Android App 的围棋程序：人机对弈、引擎自动对弈、以及**每一局的逐手复盘**。

产物：`autogo.apk`（构建产物，约 122 MB，已签名，minSdk 26 / targetSdk 34）
构建产物就是可直接安装的签名包；正式分发包见仓库 **Releases**。

---

## 版本号

每次 `bash build.sh` 都会自动把版本号 +1 并写进 APK：

- `versionCode` 从 `VERSION` 文件读，构建时自增（Android 靠它判断「这是新版本」，值不变的话装了新版系统可能不提示覆盖）
- `versionName` 格式为 `主版本.日期.序号`，例如 `1.20261012.7`

设置页会显示当前版本号，出问题时可以直接报这个号定位。

注意：`aapt2 link` 的 `--version-code` 参数**不会**覆盖 `AndroidManifest.xml` 里的值（实测过，APK 里读出来还是 manifest 的旧值），
所以构建脚本是直接用 `sed` 改 manifest，这是唯一生效的做法。

---

## 一、安装

1. 从仓库 **Releases** 下载最新 `peilian-qipan-vNNN.apk`
2. 系统会提示「未知来源」，允许安装即可
3. 首次启动只释放**当前选中**的那个网络（默认 b10c128，14 MB），约几秒

APK 有 120 MB，因为三个网络都打进去了（见第五节）。手机上装的时候会稍慢一点。

---

## 二、功能

| 功能 | 说明 |
|---|---|
| 人机对弈 | 你执黑或执白，点交叉点落子；非法手会提示原因 |
| 自动对弈 | 引擎自己跟自己下，一手一手实时看 |
| 逐手复盘 | 对每一手之前的局面重新分析，给出胜率曲线 + 失误排行 + 引擎推荐点 |
| 悔棋 / 停一手 / 认输 | 悔棋会整体重算盘面（提子、劫点都重算，不是简单回滚） |
| 棋谱 | 终局自动存 SGF 到 App 私有目录 |
| **读谱** | 用系统文件选择器读入任意 SGF：已下完的直接进复盘，没下完的可以接着下 |
| **三档网络** | 极速 b6c96 / 标准 b10c128 / 强力 b18c384nbt，设置里随时切换 |
| **屏幕自适应** | 启动时读屏幕尺寸，横竖屏自动换布局，小屏棋盘自动缩小、控件区可滚动 |
| 设置 | 盘面大小（9/13/19）、贴目、每手 visits、线程数、网络、是否允许认输 |
| **用户协议** | 首次启动须同意《用户协议》（学习版声明）才能进入；不同意直接退出 |
| **更新** | 检查仓库最新 Release，校验来源仓库与安装包签名后才允许安装 |

复盘用的是 `kata-genmove_analyze`：它会边搜边吐 `info` 行（含每个候选点的胜率、
scoreLead、PV），最后给出自己选的那手。分析完先 `undo` 撤掉引擎自己下的那手，
再 `play` 棋谱上的实际那手 —— 这样逐手前进只要 undo+play，不必每手重放整盘。

### 三档网络怎么选

三个网络都打进 APK，但**同一时刻磁盘上只有一份**（切换时会删掉上一个）。
以下是在这台设备上实测的 9 路 100 visits 单步耗时：

| 档位 | 大小 | 实测单步 | 默认 visits | 适用 |
|---|---|---|---|---|
| 极速 b6c96 | 5 MB | 约 0.4 s | 300 | 想快、或边下边看 |
| 标准 b10c128 | 14 MB | 约 1.7 s | 100 | 默认，平衡 |
| 强力 b18c384nbt | 93 MB | 约 11 s | 40 | 想认真下一盘，能接受等待 |

注意第三列到第四列的关系：**网络越强，单步越慢，就越要把 visits 调低**，
否则一手要等十几秒到几十秒。

App 里**每个档位各自记住自己的 visits**（切到 b18 用 40，切回 b10 还是 100），
所以不用每次手动改。

### 屏幕自适应

启动时会读一次屏幕（`Screen.java`），之后所有尺寸都由「可用空间」算出来，
而不是写死 dp：

| 场景 | 布局 |
|---|---|
| 竖屏 | 棋盘在上、控件在下；棋盘给控件留 40% 高度（至少 260dp） |
| 横屏 | 棋盘在左、控件在右；此时限制棋盘的是高度，宽度最多占 62% |
| 小屏 | 棋盘自动缩小；控件区放进 ScrollView，永远滚得动 |
| 窄屏（<360dp） | 按钮字号自动收一号，三个汉字不换行 |
| 转屏 | `onConfigurationChanged` 重建布局并还原当前局面 |

棋盘的尺寸计算抽在 `LayoutMath.java` 里，是**纯函数**，所以能拿一堆真实分辨率
在 JVM 上跑单测，验证「棋盘放得下、不会小到没法下、控件区有地方」这些不变量。
实测各尺寸算出的棋盘边长：

```
老小屏 竖屏      320x480    棋盘 220 dp
常见小屏 竖屏    360x640    棋盘 344 dp
主流手机 竖屏    411x891    棋盘 395 dp
平板 竖屏        800x1280   棋盘 768 dp
主流手机 横屏    891x411    棋盘 395 dp
平板 横屏        1280x800   棋盘 784 dp
```

设置页里会显示当前读到的屏幕信息，方便确认自适应有没有按预期生效。

---

## 二之二、启动门与完整性校验

**用户协议**：`EulaActivity` 是启动入口，首次启动必须点「我同意」才进主菜单；
点「不同意」或按返回键直接 `finishAffinity()` 退出。条款正文与版本判定在 `Eula.java`（纯 Java，有单测），
改条款就把 `Eula.VERSION` 加一，已同意的用户会重新看到。

**完整性标记**：程序内部有一组中性形态的构建标记（代码里是编码常量，资源里是 `cfg_*` 键，
界面上不显眼展示）。启动时会与期望值比对：

| 情况 | 行为 |
|---|---|
| 标记完整 | 正常使用，不做任何提示 |
| 首次发现不一致 | **不立刻报错**，只记下时间并排一个 3～72 小时的宽限期 |
| 宽限期内 | 照常使用，不提示 |
| 宽限期到 | 提示「检测到本程序的文件已被修改，不是正版」并退出 |

三个学习库的存档也带格式戳，戳对不上就拒绝加载（不删文件；无戳的老存档照常读，避免升级丢数据）。
判定逻辑在 `IntegrityGuard`（纯 Java，有单测），只做提示与退出，**不删用户数据、不损坏设备、不联网上报**。

**更新链路**：只接受本仓库 Release 的资产（来源 URL 校验），并且安装包签名证书必须与
`UpdateManager.EXPECTED_CERT_SHA256` 一致，否则拒绝安装。

---

## 三、引擎是怎么塞进 App 的

本项目一共装了三台外部引擎，用两种方式装载：

### 3.1 围棋 KataGo —— `nativeLibraryDir` 方式

KataGo 官方 release 只有 x86_64，Android 上也不能跑 glibc 二进制，所以：

1. **用 NDK 的 sysroot + 系统原生 clang 交叉编译**出 aarch64-android 的 katago
   （NDK 自带的 clang 只有 linux-x86_64 prebuilt，在这台 aarch64 机器上根本起不来）
2. 二进制命名成 `libkatago.so` 放进 `jniLibs/arm64-v8a/`，
   manifest 里开 `android:extractNativeLibs="true"`，
   系统会把它释放到 `nativeLibraryDir` —— 这个目录带 `apk_data_file` 标签，
   AOSP 的 `app.te` 明确 `allow appdomain apk_data_file:file ... x_file_perms`，**允许 App 执行**
3. App 用 `ProcessBuilder` 把它当子进程拉起，走 GTP 文本协议通信

这条链路已在真机（Android 16）上验证 KataGo 能正常对弈。

### 3.2 五子棋 Rapfi / 象棋 ElephantEye —— `assets` 释放方式（v197 起）

这两个引擎（以及将来任何外部引擎）不再放进 `lib/`，而是：

1. 二进制作为 `assets/engine/rapfi`、`assets/engine/eleeye` 打进 APK（构建时由 `build.sh`
   从 `jniLibs/arm64-v8a/` 复制到临时 assets 目录再交给 aapt2）
2. 首次运行时 `NativeExe.prepare()` 把它**释放到应用私有目录**（`filesDir`，SELinux 标签
   `app_data_file`，属 App 自己，允许 exec），再 `chmod 700`
3. 真正起一次进程、走一步自检，通过才启用；失败（进程起不来 / 超时 / 通信断）就**退回内置 AI**

**为什么不用 `nativeLibraryDir` 装这两个**：KataGo 那条路在真机上虽然可行，但不同 ROM 对
`apk_data_file` 的 exec 策略并不完全一致，而且只要有一个环节不满足就整条路作废；
把二进制释放到 App 自己的 `filesDir` 是 Android 上跑外部可执行程序最稳的做法，也便于日后
更新引擎（删了重放即可，不必重新打包）。同时废弃了「只用 `File.canExecute()` 判断可用」
的旧逻辑 —— `canExecute()` 只看权限位，测不出 SELinux 拦截，会「检查通过、运行时失败」，
现在一律以「真跑一次自检」为准。

> 两个引擎都是纯 alpha-beta 搜索、不含神经网络的轻量程序：Rapfi 约 3.7 MB，
> ElephantEye 约 513 KB，对 122 MB 的 APK 体积影响可忽略。

---

## 四、重新构建

```bash
bash build.sh          # 产出 autogo.apk
bash run_tests.sh      # 装机前能跑的全部自动化验证（当前总数与分项见目录树里 `tests/` 那几行，或直接跑脚本看输出；含对着真引擎的 GTP 端到端、整盘分析端到端、对着真 Rapfi 进程的外部引擎适配层，以及启动页 / 界面层 / 棋盘视图 / 复盘页桩测）
```

构建不用 Gradle，直接 aapt2 → javac → d8 → zipalign → apksigner 走一遍。
几个必须知道的坑都写在 `build.sh` 注释里：

| 现象 | 原因 | 处理 |
|---|---|---|
| `aapt2: cannot execute: required file not found` | Google 的 build-tools 里 aapt2/zipalign 是 x86_64 | 用 Ubuntu 的 `android-sdk-build-tools`（aarch64 原生），脚本会自动回退 |
| `javac: error: Invalid filename` | 项目路径含中文 | 源码先拷到 ASCII 临时目录再编译 |
| `cannot find symbol: method metafactory` | android.jar 里没有 `LambdaMetafactory` | 加 `core-lambda-stubs.jar` 到 bootclasspath |
| `zip: command not found` | 缺 zip | `apt install zip` |

交叉编译 KataGo 本体（一次性）：

```bash
apt install -y clang-18 lld-18
cmake -S KataGo/cpp -B KataGo/cpp/build-android \
  -DCMAKE_TOOLCHAIN_FILE=KataGo/cpp/android-toolchain.cmake \
  -DUSE_BACKEND=EIGEN -DUSE_AVX2=0 -DKATAGO_AUTO_FETCH_DEPS=0 \
  -DCMAKE_BUILD_TYPE=Release -DEIGEN3_INCLUDE_DIRS=/usr/include/eigen3
make -C KataGo/cpp/build-android -j4
```

`android-toolchain.cmake` 里有三处非标准设置，都是被逼的：

- `-rtlib=compiler-rt` —— 否则 clang 会去找 NDK 里不存在的 `-lgcc`
- `-resource-dir=<NDK>/lib/clang/18` —— 让它找到 `libclang_rt.builtins-aarch64-android.a`
- `-L<NDK>/lib/linux/aarch64` + `-lunwind` —— aarch64 版 `libunwind.a` 只在这里

另外给 KataGo 源码打了 3 处补丁（`core/sha2.cpp`、`dataio/numpywrite.cpp`、
`neuralnet/desc.cpp`），都是加一行 `#include <endian.h>`：bionic 的 `<sys/types.h>`
不像 glibc 那样会带出 `BYTE_ORDER`，不加会直接 `#error` 中断编译。

---

## 五、代码结构

```
autogo-android/
├── AndroidManifest.xml
├── build.sh                构建脚本（含上面那些坑的处理）
├── run_tests.sh            装机前能跑的全部自动化验证（1706 项 = 逻辑 1200 + 界面层探针 506）
├── make_icons.py           纯标准库生成启动图标
├── assets/                 三个权重（b6c96 5 MB / b10c128 14 MB / b18c384nbt 93 MB）
├── tools/rapfi/            外部引擎的宿主编译版 + 构建记录（NOTICE.md：上游 commit 与两处补丁）
├── jniLibs/arm64-v8a/libkatago.so   交叉编译出的引擎（9.5 MB）
├── jniLibs/arm64-v8a/librapfi.so    外部五子棋引擎 Rapfi（3.7 MB，打包进 assets 后释放执行）
├── jniLibs/arm64-v8a/libeleye.so    外部象棋引擎 ElephantEye 象眼（513 KB，同上）
├── res/                    图标与字符串
├── tests/
│   ├── BoardTest.java     398 项：规则 / SGF 读写 / 分析解析 / 布局尺寸 / 棋盘几何
│   ├── GtpTest.java        77 项：GTP 客户端 + 真引擎整盘分析端到端（GameReview）
│   ├── GomokuTest.java    156 项：五子棋规则（连线 / 禁手 / 终局 / 深拷贝 / 坐标命名 / 重放）
│   ├── GomokuAITest.java   47 项：内置 AI（成五 / 堵五 / 禁手过滤 / 评估对称 / 自对弈整局）
│   ├── GomokuLearnTest.java   58 项：学习库（哈希稳定 / 记战绩与失误 / 候选挑选 / 只读 / 存档往返 / 淘汰清理）
│   ├── XiangqiTest.java       67 项：象棋规则（各子走法正反 / 蹩马腿 / 塞象眼 / 炮翻山 / 过河兵 / 九宫限制 / 将帅照面 / 将军·将死·困毙 / 非法着法不改局面 / copy 独立）
│   ├── XiangqiAITest.java     17 项：三档难度夹取 / 吃白送的车 / 确定性 / 只读棋盘 / 一步杀 / 评估符号与互补 / 终局与空盘返回 null
│   ├── EulaTest.java          23 项：用户协议（学习/禁止商业等关键词必须齐全、同意门槛、版本升级重弹）
│   ├── IntegrityGuardTest.java 37 项：编码常量还原、存档戳、来源校验、延迟报错状态机、宽限期边界
│   ├── XiangqiLearnTest.java  27 项：学习库（特征跨局一致 / 区域与过河判定 / 样本不足不加权 / 连胜正加权·连败负加权 / 存档往返 / 坏文件容错）＋ 引擎坐标与 FEN 换算
│   ├── XiangqiReviewTest.java 34 项：象棋复盘（逐手胜率 / 失误排行 / 推荐点 / 视角换算 / 空谱与终局）
│   ├── XiangqiTrendTest.java  10 项：象棋趋势/统计（走势与汇总口径）
│   ├── ui/XiangqiViewProbe.java 22 项：线 34 / 圆 128 / 文字 53、列标 A..I、红黑棋子字、选中与上一手、触摸命中与 0.55 格容差
│   ├── GomokuReviewTest.java  101 项：复盘（胜率换算 / 逐手亏损 / 推荐点 / 结论口径 / 非法手顺 / 开禁手复盘旧手顺 / 自对弈整局）
│   ├── RapfiGomokuTest.java    74 项：外部强引擎适配层（对着真 pbrain-rapfi 跑 Gomocup 协议；外加行序、半行卡死、超时重起与「忽略 TERM 的引擎也会被超时 / close() 真的收掉」钉桩；外加「管道塞满、没人读时 close() 也不卡，而且跳过 END」的钉桩；外加「重起出来的那个引擎也会被最后一次 close() 按 pid 收掉」的钉桩；跑不了就算失败）
│   ├── ui/fake/               37 个假 android 类：够在 JVM 里驱动真 GomokuActivity / GomokuView / GomokuReviewActivity（含 graphics / MotionEvent / Path）
│   ├── ui/Driver95.java       120 项：界面层桩测（棋盘 13/15/19 路、我执黑/白、悔棋轮次、复盘入口、终局设置留存、非 15 路强档自动走内置 AI；外加「点复盘的两句话次序」钉桩 —— 空盘/有手顺时只要电脑在思考就说等电脑、真闲着没下过才说还没落子）
│   ├── ui/ViewProbe.java      46 项：真 GomokuView 的触摸、几何与绘制（交点吸附、格子正中死区、全屏返回按钮命中与容差、换路数重算、onMeasure 四种规格；外加网格 2n 条线 / 坐标 2n 个文字且列跳过 I / 9 个星位 / 石身+边+最后一手标记 / 五连赢线 30+1 条且长 4×spacing / 手顺数字 / 推荐点提示圈 —— 自查发现的绘制缺口（先前的审计报告没提过这条））
│   ├── ui/MenuProbe.java      38 项：真 MenuActivity（右上角「公告」行与左下角版本行、两个入口按钮的 Intent 目标、深浅色与「按时间自动」、每分钟 tick 的注册与注销、版本号兜底）
│   ├── ui/ReviewProbe.java    94 项：真 GomokuReviewActivity（读 Intent 的默认值与长度门槛、选行夹取、失误排行阈值、黑白视角换算、空棋谱与终局文字；外加「点失误行 / 手顺行 / 终局行各跳到哪儿」、胜率条标签，以及「选中某一步时被涂底色的必须是那一行」的高亮回归钉桩）
│   ├── ui/stub/               1 个桩（MainActivity）：只为让 MenuActivity 的 Intent 目标能解析，探针不驱动它们
│   └── fixture_analyze.txt 真机跑出来的 kata-genmove_analyze 原始输出
└── src/com/autogo/
    ├── Board.java          围棋规则引擎（board.py 的 Java 移植）
    ├── Screen.java         读取屏幕尺寸 / 方向 / 密度
    ├── LayoutMath.java     布局尺寸与棋盘几何计算（纯函数，可单测）
    ├── WinrateBarView.java 胜率条（横屏右下角状态面板）
    ├── BoardView.java      棋盘绘制与触摸
    ├── GtpEngine.java      GTP 客户端（子进程 + 超时读取）
    ├── EngineManager.java  权重释放、配置生成、引擎启动、自检
    ├── Analysis.java       解析 kata-genmove_analyze 的 info 行
    ├── MenuActivity.java   启动选择页（围棋 / 五子棋）
    ├── MainActivity.java   围棋对局主界面
    ├── GomokuActivity.java 五子棋对局界面（棋盘 13 / 15 / 19 路、「我执」黑或白（执白时电脑先走）、可悔棋 / 认输 / 换难度 / 开关禁手 / 复盘；15 路优先 Rapfi 引擎，否则内置 AI）
    ├── GomokuReviewActivity.java 五子棋复盘界面（胜率条 / 曲线 / 失误排行 / 手顺，点哪一手都同步）
    ├── GomokuBoard.java   五子棋规则（连线 / 禁手 / 终局判定，纯 Java 可单测）
    ├── GomokuEngine.java  五子棋引擎接口（内置 AI 与外部 Rapfi 强引擎共用同一个接口）
    ├── GomokuView.java    五子棋棋盘绘制与触摸
    ├── LocalGomokuAI.java 内置五子棋 AI（棋型评估 + 迭代加深 α-β，纯 Java 可单测）
    ├── GomokuLearn.java 学习库（局面哈希 + 战绩/失误统计 + 候选挑选 + 落盘；纯 Java 可单测）
    ├── XiangqiBoard.java 象棋规则（各子走法、蹩马腿/塞象眼、炮需炮架、过河兵、士帅不出九宫、将帅照面、将死/困毙；纯 Java 可单测）
    ├── XiangqiAI.java 象棋内置 AI / 兜底（三档：搜索深度 1/2/3 + 子力评估 + 12 开局体系 + 学习加权；纯 Java 可单测；实现 XiangqiEngineApi）
    ├── XiangqiView.java 象棋棋盘绘制与触摸（10×9、九宫、楚河汉界、坐标、圆片棋子、选中/可走点/上一手、立体棋具用 BoardArt）
    ├── XiangqiActivity.java 象棋对局页（人执红先走、电脑执黑；新局/悔棋/认输/难度/全屏/返回、终局弹窗；优先 ElephantEye 引擎，失败退回内置 AI 并记录学习库）
    ├── Game2048.java       2048 规则（4×4 / 四方向滑动 / 同值合并一次 / 生成 2 或 4；纯 Java 可单测）
    ├── Game2048View.java   2048 棋盘绘制与滑动手势
    ├── Game2048Activity.java 2048 页面（得分 / 最高分 / 重开 / 存档）
    ├── SudokuGame.java     数独规则（生成 / 求解 / 唯一解校验 / 三档难度 / 行列宫冲突 / 笔记 / 撤销；纯 Java 可单测）
    ├── SudokuView.java     数独 9×9 棋盘绘制与点选（宫线加粗、冲突标红、候选数小字）
    ├── SudokuActivity.java 数独页面（数字键盘 / 笔记 / 提示 / 撤销 / 计时 / 难度切换；竖屏上下排、横屏左右排）
    ├── Changelog.java 更新公告（最近几版的要点，最新在前；纯 Java 可单测；启动页「公告」按钮的内容来源）
    ├── RapfiGomokuEngine.java 外部强引擎 Rapfi 的适配层（起子进程走 Gomocup 协议；找不到可执行文件就退回内置 AI）
    ├── XiangqiEngine.java 外部强引擎 ElephantEye 的适配层（起子进程走 UCCI 协议；坐标/FEN 换算；不可用就退回内置 AI）
    ├── XiangqiEngineApi.java 象棋引擎统一接口（内置 AI 与 ElephantEye 共用）
    ├── XiangqiLearn.java 象棋学习库（局面特征 + 战绩统计 + 选招加权 + 落盘；纯 Java 可单测）
    ├── NativeExe.java 外部可执行文件准备工具（assets → filesDir 释放 + chmod；绕开安装目录 exec 限制）
    ├── Eula.java 用户协议正文与版本判定（纯 Java，可单测）
    ├── EulaActivity.java 启动入口：用户协议页 + 完整性标记检查
    ├── BuildInfo.java 构建元数据（编码常量）与运行期一致性校验
    ├── IntegrityGuard.java 标记异常的延迟判定（宽限期内不报错；纯 Java，可单测）
    ├── GomokuReview.java  五子棋复盘分析（逐手胜率 / 失误排行 / 推荐点，纯 Java 可单测）
    ├── ReviewActivity.java 复盘
    ├── GameReview.java    整盘逐手分析 + 「这一局为什么赢」的结论（纯 Java，可单测）
    ├── WinrateChartView.java 胜率曲线
    ├── Theme.java         黑夜 / 白天两套配色
    ├── Palette.java       棋盘与棋子的颜色取值
    ├── UiTheme.java       主题档判定与窗口主题应用（选择页 / 五子棋页共用，与设置页同一份偏好）
    ├── CrashLog.java      崩溃日志（写进 App 私有目录）
    └── Sgf.java            SGF FF[4] 读写（含解析器）
```

---

## 六、验证到什么程度

装机之前能做的自动化验证都做了，`bash run_tests.sh` 一条命令跑完 **全部检查**（项数见目录树 / 脚本输出），**全过**。

**1. 引擎本体（在真实设备上实跑）**

- `./katago version` → `KataGo v1.18.2` / `Using Eigen(CPU) backend`
- 19 路 GTP 会话：`genmove B` → `R16`，`genmove W` → `Q4`（正常星位）
- 依赖只有 `libm/libdl/libc`，不需要额外的 `libc++_shared.so`

**2. 规则引擎 / SGF 读写 / 分析解析 / 布局尺寸 / 棋盘几何 / 胜负结论（398 项 JVM 断言）**

坐标转换、提子、自杀、劫、数子、SGF 编码与**读取**，且与桌面版 Python 实现
**逐用例交叉核对**。SGF 读取还拿桌面版自对弈产出的真实 19 路 337 手棋谱做了端到端验证。
分析解析用的是真机跑出来的 `kata-genmove_analyze` 原始输出当 fixture。

**3. GTP 客户端 + 真引擎整盘分析端到端（77 项，对着真 KataGo 进程）**

`GtpEngine` 只依赖 `java.io` / `Process`，没有任何 Android 类，所以能在 JVM 上直接
对着一个真的 KataGo 跑。覆盖：握手、banner 跳过、多行响应（`showboard`）、
`?` 开头的错误响应、非法落子、超时（`GtpTimeout`）、超时后引擎仍可用、
以及 `kata-genmove_analyze` → `Analysis.parse` 整条链路。

**4. 五子棋 / 象棋 / 军棋：规则 / 内置 AI / 复盘 / 学习库 / 外部引擎适配 / 界面层桩测（JVM 断言，项数与分项见目录树）**

第 ①③④ 步（规则 / AI / 复盘）都是纯 Java、不碰 Android，直接进 JVM 套件：`GomokuTest` 156 项
（横竖两种斜的连线、长连、四四 / 三三禁手与反例、和棋、终局后落子、深拷贝、坐标命名、重放旧手顺）、
`GomokuAITest` 47 项（一步成五优先、堵五、堵活三、自己做四、禁手点不落、终局 / 满盘返回 -1、
评估反对称与镜像为 0、同局面同手、自对弈整局无非法手）、`GomokuReviewTest` 101 项
（分数→胜率换算与饱和、逐手亏损算式与视角互补、推荐点与手顺命名、结论四种口径、
非法手顺报错、难度还原、自对弈整局）、`RapfiGomokuTest` 74 项（对着真的 `pbrain-rapfi` 进程
走 Gomocup 协议：坐标双向映射、空盘给点、成五 / 堵五 / 活四取胜、只读契约、终局返回 -1、进程重启与关闭、
行序（谁执黑 / 谁执白）、半行卡死的硬超时、超时杀进程后的自动重起、连忽略 TERM 的引擎也会被超时 / `close()` 真的收掉）。四条套件都做过「改错源码 ⇒ 断言变红」的反证：规则侧 15 条改法、AI 侧 7 条、
复盘侧 10 条、外部引擎适配层 12 条全部变红（另有 1 条真等价对照全绿：只把轮询间隔 10→20 ms）；
其中 4 条改法最初是**全绿漏网**，
补了断言才抓住（详见本文档 AG / AH 段）。

`tests/ui/` 的四项是**界面层桩测**（各探针当前项数见目录树）：在没有真机的构建环境里（`pm`/`am`/`logcat` 不可用、`android.jar` 是 android-34 平台包，安装与画面本身测不了），
于是用 **37 个手写的假 android 类**（含 `graphics` / `MotionEvent` / `Path`）在 JVM 里驱动**真的**界面类 ——
`GomokuActivity`、`MenuActivity`（启动选择页）、`GomokuView`（触摸、几何与绘制）、
`GomokuReviewActivity`（读 Intent、选行与点行跳步、高亮行回归钉桩、全档位胜率条与曲线）。
覆盖：棋盘 13 / 15 / 19 路与「我执」黑/白（执白时电脑先走、悔棋按轮次退到轮到你）、空盘与终局时改设置立刻生效、
进行中改设置只提示（点「新局」生效）、终局或进程内重建后设置仍记住、复盘页拿到的路数与先手正确、
以及**非 15 路时「强」档自动改走内置 AI**（外部引擎 Rapfi 只会下 15×15）。
它们都是「改错源码 ⇒ 断言变红」验证过的：棋盘视图 6 条改法、复盘页 9 条改法都会变红
（其中一条把探针直接撞崩，也算抓住）。一条**已知限制**写在 `Driver95` 头部：
`sun.misc.Unsafe.allocateInstance` 不跑字段初始化器，所以 `setDefaults()` 是「声明默认值」的
手抄镜像 —— 改声明默认值而忘了改镜像，探针看不出来。

**5. 外部引擎执行 —— 两种方式都在真机/策略层面确认过**

这是整个方案里唯一无法在构建环境内实跑的关键假设（`/data/app` 不在构建容器的挂载命名空间里）。
但 AOSP 的策略文件写得很明白：

```
# system/sepolicy/private/app.te
# Allow apps to read/execute installed binaries
allow appdomain apk_data_file:dir  { open getattr read search ioctl lock };
allow appdomain apk_data_file:file { getattr open read ioctl lock map x_file_perms };
```

`x_file_perms` 包含 `execute_no_trans`，而 `nativeLibraryDir` 里的文件正是
`apk_data_file` 标签，`untrusted_app` 继承自 `appdomain`。注释就是「允许 App 执行
已安装的二进制」。参考实现 `youtubedl-android` 用的也是同一套办法。（KataGo 已在真机跑通。）

v197 起五子棋 Rapfi / 象棋 ElephantEye 改用更稳的 `assets` 释放方式（见 3.2 节）：二进制打包进
`assets/engine/`，运行时由 `NativeExe` 释放到 `filesDir`（`app_data_file` 标签，属 App 自己）再执行。
可用性判断不再只看 `File.canExecute()`（它测不出 SELinux 拦截），而是**真的起一次进程走一步自检**，
通过才启用、失败退回内置 AI —— 这样即使某台机器 exec 受限，用户也不会「看着像在用引擎、其实在跑内置 AI」。

**10. 终局说明「为什么赢」**

原来终局只报一个数目：「终局：B+3.5（黑 65.0 : 白 61.5，含贴目 7.5）」。看不出赢在哪。

现在终局后**自动逐手分析整盘**，在状态区给出一行结论，例如：

```
终局：黑胜 3.5 目｜白第 87 手 Q16 亏 23%（应下 Q4），黑棋第 88 手起再未落后
```

判定逻辑在 `GameReview`（**纯 Java，不依赖 Android**，所以能上 JVM 直测）：

- 找出全局**最大的那一次失误**：谁、第几手、下在哪、亏了多少、引擎原本推荐什么。
  这一手通常就是「为什么赢」——败方把棋送出去的那一手。
- 再补一句胜方**从哪一手起没再落后**。分支：全程压制 / 从头领先到尾 / 第 N 手起再未落后 /
  胜负到最后才见分晓（赢方终局胜率仍不足五成，多半是目数和胜率判断不一致，此时只说「接近」，
  不断言谁领先）。
- 全程亏损都低于 5% 就不硬编败着，直接说「全程没有明显失误」，避免把细棋波动写成败着。
- 最大失误若是**胜方自己**下的，措辞改成「但没影响胜负」，不会倒因为果。

实现要点：

- 分析跑在主线程之外的 `engineExec` 上（单线程），和落子/搜索天然串行，不会串包。
- 进度实时显示（`正在分析胜负原因… 12 / 87`），**可随时打断**：开新局或悔棋会作废这一轮
  （用自增的「分析代号」判断，而不是布尔标志 —— 布尔会被下一轮启动时清掉，导致上一轮
  永远收不到取消信号）。
- 分析用的 `kata-genmove_analyze` 会顺手落一子且最后一手不 undo，所以跑完要把引擎盘面
  **重放对齐**回当前棋谱。
- 结果缓存下来传给复盘页（`EXTRA_WR/LOSS/BEST/BESTPV/VERDICT`），点「复盘」直接看明细，
  **不会重算第二遍**（19 路完整复盘要等一两分钟，重算很亏）。复盘页会校验数组长度对得上才采用，
  宁可重算也不用一份和棋谱不匹配的数据画曲线。
- 实测确认：**配置里的 `maxVisits` 生效，命令行的 `1000` 被忽略**（config=50 → visits 31；
  config=1000 → visits 972）。所以复盘速度由启动引擎时的 `-override-config` 决定，那个 `1000`
  不构成浪费。

## 黑夜 / 白天模式

设置里多了一项「界面主题」，四种模式：

| 模式 | 行为 |
| --- | --- |
| 跟随系统 | 用系统的深色开关 |
| 白天 | 始终亮色 |
| 黑夜 | 始终深色 |
| 按时间自动 | 19:00 ~ 07:00 自动转深色，到点自己换 |

- 判定逻辑在 `Theme.java`，**纯函数**（模式 + 系统深色 + 钟点 → 该不该用深色），
  所以 24 个钟点 × 4 种模式都能在 JVM 上钉死。最容易写反的就是 19:00 / 07:00 两个端点，
  测试里专门断言了 06:59 是夜间、07:00 是白天。
- 配色集中在 `Palette.java`（明暗两套）。所有界面都从这里取色，不再散在各地写死。
  棋盘 / 胜率条 / 胜率曲线三个自绘 View 各加了 `setPalette()`。
- 测试不只「能编译」，还钉了**对比度**（`Palette.contrast` 走 WCAG 公式）：
  正文 ≥ 4.5:1、次要文字 ≥ 3:1、按钮文字 ≥ 4.5:1、曲线和游标 ≥ 3:1。
- **深色模式的一个坑**：深色棋盘木色的亮度和黑子很接近，黑子会糊在棋盘里。
  所以深色模式下棋子描边从 `0x33000000` 换成 `0x66FFFFFF`（浅色描边），
  测试里把描边合成到棋盘上再算对比度，确保 ≥ 1.5:1。

**几点实现上的选择：**

- **没走 `recreate()`。** 改主题时 Activity 重建会重置对局（`onCreate` 里会 `newGame`）。
  所以改设置只重刷已有控件的颜色（`applyPaletteToViews()`），对局和棋谱都不动。
  顺带 `setTheme()` 也切一次，让**之后**新建的对话框是新主题。
- **manifest 的 `configChanges` 加了 `uiMode`。** 不加的话系统切深浅色会重建 Activity，
  一局棋就没了。加了之后走 `onConfigurationChanged`，只重判配色 + 重建视图。
- **「按时间自动」靠两处触发**：`onResume`（可能搁了一夜才回来）+ 前台每分钟一次
  （`themeTick`，`onPause` 里撤掉）。
- 棋盘上那些**语义色**故意不跟主题变：最后一手标记、棋子上按对比取的黑/白字、
  幽灵子、形势归属底纹、全屏按钮、棋子渐变 —— 它们代表的是「黑/白/提示」而不是「亮/暗」。

## 八、已知限制

**体积**
- 三个网络都打进 APK，所以包有 120 MB。想瘦身可以把 b18c384nbt 改成「首次启动联网下载」的可选项，能回到 30 MB 左右。

**围棋 / 复盘**
- 复盘默认每手最多 200 visits，一局 300 手大约要几分钟；分析期间可以退出。
- 只判简单劫，不判位置超级劫（与桌面版口径一致，为的是不误杀引擎给出的合法手）。
- 不支持让子棋（SGF 里的 `AB`/`AW` 摆子能读进来，但界面没有设置让子的入口）。
- 复盘的「最大失误」在低 visits 下会跳：两手损失接近时搜索越小越不稳定。手顺、胜率曲线、单手的损失都是实数，
  但「关键在第 N 手」这类结论要当成噪声范围内的提示看；想稳就把复盘 visits 调高。
- 复盘屏目前不显示**主变化（PV）**：引擎算了、也存了、也传进 Intent 了，但界面没有读取点。
  功能表里的「引擎推荐点」是 `bestMove`（有显示），PV 属于多算了一步。
- 复盘用的临时棋谱放在 App 私有缓存目录，正常路径都会删；进程被杀会残留，攒到系统清缓存为止。
- 引擎关闭是异步的：点返回不等引擎退出，引擎可能在页面走掉之后才真正结束（空闲 0.04 s，
  长搜索时几秒到十几秒），不会常驻。这段时间新旧引擎可能同时占着模型内存，窗口上限是 `close()` 里的 3 秒等待。

**SGF 读写**
- 读取只取主线，遇到分支图会停在分支点；**如果主线在变化图之后还继续，后面那些手会被漏掉**，
  界面会明说「（主线在变化图之后还有手，只读入了前 N 手）」。只含变化图、一手没丢的谱则提示
  「（本谱含变化图，分支未读入）」。多局棋谱只读第 1 局，同样会提示。
- 保存棋谱只写**盘面信息**（手顺、摆子、让子、贴目、结果），**不写回**题谱里的 `C`（说明文字）、
  `GN`（局名）、`LB`/`TR`（标记）。盘面不会错，文字和标记会丢。
- 打包坐标写法（`AB[aabb]`）是历史扩展，仍支持；代价是 `AB[dead]` 这种「看着像单词、其实是合法坐标对」
  的值会被当 2 颗子（`AB[pass]`/`AB[resign]` 已单独排除）。
- `AB[tt]`（历史虚手写法）在 ≤19 路被当 0 子忽略。

**让子 / 摆子**
- 让子谱的先后手按「本谱先走的一方」算（主线首手颜色 → `PL[...]` → `HA[n]≥2` 时白先），不用手数奇偶。
  快照与保存的棋谱都会写 `PL[...]`，因为真引擎服从 `PL` 并覆盖它自己的推断，而「只有 `AB` 摆子」
  「`HA` 与 `AW` 并存」这类形态引擎的推断未必与我们一致。
- `loadsgf` 之后 `showboard` 会多一行 `Handicap bonus score: N`（引擎自己的让子约定报数）。
  App 的胜负判定用自己的 `scoreArea()`、**从不消费引擎的 `final_score`**，所以这行不影响判定。
- 引擎同步带摆子的局面走 `loadsgf`（GTP 没有通用的「摆一个白子」命令）；万一写临时谱或 `loadsgf` 失败，
  会退回只重放手顺的旧路径 —— 那时引擎看不到摆子（不会崩，但分析会偏）。
- 零气死子没法交给引擎（引擎不接受这种局面），此时复盘退回「空盘重放」，界面会明确说明只作参考。

**其他**
- 棋谱目录里放满 9999 局后，再保存会报错而不是覆盖最后一局。
- 引擎日志写在 App 私有目录的 `engine.log`，出问题时可以导出。
- 界面层探针用 `sun.misc.Unsafe.allocateInstance` 建实例，它不跑字段初始化器，所以 `setDefaults()`
  是「声明默认值」的手抄镜像 —— 改声明默认值而忘了改镜像，探针看不出来。
- 外部引擎（Rapfi / ElephantEye）是否在具体某台设备上真的被拉起，取决于该机的 SELinux 策略；
  启动前会真跑一次自检，失败就退回内置 AI 并在状态条说明。

---


## 许可证 / License

本项目采用 **GNU Affero General Public License v3.0（AGPL-3.0）** 许可，全文见 [`LICENSE`](LICENSE)。
This project is licensed under the **AGPL-3.0** — see [`LICENSE`](LICENSE).
