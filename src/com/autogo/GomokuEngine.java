package com.autogo;

/**
 * 五子棋对手 / 评估器的统一接口。
 *
 * 现在只有内置实现 {@link LocalGomokuAI}；将来如果集成外部强引擎（另编译的进程/so），
 * 也实现这个接口挂上来，界面与复盘侧一行都不用改。
 */
public interface GomokuEngine {

    /** 引擎名（界面显示用）。 */
    String name();

    /** 设难度：0 = 快，1 = 普通（默认），2 = 强。越界的值按普通处理。 */
    void setLevel(int level);

    int level();

    /**
     * 该走哪一手。返回棋盘内部点号；无处可下（棋局已结束或棋盘满）返回 -1。
     * 约定：只读棋局，不改变传入的 GomokuBoard。
     */
    int genmove(GomokuBoard board, int color);

    /**
     * 以 color 为视角的盘面评估分（正数 = 对 color 有利，负数 = 不利，0 = 均势）。
     * 两边视角互为相反数。给复盘算曲线的，不是胜率本身。
     */
    double evaluate(GomokuBoard board, int color);

    /**
     * 引擎给的候选着法（越靠前越好，最多 n 个；只读棋局）。给「学习」层用：
     * 学习只在这个候选集合里挑，挑不到就照 {@link #genmove} 那一手走。
     * 不给细排名的实现可以照原样返回空数组（那就等于学习不参与）。
     */
    default int[] topMoves(GomokuBoard board, int color, int n) {
        return new int[0];
    }
}
