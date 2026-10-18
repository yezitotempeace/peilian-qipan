package com.autogo;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 引擎的落地与启动：释放权重、生成 GTP 配置、拉起子进程。
 *
 * 两个 Android 特有的坑：
 * 1. APK 里的 assets 不能直接当文件用，首次启动要把权重拷到 filesDir（14MB，一次性）。
 * 2. 二进制必须以 lib*.so 的形式放进 jniLibs，并在 manifest 里开 extractNativeLibs，
 *    才会被系统释放到 nativeLibraryDir —— 只有那个目录允许 App 执行文件。
 */
public class EngineManager {

    /** jniLibs 里的可执行文件名，故意用 lib 前缀骗过打包器。 */
    public static final String EXE_NAME = "libkatago.so";

    /**
     * 关引擎，但不在当前线程上等。
     *
     * 实测：`GtpEngine.close()` 会等引擎把 `quit` 排完队 —— 当时有长搜索在飞时阻塞
     * **662 ms**（我自己量的）到 **3008 ms**（第 18 轮独立审计量的），取决于那一刻引擎在算什么。
     * 两个 Activity 原本都在主线程的 `onDestroy()` 里关引擎，于是「分析中途点返回」会把界面卡住
     * 这么久（不到 ANR 阈值，但手感很差）。父进程若被系统直接杀掉，引擎会因 stdin EOF 自己退出 ——
     * 但**不是常量**：空闲时几乎立刻（审计员实测 SIGKILL 后 0.04 s），在算长搜索时我实测 **7.3 s**、
     * 审计员实测 **18.85 s**。所以异步关的收益是「界面不等」，代价是引擎可能多活几秒；
     * 它自己会走（EOF），不会变成常驻的野进程。
     */
    public static void closeAsync(final GtpEngine e) {
        if (e == null) return;
        Thread t = new Thread(() -> {
            try {
                e.close();
            } catch (RuntimeException ignore) {
                // 关失败没什么可做的：进程已经在退出路上，父进程一死引擎也会自己走（EOF）
            }
        }, "autogo-engine-close");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 内置权重。三个都在 APK 的 assets 里，但**只有选中的那个会被释放到 filesDir**
     * （b18 有 93 MB，全部释放会让 App 占掉两百多 MB）。
     * 切换网络时会删掉上一个，所以任意时刻磁盘上最多只有一份。
     *
     * 实测速度（本机 aarch64，19 路 100 visits，含模型加载）：
     *   b6c96     3 手共 2.3 s
     *   b10c128   3 手共 5.7 s
     */
    public static final class ModelSpec {
        public final String alias;
        public final String asset;
        public final String label;
        public final String note;
        /** 该档的推荐 visits。网络越强单步越慢，越该调低，否则一手要等十几秒。 */
        public final int recommendedVisits;

        ModelSpec(String alias, String asset, String label, String note, int recommendedVisits) {
            this.alias = alias;
            this.asset = asset;
            this.label = label;
            this.note = note;
            this.recommendedVisits = recommendedVisits;
        }
    }

    // 每档的“建议 visits”是按本机实测速度给的（9 路 100 visits：b6c96 约 0.4 s/手、
    // b10c128 约 1.7 s/手、b18 约 11 s/手）。网络越强，越该把 visits 调低才跑得动。
    public static final ModelSpec[] MODELS = {
            new ModelSpec("fast", "model_b6c96.txt.gz",
                    "极速 b6c96", "5 MB，最快", 300),
            new ModelSpec("normal", "model_b10c128.txt.gz",
                    "标准 b10c128", "14 MB，平衡", 100),
            new ModelSpec("strong", "model_b18c384nbt.bin.gz",
                    "强力 b18c384nbt", "93 MB，最强，一手要十几秒", 40),
    };

    public static final String DEFAULT_MODEL = "normal";

    public static ModelSpec model(String alias) {
        for (ModelSpec m : MODELS) {
            if (m.alias.equals(alias)) return m;
        }
        for (ModelSpec m : MODELS) {
            if (m.alias.equals(DEFAULT_MODEL)) return m;
        }
        return MODELS[0];
    }

    private final Context ctx;

    public EngineManager(Context ctx) {
        this.ctx = ctx;
    }

    public File nativeDir() {
        return new File(ctx.getApplicationInfo().nativeLibraryDir);
    }

    public File exeFile() {
        return new File(nativeDir(), EXE_NAME);
    }

    public File filesDir() {
        return ctx.getFilesDir();
    }

    /** 选中的权重释放到 filesDir 后的路径。 */
    public File modelFile(String alias) {
        return new File(filesDir(), model(alias).asset);
    }

    public File configFile() {
        return new File(filesDir(), "gtp.cfg");
    }

    public File logFile() {
        return new File(filesDir(), "engine.log");
    }

    public File gamesDir() {
        File d = new File(filesDir(), "games");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 可执行文件是否存在且可执行。 */
    public boolean isEngineInstalled() {
        File exe = exeFile();
        return exe.isFile() && exe.canExecute();
    }

    /**
     * 把选中的权重从 assets 拷到 filesDir；已存在且大小对得上就跳过。
     * 拷完会删掉其它网络的释放文件 —— b18 有 93 MB，全留着会白占两百多 MB。
     */
    public File ensureModel(String alias) throws IOException {
        ModelSpec spec = model(alias);
        File dst = modelFile(alias);
        long assetSize = -1;
        try (InputStream probe = ctx.getAssets().open(spec.asset)) {
            assetSize = probe.available();
        } catch (IOException ignored) {
        }
        // 拿不到 asset 长度时（available() 对某些压缩项会返回 0），退化成
        // 「存在且非空就算好了」，否则每次启动都要白拷几十 MB。
        boolean sizeKnown = assetSize > 0;
        if (dst.isFile() && dst.length() > 0 && (!sizeKnown || dst.length() == assetSize)) {
            removeOtherModels(spec.asset);
            return dst;
        }

        File tmp = new File(filesDir(), spec.asset + ".part");
        try (InputStream in = ctx.getAssets().open(spec.asset);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(dst)) {
            // renameTo 失败就退化成直接覆盖
            try (InputStream in = new java.io.FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            tmp.delete();
        }
        removeOtherModels(spec.asset);
        return dst;
    }

    /** 同一时刻只保留一份释放出来的权重。 */
    private void removeOtherModels(String keepAsset) {
        for (ModelSpec m : MODELS) {
            if (m.asset.equals(keepAsset)) continue;
            File f = new File(filesDir(), m.asset);
            if (f.isFile()) f.delete();
        }
    }

    /**
     * 生成 GTP 配置。
     *
     * 注意 KataGo 的两条硬约束（违反会直接 abort，且 stderr 上不一定看得见）：
     * - logFile / logDir / logDirDated 三者只能出现一个
     * - 写了 allowResignation 就必须同时给 resignThreshold 和 resignConsecTurns
     *
     * 另外 Eigen(CPU) 后端每次 NN 前向只用一个线程，NN 服务线程数才是真正的并行度，
     * 必须与搜索线程数一致，否则搜索线程会全部堵在 NN 队列上。
     */
    public File writeConfig(int visits, int threads, boolean allowResign) throws IOException {
        File logs = new File(filesDir(), "logs");
        if (!logs.exists()) logs.mkdirs();
        int t = Math.max(1, threads);
        StringBuilder sb = new StringBuilder();
        sb.append("# 由陪练棋盘 App 生成\n");
        sb.append("logDir = ").append(logs.getAbsolutePath()).append('\n');
        sb.append("logAllGTPCommunication = false\n");
        sb.append("logSearchInfo = false\n");
        sb.append("logToStderr = false\n\n");
        sb.append("rules = chinese\n");
        sb.append("ponderingEnabled = false\n");
        sb.append("allowResignation = ").append(allowResign).append('\n');
        sb.append("resignThreshold = -0.95\n");
        sb.append("resignConsecTurns = 4\n");
        sb.append("reportAnalysisWinratesAs = SIDETOMOVE\n\n");
        sb.append("numSearchThreads = ").append(t).append('\n');
        sb.append("numNNServerThreadsPerModel = ").append(t).append('\n');
        sb.append("nnMaxBatchSize = 1\n");
        sb.append("nnCacheSizePowerOfTwo = 20\n\n");
        sb.append("maxVisits = ").append(Math.max(1, visits)).append('\n');

        File cfg = configFile();
        try (OutputStream out = new FileOutputStream(cfg)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return cfg;
    }

    /** engine.log 的上限，超过就清掉重来。 */
    private static final long MAX_ENGINE_LOG_BYTES = 1 << 20;

    /**
     * 清掉上一次运行留下的日志。
     *
     * 实测：KataGo 配了 `logDir` 之后，**每启动一次就新建一个带时间戳的 .log 文件**
     * （内容只有启动横幅，约 8 KB），而且从不删除。App 每次启动、每次改设置、
     * 每次搜索超时重启都会触发一次 —— 长期下来就是只增不减的一堆垃圾文件。
     *
     * 这里在每次启动前清空 logs/，并把我们自己抓 stderr 的 engine.log 限制在 1 MB 以内。
     * 日志本身没有保留价值（KataGo 的通信/搜索日志都已关掉），所以直接清掉最省事。
     */
    private void cleanOldLogs() {
        File logs = new File(filesDir(), "logs");
        File[] old = logs.listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.isFile() && !f.delete()) {
                    // 删不掉不影响下棋，忽略
                }
            }
        }
        File lf = logFile();
        if (lf.exists() && lf.length() > MAX_ENGINE_LOG_BYTES) {
            if (!lf.delete()) {
                // 同上
            }
        }
    }

    /** 启动引擎。调用方负责在后台线程里做这件事（模型加载要几秒）。 */
    public GtpEngine start(int visits, int threads, boolean allowResign, String modelAlias)
            throws IOException {
        if (!isEngineInstalled()) {
            throw new IOException("引擎二进制缺失：" + exeFile().getAbsolutePath()
                    + "（APK 未包含 arm64-v8a 的 " + EXE_NAME + "？）");
        }
        File model = ensureModel(modelAlias);
        File cfg = writeConfig(visits, threads, allowResign);
        cleanOldLogs();

        List<String> args = Arrays.asList(
                "gtp",
                "-model", model.getAbsolutePath(),
                "-config", cfg.getAbsolutePath(),
                "-override-config", "maxVisits=" + Math.max(1, visits));
        GtpEngine engine;
        try {
            engine = new GtpEngine(exeFile().getAbsolutePath(), args, logFile());
        } catch (IOException e) {
            // 最常见的原因就是 SELinux 不让 App 执行 nativeLibraryDir 里的文件
            throw new IOException("无法启动引擎进程：" + e.getMessage()
                    + "\n\n可执行文件：" + exeFile().getAbsolutePath()
                    + "\n可执行位=" + exeFile().canExecute()
                    + "　大小=" + exeFile().length()
                    + "\n\n如果提示 Permission denied，说明系统不允许从这个目录执行文件；"
                    + "请把这段信息反馈出来。", e);
        }
        try {
            // 握手：确认进程真的活着并且能应答
            engine.send("name", 120_000);
        } catch (IOException e) {
            engine.close();
            throw new IOException("引擎进程启动后没有正常应答：" + e.getMessage()
                    + "\n（模型加载失败或权重文件损坏时会这样）", e);
        }
        return engine;
    }

    /**
     * 默认搜索线程数。Eigen 后端每次 NN 前向只用一个线程，真正的并行度来自
     * NN 服务线程数（配置里与搜索线程数保持一致），所以尽量用满核心能直接换速度；
     * 留一个核心给 UI 和系统，免得界面卡顿。
     */
    public static int defaultThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(2, cores - 1);
    }

    /** 指定网络的权重是否已经释放到本地。 */
    public boolean isModelReady(String alias) {
        File m = modelFile(alias);
        return m.isFile() && m.length() > 0;
    }

    /** 当前释放到本地的权重占用字节数（同一时刻最多一份）。 */
    public long extractedModelBytes() {
        long total = 0;
        for (ModelSpec m : MODELS) {
            File f = new File(filesDir(), m.asset);
            if (f.isFile()) total += f.length();
        }
        return total;
    }

    public List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        out.add("可执行文件: " + exeFile().getAbsolutePath());
        out.add("  存在=" + exeFile().isFile() + " 可执行=" + exeFile().canExecute()
                + " 大小=" + exeFile().length());
        out.add("权重: 已释放 " + (extractedModelBytes() / 1048576) + " MB");
        for (ModelSpec m : MODELS) {
            File f = new File(filesDir(), m.asset);
            out.add("  " + m.label + " (" + m.alias + "): "
                    + (f.isFile() ? (f.length() / 1048576) + " MB" : "未释放"));
        }
        out.add("配置: " + configFile().getAbsolutePath());
        out.add("CPU 核数: " + Runtime.getRuntime().availableProcessors());
        out.add(probe());
        return out;
    }

    /**
     * 真跑一次二进制，确认「这个目录到底能不能执行」。
     * canExecute() 只反映文件模式位，SELinux 拦截时它仍然返回 true，所以必须实跑。
     */
    public String probe() {
        try {
            ProcessBuilder pb = new ProcessBuilder(exeFile().getAbsolutePath(), "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            boolean finished = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            String head = sb.toString().trim();
            if (head.length() > 200) head = head.substring(0, 200) + "…";
            return "自检: " + (finished ? ("exit=" + p.exitValue()) : "超时") + " " + head;
        } catch (Exception e) {
            return "自检失败（很可能无法从这个目录执行文件）: " + e;
        }
    }
}
