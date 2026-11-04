# 陪练棋盘 · Android 版

**简介 / Introduction（中英）**

陪练棋盘是一个 Android 六合一棋类练习 App：**围棋**（KataGo 真引擎，9/13/19 路）、**五子棋**（内置 AI，可选外部 Rapfi 引擎）、**象棋**（内置三档 AI + 逐手复盘）、**军棋**（明棋 / 暗棋，人执红、电脑执黑）、**2048**（4×4 数字合并）、**数独**（9×9，简单 / 普通 / 困难三档，困难档极难）。

Peilian Qipan (Board-Game Practice) is an all-in-one Android app for four board games: **Go** (real KataGo engine, 9/13/19), **Gomoku** (built-in AI, optional external Rapfi engine), **Xiangqi** (Chinese chess; three AI levels + move-by-move review) and **Junqi** (Army chess; open or hidden mode, you play red against the computer).

- 版本 / Version: **v190**（`1.20261020.190`，versionCode 190）
- 构建 / Build: `bash build.sh`（需要 `assets/` 里三个 KataGo 权重、`jniLibs/arm64-v8a/` 的 `.so`）
- 测试 / Test: `bash run_tests.sh`（JVM 用例 **1724 项**，最近一次全过）
- 安装包 / APK: 构建产物 `autogo.apk`（约 122 MB，已签名，minSdk 26 / targetSdk 34）；同版本 APK 见仓库 **Releases**

---

把 KataGo 真正装进 Android App 的围棋程序：人机对弈、引擎自动对弈、以及**每一局的逐手复盘**。

产物：`autogo.apk`（构建产物，约 122 MB，已签名，minSdk 26 / targetSdk 34）
同一份也复制到了 `/sdcard/Download/陪练棋盘.apk`，方便直接点开安装。

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

容器里装不了（原因见文末「为什么没能在本机验证」），需要手动装：

1. 用文件管理器打开 `/sdcard/Download/陪练棋盘.apk`
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

## 三、引擎是怎么塞进 App 的

KataGo 官方 release 只有 x86_64，Android 上也不能跑 glibc 二进制，所以：

1. **用 NDK 的 sysroot + 系统原生 clang 交叉编译**出 aarch64-android 的 katago
   （NDK 自带的 clang 只有 linux-x86_64 prebuilt，在这台 aarch64 机器上根本起不来）
2. 二进制命名成 `libkatago.so` 放进 `jniLibs/arm64-v8a/`，
   manifest 里开 `android:extractNativeLibs="true"`，
   系统会把它释放到 `nativeLibraryDir` —— **只有那个目录允许 App 执行文件**
3. App 用 `ProcessBuilder` 把它当子进程拉起，走 GTP 文本协议通信

第 2 条是这套方案的关键，参考实现是 `youtubedl-android`（同样把 Python 可执行文件
命名成 `libpython.so` 再 exec）。

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
├── run_tests.sh            装机前能跑的全部自动化验证（1209 项 = 398 + 156 + 47 + 101 + 58 + 74 + 77 + 298）
├── make_icons.py           纯标准库生成启动图标
├── assets/                 三个权重（b6c96 5 MB / b10c128 14 MB / b18c384nbt 93 MB）
├── tools/rapfi/            外部引擎的宿主编译版 + 构建记录（NOTICE.md：上游 commit 与两处补丁）
├── jniLibs/arm64-v8a/libkatago.so   交叉编译出的引擎（9.5 MB）
├── jniLibs/arm64-v8a/librapfi.so    可选的外部五子棋引擎（3.7 MB，同样从 nativeLibraryDir 执行）
├── res/                    图标与字符串
├── tests/
│   ├── BoardTest.java     398 项：规则 / SGF 读写 / 分析解析 / 布局尺寸 / 棋盘几何
│   ├── GtpTest.java        77 项：GTP 客户端 + 真引擎整盘分析端到端（GameReview）
│   ├── GomokuTest.java    156 项：五子棋规则（连线 / 禁手 / 终局 / 深拷贝 / 坐标命名 / 重放）
│   ├── GomokuAITest.java   47 项：内置 AI（成五 / 堵五 / 禁手过滤 / 评估对称 / 自对弈整局）
│   ├── GomokuLearnTest.java   58 项：学习库（哈希稳定 / 记战绩与失误 / 候选挑选 / 只读 / 存档往返 / 淘汰清理）
│   ├── XiangqiTest.java       67 项：象棋规则（各子走法正反 / 蹩马腿 / 塞象眼 / 炮翻山 / 过河兵 / 九宫限制 / 将帅照面 / 将军·将死·困毙 / 非法着法不改局面 / copy 独立）
│   ├── XiangqiAITest.java     17 项：三档难度夹取 / 吃白送的车 / 确定性 / 只读棋盘 / 一步杀 / 评估符号与互补 / 终局与空盘返回 null
│   ├── ui/XiangqiViewProbe.java 22 项：线 34 / 圆 128 / 文字 53、列标 A..I、红黑棋子字、选中与上一手、触摸命中与 0.55 格容差
│   ├── GomokuReviewTest.java  101 项：复盘（胜率换算 / 逐手亏损 / 推荐点 / 结论口径 / 非法手顺 / 开禁手复盘旧手顺 / 自对弈整局）
│   ├── RapfiGomokuTest.java    74 项：外部强引擎适配层（对着真 pbrain-rapfi 跑 Gomocup 协议；外加行序、半行卡死、超时重起与「忽略 TERM 的引擎也会被超时 / close() 真的收掉」钉桩；外加「管道塞满、没人读时 close() 也不卡，而且跳过 END」的钉桩；外加「重起出来的那个引擎也会被最后一次 close() 按 pid 收掉」的钉桩；跑不了就算失败）
│   ├── ui/fake/               37 个假 android 类：够在 JVM 里驱动真 GomokuActivity / GomokuView / GomokuReviewActivity（含 graphics / MotionEvent / Path）
│   ├── ui/Driver95.java       120 项：界面层桩测（棋盘 13/15/19 路、我执黑/白、悔棋轮次、复盘入口、终局设置留存、非 15 路强档自动走内置 AI；外加「点复盘的两句话次序」钉桩 —— 空盘/有手顺时只要电脑在思考就说等电脑、真闲着没下过才说还没落子）
│   ├── ui/ViewProbe.java      46 项：真 GomokuView 的触摸、几何与绘制（交点吸附、格子正中死区、全屏返回按钮命中与容差、换路数重算、onMeasure 四种规格；外加网格 2n 条线 / 坐标 2n 个文字且列跳过 I / 9 个星位 / 石身+边+最后一手标记 / 五连赢线 30+1 条且长 4×spacing / 手顺数字 / 推荐点提示圈 —— 自查发现的绘制缺口（先前的审计报告没提过这条））
│   ├── ui/MenuProbe.java      38 项：真 MenuActivity（右上角「公告」行与左下角版本行、两个入口按钮的 Intent 目标、深浅色与「按时间自动」、每分钟 tick 的注册与注销、版本号兜底）
│   ├── ui/ReviewProbe.java    94 项：真 GomokuReviewActivity（读 Intent 的默认值与长度门槛、选行夹取、失误排行阈值、黑白视角换算、空棋谱与终局文字；外加「点失误行 / 手顺行 / 终局行各跳到哪儿」、胜率条标签，以及「选中某一步时被涂底色的必须是那一行」的高亮回归钉桩 —— 第 54 轮审计 低-1）
│   ├── ui/stub/               1 个桩（MainActivity；原写「2 个」有误，第 54 轮审计指出）：只为让 MenuActivity 的 Intent 目标能解析，探针不驱动它们
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
    ├── GomokuActivity.java 五子棋对局界面（棋盘 13 / 15 / 19 路、「我执」黑或白（执白时电脑先走）、可悔棋 / 认输 / 换难度 / 开关禁手 / 复盘）
    ├── GomokuReviewActivity.java 五子棋复盘界面（胜率条 / 曲线 / 失误排行 / 手顺，点哪一手都同步）
    ├── GomokuBoard.java   五子棋规则（连线 / 禁手 / 终局判定，纯 Java 可单测）
    ├── GomokuEngine.java  五子棋引擎接口（内置 AI 与将来的外部强引擎共用同一个接口）
    ├── GomokuView.java    五子棋棋盘绘制与触摸
    ├── LocalGomokuAI.java 内置五子棋 AI（棋型评估 + 迭代加深 α-β，纯 Java 可单测）
    ├── GomokuLearn.java 学习库（局面哈希 + 战绩/失误统计 + 候选挑选 + 落盘；纯 Java 可单测）
    ├── XiangqiBoard.java 象棋规则（各子走法、蹩马腿/塞象眼、炮需炮架、过河兵、士帅不出九宫、将帅照面、将死/困毙；纯 Java 可单测）
    ├── XiangqiAI.java 象棋内置 AI（三档：搜索深度 1/2/3 + 子力评估；纯 Java 可单测）
    ├── XiangqiView.java 象棋棋盘绘制与触摸（10×9、九宫、楚河汉界、坐标、圆片棋子、选中/可走点/上一手、立体棋具用 BoardArt）
    ├── XiangqiActivity.java 象棋对局页（人执红先走、电脑执黑；新局/悔棋/认输/难度/全屏/返回、终局弹窗）
    ├── Game2048.java       2048 规则（4×4 / 四方向滑动 / 同值合并一次 / 生成 2 或 4；纯 Java 可单测）
    ├── Game2048View.java   2048 棋盘绘制与滑动手势
    ├── Game2048Activity.java 2048 页面（得分 / 最高分 / 重开 / 存档）
    ├── SudokuGame.java     数独规则（生成 / 求解 / 唯一解校验 / 三档难度 / 行列宫冲突 / 笔记 / 撤销；纯 Java 可单测）
    ├── SudokuView.java     数独 9×9 棋盘绘制与点选（宫线加粗、冲突标红、候选数小字）
    ├── SudokuActivity.java 数独页面（数字键盘 / 笔记 / 提示 / 撤销 / 计时 / 难度切换；竖屏上下排、横屏左右排）
    ├── Changelog.java 更新公告（最近几版的要点，最新在前；纯 Java 可单测；启动页「公告」按钮的内容来源）
    ├── RapfiGomokuEngine.java 外部强引擎 Rapfi 的适配层（起子进程走 Gomocup 协议；找不到可执行文件就退回内置 AI）
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

**1. 引擎本体（在本机真机上实跑）**

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

**4. 五子棋：规则 / 内置 AI / 复盘 / 外部引擎适配 / 界面层桩测（JVM 断言，项数与分项见目录树）**

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

`tests/ui/` 的四项是**界面层桩测**（各探针当前项数见目录树）：容器里没有真机（`pm`/`am`/`logcat` 不可用、`android.jar` 是 android-34 **真**平台包（容器里没有真机/设备，仍测不了安装与画面）），
于是用 **37 个手写的假 android 类**（含 `graphics` / `MotionEvent` / `Path`）在 JVM 里驱动**真的**界面类 ——
`GomokuActivity`、`MenuActivity`（启动选择页）、`GomokuView`（触摸、几何与绘制）、
`GomokuReviewActivity`（读 Intent、选行与点行跳步、高亮行回归钉桩、全档位胜率条与曲线）。
覆盖：棋盘 13 / 15 / 19 路与「我执」黑/白（执白时电脑先走、悔棋按轮次退到轮到你）、空盘与终局时改设置立刻生效、
进行中改设置只提示（点「新局」生效）、终局或进程内重建后设置仍记住、复盘页拿到的路数与先手正确、
以及**非 15 路时「强」档自动改走内置 AI**（外部引擎 Rapfi 只会下 15×15）。
它们都是「改错源码 ⇒ 断言变红」验证过的：第 51 轮那两条缺陷没修时，界面层那 62 项里有 6 项是红的；
棋盘视图 6 条改法、复盘页 9 条改法也都变红（其中一条把探针直接撞崩，也算抓住）。一条**已知限制**写在
`Driver95` 头部：`sun.misc.Unsafe.allocateInstance` 不跑字段初始化器，所以 `setDefaults()` 是「声明默认值」的
手抄镜像 —— 改声明默认值而忘了改镜像，探针看不出来（第 52 轮审计低-2）。

**5. 「从 nativeLibraryDir 执行二进制」—— 已从 SELinux 策略层面确认可行**

这是整个方案唯一没法本地实跑的关键假设（`/data/app` 不在容器的挂载命名空间里）。
但 AOSP 的策略文件写得很明白：

```
# system/sepolicy/private/app.te
# Allow apps to read/execute installed binaries
allow appdomain apk_data_file:dir  { open getattr read search ioctl lock };
allow appdomain apk_data_file:file { getattr open read ioctl lock map x_file_perms };
```

`x_file_perms` 包含 `execute_no_trans`，而 `nativeLibraryDir` 里的文件正是
`apk_data_file` 标签，`untrusted_app` 继承自 `appdomain`。注释就是「允许 App 执行
已安装的二进制」。参考实现 `youtubedl-android` 用的也是同一套办法。

**6. 这轮测试抓出的 bug**

- `Analysis.parse()` 原本取**第一个**非 info 行当落子，落子其实总在 info 流**之后**，
  任何夹在中间的行都会被误认。已改成取最后一个。
- `MainActivity` 把根布局套在 `ScrollView` 里，而棋盘用的是 `height=0 + weight=1`。
  ScrollView 以 UNSPECIFIED 高度测量子视图，LinearLayout 分不了权重，
  棋盘会**塌成 0 高度、整块看不见**。已改为直接 `setContentView(root)`。
- `BoardView.onTouchEvent` 在布局完成前 `spacing` 为 0，除法得 NaN 被 `Math.round`
  变成 0，表现为「点哪都落在 A19」。已加保护。
- 复盘列表逐手 `setText` 是 O(N²)，300 手时明显卡顿。已改成每 5 手刷一次。
- `GtpEngine` 超时后响应流就错位了（引擎还在搜，响应稍后才到），原来会继续读，
  把上一条命令的残留当成这一条的响应，静默产生错位结果。现在超时即把引擎标记为
  不可用并拒绝后续命令；`MainActivity` 收到超时会自动重启引擎并重放当前局面。
- `EngineManager.ensureModel()` 在拿不到 asset 长度时会每次启动都白拷 14 MB 权重。
  现在拿不到长度就退化成「存在且非空即视为就绪」。

**7. 全屏 / 状态面板这一轮，自查又抓出 4 个（都是我这几轮自己引入的）**

- **状态面板整块看不见**：`statusPanel` 用了 `height=0 + weight=1`，却被放进 `ScrollView` 里的
  LinearLayout。ScrollView 以 UNSPECIFIED 高度测量子视图，权重分不了 → 面板塌成 0 高。
  **这是同一个坑第二次踩**（前面棋盘那次已经踩过一次并写进了本文档）。已改成
  `WRAP_CONTENT + setMinimumHeight`。
- **全屏横屏棋盘下半截被切掉**：`recompute()` 只拿宽度算间距，忽略高度。非全屏时视图是
  正方形所以看不出来；全屏时视图是 w×h，横屏下棋盘垂直方向需要的高度远超屏幕。
  已改为按**较短边**计算并居中，并把这段几何抽到 `LayoutMath.boardGeometry()`（纯函数，
  可单测）。把代码退回旧写法跑测试会报 `横屏全屏 越界: bottom=1600.7 (view 2340x1080)`，
  即棋盘超出屏幕 520px —— 确认测试真的能抓到这个问题。
- **全屏时转屏状态错乱**：`onConfigurationChanged` 无条件调 `rebuildLayout()`，会把界面
  切回普通布局，而 `fullscreen` 字段仍是 `true`，再点「全屏」反而走退出分支。已改为
  全屏时重建全屏布局；同时补了 `onWindowFocusChanged` 重新应用沉浸式标志（从别的应用
  切回来时系统栏会重新出现）。
- **读谱可能 OOM**：文件选择器用的是 `*/*`，误选大文件会把整个文件读进内存。已加 4 MB 上限。

**8. 全屏按钮闪退（用户实测报的）**

- **按一下就闪退**：进全屏的实现是「新建一个只含棋盘的内容视图，再把 boardView 搬过去」，
  即 `fsRoot.addView(boardView)`。但 boardView 早在 `buildUi()` 里就被 `rootLayout.addView()`
  加过了，`ViewGroup.addView()` 遇到已有 parent 的子视图会抛
  `IllegalStateException: The specified child already has a parent. You must call removeView()
  on the child's parent first.`
  已改成**就地**全屏：不重建内容视图，只把控件区 `setVisibility(GONE)`、根布局改黑底无边距、
  棋盘的 LayoutParams 换成 `MATCH_PARENT + weight=1`。既没有 reparent，也不必在触摸派发
  过程中替换整棵视图树。
- **全屏里可能出不来**：`onTouchEvent` 里 `!inputEnabled` 的提前 return 写在全屏按钮判定**之前**，
  于是引擎思考中、自动对弈时 `inputEnabled=false`，退出按钮点不动，只能靠物理返回键。
  已把全屏按钮判定提到方法最前面，且不受 `inputEnabled` 影响。
- 顺带：全屏按钮的进/出改为 `post` 延后一拍执行；`drawFullscreenButton` 里每帧 `new Paint()`
  改成字段复用。

**9. 日志 / 内存自查（用户问「日志是不是没上限、把内存堆爆了」）**

结论：日志确实是**无上限**的，但它在**磁盘**上，不是内存。逐条实测：

- **`logDir` 每启动一次引擎就新建一个带时间戳的 `.log`，且从不删除。**
  用与 App 完全相同的配置实测连跑 3 次：`1 个文件 12K` → `2 个 20K` → `3 个 28K`，
  每次 +8 KB（内容只有启动横幅）。触发点有：App 启动、改设置、搜索超时重启。
  已改为每次启动前清空 `logs/`（KataGo 的通信/搜索日志本来就都关着，没有保留价值）。
- **`engine.log`（我们抓的 stderr）用的是 append 模式、无上限。** 已加 1 MB 上限。
- **引擎的 RAM 是正常的**：实测 `nnCacheSizePowerOfTwo = 20` 时峰值 RSS 132 MB
  （改成 16 是 115 MB），没有出现「缓存按 2^20 预分配几个 G」的情况。
- **跨局没有内存累积**：`ReviewActivity` 用的是定长数组（`wrToMove`/`bestMove`/`bestPv`/`loss`），
  `Analysis.Result` 每手用完就丢；`Board.moves` 每局新建。查过一遍没有只增不减的集合。

顺带查出并修掉两个**真正的**内存问题：

- **每画一颗子就 `new` 一个 `RadialGradient`**。19 路盘一屏近 300 颗子，每次重绘 300 次分配，
  一局下来几十万次 —— GC 压力主要来自这里。已改成「尺寸变化时建一次、所有棋子复用」：
  画之前把画布平移到棋子中心、在原点作画，渐变的坐标就与棋子位置无关了（渲染结果完全一致）。
- **`Analysis.parse` 不去重**。`kata-genmove_analyze` 是流式输出，同一个候选点会被反复上报；
  早先来一条 add 一条，一次搜索能堆出上千条、每条还带完整 PV 字符串。
  已改成「同一个 move 只留最新一条」。**反向验证**：退回旧写法后测试报
  `原始 info 85 条 -> 唯一 85 条`、`期望 17，实际 85` —— 一个小 fixture 就有 5 倍冗余。

另外 SGF 对局记录是一局一个文件、约 2 KB，也在累积。这是**你的棋谱**，不自动删，
需要清理时在设置里能看到目录位置。

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

**10. 更新装不上的真正原因：每个 APK 的签名都不一样**

用户反馈「更新时系统认为旧版本是高版本」。查下来**跟版本号无关** —— 版本号一直是单调递增的
（`versionCode` 24 → 25 → …，`aapt2 dump badging` 和已装应用都对得上）。真正的问题是**签名**：

- `build.sh` 第 2 步是 `rm -rf "$OUT"`（`OUT=/tmp/appbuild`），而密钥库写的是
  `KS="$OUT/debug.keystore"`。于是后面那句 `if [ ! -f "$KS" ]` **每次构建都成立**，
  **每次重新生成一把新密钥**。
- Android 不允许用**不同签名**的 APK 覆盖已安装的应用，所以更新直接失败，
  表现就是「装不上新版本」。用同一个密钥签名是覆盖安装的前提。

**实测证据**（连打两次包，比对签名指纹）：

```
构建前: fd92dd7e9c568397682d45a01bed4bd1bf00ea7393c70ce95027a5d355384db9
构建后: 281ba3197c1838cb63ceb76cddf0c2c74df617bb149a8e1fa545f2bf18a9532f
```

修法：密钥库移到 `$PROJ/autogo-debug.keystore`（不会被清空），只在缺失时生成一次。
修完连打两次包，指纹完全一致（`2a8de3ac…`）—— 覆盖安装的前提就满足了。
密钥库不会被打进 APK（已用 `unzip -l` 确认）。

**一次性代价**：手机上已装的那份是用**某个已删除的随机密钥**签的，无法恢复，
所以必须**卸载一次**再装新包；之后所有更新都能直接覆盖。卸载会清掉 App 私有目录里的
棋谱和设置（容器读不到那个目录，没法代为备份；用 Magisk/KernelSU 或 Shizuku 的文件管理器
可以先拷出 `/data/data/com.autogo/files/`）。

顺带修的两处版本号问题：

- **`versionCode` 现在有下限保护**：取 `VERSION` 文件和 manifest 里现有值中的较大者再 +1。
  万一 `VERSION` 被回滚或丢失，也不会打出比已安装更低的号。验证：把 `VERSION` 改成 5，
  构建仍输出 28（而不是 6）。
- **版本名里的日期改用本地时间**（原来是 `date -u`，澳门 UTC+8，本机 0～8 点的构建会显示成昨天）。

**崩溃自记录**：因为容器里的 logcat 读不到这个应用的日志（缓冲区 256 KiB、只回 1 行有效内容），
用户也没法把日志导出来，所以加了 `CrashLog`：全局未捕获异常处理器把栈写进 App 私有目录，
下次启动直接弹窗显示，并可一键复制。这样万一还有别的问题，一轮就能拿到栈。

另外把全屏返回按钮的「绘制矩形」和「点击判定」统一成同一个 `fullscreenButtonRect()`——
之前画的是 `spacing*1.8 × spacing*1.0`、点的是 `spacing*1.2` 的方块，两者对不上。
全屏时棋盘顶部还会预留按钮高度，避免 19 路盘被压住左上角一片交叉点。

**已在真机上确认（用户实测）：**

- APK 在 Android 16（Honor，SM7550）上安装成功并**能正常对弈**。
  这一条同时验证了整个方案里唯一没法在容器内实跑的关键假设 ——
  **从 `nativeLibraryDir` 执行二进制在 Android 16 上确实可行**，
  与第 4 节从 AOSP 策略推断出的结论一致。

**尚未逐项确认：**

- 「自动对弈」和「复盘」两个功能还没拿到实测反馈（已确认的是人机对弈）。

如果哪一项有问题，App 会弹出**具体原因**（可执行文件路径、`canExecute`、
实际 exec 失败的异常文本）；设置页里也有一个「自检」，会真跑一次二进制并回报
退出码和输出。把那段文字发来就能定位。

---

## 七、为什么没能在本机验证

这台设备上的 Ubuntu 容器运行在 `com.dsh.client` 这个 App 的身份下
（uid 10308，SELinux 域 `untrusted_app`）。注意：容器里 `id` 会**骗你**——

```
$ id
uid=0(root) ... context=u:r:untrusted_app:s0:c52,c257,c512,c768

$ grep ^Uid /proc/self/status
Uid:	10308	10308	10308	10308        # 这才是内核眼里的真实身份
```

容器用了伪造 `getuid()` 的沙箱层，所以 `setuid(2000)` 会返回成功却毫无作用
（Binder 侧永远看到 10308）。这点很能说明问题：不要相信容器里 `id` 的输出。

试过并全部失败的路子：

| 尝试 | 结果 |
|---|---|
| `pm install` 直接装 | `SecurityException: ... is calling from uid u0a308` |
| 换到 `/data/local/tmp` | SELinux 标签是 `app_data_file`，`system_server` 读不了 |
| `chcon u:object_r:shell_data_file:s0` 改标签 | 静默失败（`untrusted_app` 没有 relabel 权限） |
| `--user 0` | 过了用户检查，栽在 `AppOpsService` 的包名 NPE |
| `-i com.dsh.client` | 过了归属校验，但 stdin 流式安装是 reverse mode，只允许 shell/system |
| `setuid(2000)` 冒充 shell | 返回 0 但内核 uid 不变，Binder 仍是 10308 |
| `am start` 拉起安装器 UI | `package=com.android.shell does not belong to uid=10308` |
| 屏幕自动化 `android_get_state` | `SCREEN_TARGET_UNAVAILABLE` |
| 读 `/data/app`（想直接验证 exec） | 不在容器的挂载命名空间里 |

结论：在这个容器里装 App 是**做不到**的，不是没找对方法。所以最后一步只能交给你。

---

## 八、已知限制

- 三个网络都打进 APK，所以包有 120 MB。想瘦身的话可以把 b18c384nbt 改成
  「首次启动联网下载」的可选项，APK 能回到 30 MB 左右。
- 复盘默认每手最多 200 visits，一局 300 手大约要几分钟；分析期间可以退出。
- 只判简单劫，不判位置超级劫（与桌面版口径一致，为的是不误杀引擎给出的合法手）。
- 不支持让子棋（SGF 里的 `AB`/`AW` 摆子能读进来，但界面没有设置让子的入口）。
- SGF 读取只取主线，遇到分支图会停在分支点。**如果主线在变化图之后还继续，后面那些手仍会被漏掉**（实测：`(;…;B[cc];W[dd](;B[ee])(;B[ff]);B[gg])` 主线 3 手只解析 2 手），
  但从 v44 起界面会**明说**：「（主线在变化图之后还有手，只读入了前 N 手）」；只含变化图、一手没丢的谱则提示「（本谱含变化图，分支未读入）」。多局棋谱只读第 1 局，同样会提示。
- 保存棋谱时只写**盘面信息**（手顺、摆子、让子、贴目、结果），**不写回**题谱里的 `C`（说明文字）、`GN`（局名）、`LB`/`TR`（标记）。盘面不会错，文字和标记会丢。
- 让子/摆子局：`loadsgf` 之后 `showboard` 会多一行 `Handicap bonus score: N`（引擎自己的让子约定报数）。
  App 的胜负判定用的是自己的 `scoreArea()`、**从不消费引擎的 `final_score`**，所以这行不影响 App 的判定。
  它对我们自己评分的实际影响**未量化**：审计员用「让引擎下到双方 pass、再用同一条手顺在自己盘上重放」的有效构造测过，
  无 `HA` 与 `HA[2]` 两种情形下我们 `scoreArea` 与引擎 `final_score` **都是黑+11.5（差 0）**；
  但「没下完的局面」上两者本来就不可比（实测差 18 目），所以**不能**据此说「没有偏差」，也不能反过来说「差 2 目」。
  （v46 的 README 曾把这一行外推成「约 2 目系统偏差」，那是**未实测的推断当成了事实**，已按审计员的复测改掉。）
- **让子谱的先后手**：App 按「本谱先走的一方」算（主线首手的颜色 → `PL[...]` → `HA[n]≥2` 时白先），
  不用「手数奇偶」（那假定黑先）。从 v52 起**快照与保存的棋谱都会写 `PL[...]`** ——
  实测真引擎**服从 `PL` 并覆盖它自己的推断**（`PL[B]AB[cc]` 按黑先、`PL[W]AB[cc][gg]AW[dd]` 按白先），
  而「只有 `AB` 摆子」「`HA` 与 `AW` 并存」这类形态引擎自己的推断未必与 App 一致，写上 `PL` 才对齐；
  之前「保存不写 `PL`」的信息损失也一并补掉了。
- **摆子里有「零气的死子」时，引擎整谱拒收**：`AB[aa]AW[ab][ba]` 这类局面（或者任何 `AE` 之后的形态）
  会让真引擎报 `Could not load sgf: ... initial board position contains invalid stones or zero-liberty stones`。
  v52 起 `AE` 会在写快照前被**吸收**（按顺序解算到空盘上再重新输出，实测 4 种写法都能读）；
  但真正的零气死子**没法交给引擎**（引擎不接受这种局面），此时复盘会退回「空盘重放」，
  界面会明确写「摆子引擎读不进去，下面的数字是在空盘上重放算的，只作参考」，不会假装修好了。
- **复盘的「最大失误 / 谁亏得最多」在低 visits 下会跳**：实测同一盘棋（`maxVisits=30`）两次跑，
  一次给「黑第 3 手 C3 亏 29%」、一次给「白第 4 手 G7 亏 28%」—— 两手损失接近时，搜索越小越不稳定。
  手顺、胜率曲线、单手的损失都有实数，但「关键在第 N 手」这类结论要当成**噪声范围内的提示**看；
  想稳就把复盘 visits 调高。
- SGF 的**打包坐标写法**（`AB[aabb]` 一个 token 里两组坐标）是历史扩展，App 仍支持；
  代价是 `AB[dead]` 这种「看着像单词、其实是合法坐标对」的值会被当 2 颗子（`AB[pass]`/`AB[resign]` 已单独排除）。
- 引擎同步带摆子的局面走 `loadsgf`（GTP 没有通用的「摆一个白子」命令）；万一写临时谱或 `loadsgf` 失败，会**退回**只重放手顺的旧路径 —— 那时引擎看不到摆子（不会崩，但分析会偏）。
- SGF 里 `AB[tt]`（历史虚手写法）在 ≤19 路被当 0 子忽略；`AB[pass]`/`AB[resign]` 同样当 0 子（v44 起，之前会被按「打包坐标」切成 `pa`+`ss` 静默落 2 子）。
- 棋谱目录里放满 9999 局后，再保存会**报错**而不是覆盖掉最后一局（v40 起；之前会静默覆盖）。
- 引擎日志写在 App 私有目录的 `engine.log`，出问题时可以导出。
- **`bestPv`（每手的主变化）是一条「死数据链」**：引擎算了、`GameReview.bestPv` 存了、`MainActivity:1118`
  也把它塞进 Intent 的 `EXTRA_BESTPV`，`ReviewActivity` 还把它接进 3 个写点（`:122/:173/:382` —— 第 37 轮审计直读行号
  纠正过我原来写的 `:71`，那是字段声明 `private String[] bestPv;` 不算写点；`:162` 那行是
  `getStringArrayExtra(EXTRA_BESTPV)`、不含 `bestPv`）—— 但**没有任何界面读取点**（`ReviewActivity.bestPv` 读 0 次、
  `onEngineMoveDone` 的 `pv` 形参读 0 次、`BoardView` 也没有 PV 接口）⇒ **复盘屏目前不显示主变化**。
  第 34 轮审计查出来、并更正了它自己第 32 轮「用户可见」的推断；我在容器里独立复核一致
  （`grep -rn bestPv src/` 共 **10 处**：MainActivity 3 + ReviewActivity 4 + GameReview 3；其中唯一一次「读」
  是 `MainActivity:1118` 把它塞进 Intent —— 也就是说 `GameReview.bestPv` 只被读这一次，界面层零读取）。
  功能表承诺的「引擎推荐点」是 `bestMove`（有显示），
  PV 属于多算了一步。**按现状不算缺陷**；要不要把 PV 显示出来、或把这条链删掉，是产品选择（记在这里，未动 `src/`）。
- **复盘屏的结果数组是跨线程共享的**：`wrToMove`/`bestMove`/`loss` 由工作线程写、界面线程读，
  字段上没有 `volatile`（可见性靠 `Handler.post` 的入队顺序）。这是 J1 合并前后**同一个**模式，
  第 18 轮没有动它（要安全地改，得连数据快照方式一起改）。
- `ReviewActivity.done[]` 是**死状态**：全文件只写不读 —— 判断「这手算没算」用的是
  `Double.isNaN(wrToMove[i])`。第 18 轮审计指出，保留原样并记在这里。
- 引擎关闭现在是**异步**的（`EngineManager.closeAsync`）：点返回时界面不再等引擎退出，引擎可能在
  Activity 走掉之后才真正结束。父进程被杀后引擎靠 stdin EOF 自己退，但**不是立刻**：空闲时 0.04 s
  （第 19 轮审计实测），在算长搜索时我实测 **7.3 s**、审计员实测 **18.85 s** ⇒ 它可能多活几秒，不会常驻。
  （v60 的记录里我写过「6 秒内」，那是把一次实测外推成了常量，已按审计员的复测改掉。）
- 异步关旧引擎的那段时间里，新旧两个引擎可能**同时占着模型内存**，窗口上限是 `close()` 里的
  `waitFor(3s)`。具体多大：第 21 轮审计在本机容器里对「19 路、20 万 visits」的搜索采样 `ps -eo rss`，
  b10c128 约 **117 MB**、b18c384nbt 约 **539 MB**（v62 这里我写过「各自约 1 GB」，那是把两个引擎、
  更大网络的合计当成了单个 —— 已按实测改）。安卓侧的数字没测。
- `GameReview.summarize` 的 `missingPoints` 数的是「index ≥ 1 没算到的点**个数**」（v63 起）：数个数而不是
  数前缀，因为解析失败可能落在中间，按「算到第几手」说会说错（第 21 轮审计的 GAP 用例）；v61/v62 数的是
  「算到几个点」、把个数当前缀用了，已修。起点（index 0）单独处理：它没算到时不说「从头领先到尾」、
  也不说「全程没有明显失误」（第 22 轮提示）⇒ `blackWr = {NaN, 0.5}` 这类输入 v60 给结论、v61/v62 走兜底、
  v63 起又给结论，但措辞有保留（v65 起是「算到的部分没有明显失误，…」）。
  **这里必须用原始 `wrToMove` 判**（`GameReview.startComputed`）：`toBlackWinrates` 会把 index 0 的 NaN
  填成 **0.5**，界面路径上 `blackWinrates()[0]` 永远是 0.5 ⇒ v64 那版从它反推等于永远「知道」，
  这条修复对用户不可见（第 23 轮审计端到端实测，已修）。
  `src/` 里只有测试用七/八参版本，界面走 `verdict()`（能拿到真实缺口数）。
- 复盘用的临时棋谱放在 App 私有缓存目录，正常路径都会删（`finally`）；进程被杀会**残留**，
  会一直攒到系统清缓存。名字带调用时刻（`autogo-review-<nanoTime>.sgf`）以免两个复盘任务互相覆盖。

---

## 九、独立审计与修复记录

历史审计流水（第 1～109 轮、v1～v189 逐版记录）已移到 [`docs/AUDIT-LOG.md`](docs/AUDIT-LOG.md)，内容原样保留。

---

## 许可证 / License

本项目采用 **GNU Affero General Public License v3.0（AGPL-3.0）** 许可，全文见 [`LICENSE`](LICENSE)。
This project is licensed under the **AGPL-3.0** — see [`LICENSE`](LICENSE).
