# Rapfi 构建记录（2026-10-15，供 README 用）

上游：`https://github.com/dhbloo/rapfi`（`git clone` 到 /tmp/gomoku-strong/rapfi，版本见 git log）
需要的最小配置：CMake ≥ 3.18.2、C++17。

## 两处上游源码补丁（都已落盘）
1. `Rapfi/external/zip/src/zip.c`：`S_IWRITE` 是 Windows 叫法，bionic 只有 `S_IWUSR`。
   在 `#ifdef __MINGW32__ ... #endif` **之外**加：
   ```c
   #ifndef S_IWRITE
   #include <sys/stat.h>
   #define S_IWRITE S_IWUSR
   #endif
   ```
   （第一次把补丁放进 MINGW32 块里 ⇒ 对 Android 无效，编译仍报 1383/1719 行未声明。）
2. `Rapfi/CMakeLists.txt`（约 606 行的 pthread/atomic 链接块）：bionic 里 pthread/atomic 在 libc 内，
   没有独立 `-lpthread`/`-latomic` 可链。加一条分支：
   ```cmake
   elseif(RAPFI_ANDROID_BUILD)
       target_link_libraries(rapfi PRIVATE -pthread)
   ```
   并在配置时传 `-DRAPFI_ANDROID_BUILD=ON`。

## 宿主构建（aarch64 Linux，跑得起来，用于 JVM 测试与协议验证）
```
cmake -S rapfi -B native-build -DCMAKE_C_COMPILER=/usr/lib/llvm-18/bin/clang -DCMAKE_CXX_COMPILER=/usr/lib/llvm-18/bin/clang++ -DUSE_NEON=ON
cmake --build native-build -j8
```
产物 `native-build/pbrain-rapfi`（2.3 MB），`START 15` / `BOARD ... DONE` 协议实测可用。

## Android 交叉编译（NDK r27 sysroot + 系统 clang-18，与 KataGo 同一套配方）
```
cmake -S rapfi -B android-build -DCMAKE_TOOLCHAIN_FILE=/tmp/gomoku-strong/android-toolchain.cmake -DUSE_NEON=ON -DUSE_NEON_DOTPROD=ON -DRAPFI_ANDROID_BUILD=ON
cmake --build android-build -j8
```
产物 `android-build/pbrain-rapfi`（3.68 MB），`readelf -d` 只需 libm/libdl/libc（与 libkatago.so 同形）。
已拷成 `/tmp/gomoku-strong/out/librapfi-arm64-android` 与 `/tmp/gomoku-strong/out/pbrain-rapfi-linux-aarch64`。

## 协议要点（宿主实测）
- `START 15` ⇒ `OK`；`INFO TIMEOUT_TURN <ms>` 设每手时限；`BOARD` 后逐行 `<col字母>,<row>,<1|2>`（1=自己 2=对方）再 `DONE` ⇒ 引擎回一手 `<col字母>,<row>`；首行棋盘行决定引擎执什么色。
- 坐标：引擎格式 x=列（A 起），y=行（1 起、从**下**往上），`y = size-1-row`（我的 row 0 在上）。
