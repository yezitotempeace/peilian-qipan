/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.view;
/** 假动画器：测试里不真的跑动画，只把链式调用接住。 */
public class ViewPropertyAnimator {
    public ViewPropertyAnimator alpha(float a) { return this; }
    public ViewPropertyAnimator scaleX(float s) { return this; }
    public ViewPropertyAnimator scaleY(float s) { return this; }
    public ViewPropertyAnimator translationX(float t) { return this; }
    public ViewPropertyAnimator translationY(float t) { return this; }
    public ViewPropertyAnimator setDuration(long d) { return this; }
    public ViewPropertyAnimator setStartDelay(long d) { return this; }
    public void start() {}
}
