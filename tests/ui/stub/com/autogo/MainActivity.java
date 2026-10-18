package com.autogo;

/**
 * 接线桩：只为了让 {@link MenuActivity} 里的 Intent 目标（{@code MainActivity.class}）能解析。
 *
 * 围棋对局页 1800+ 行，拖着一堆只在真机上存在的 API，编不进 JVM 桩测；本桩不参与任何断言，
 * MenuProbe 断的是「点「围棋」跳到哪个类名」。真 MainActivity 的字节码由反向构建那一套保证没动。
 */
public class MainActivity {
}
