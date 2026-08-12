package com.achyut.adglasses.localagent.shizuku;

interface ILocalAgentShizukuInput {
    void destroy() = 16777114;

    boolean pressEnter() = 1;
    boolean pressBack() = 2;
    boolean pressHome() = 3;
    boolean swipe(int startX, int startY, int endX, int endY, int durationMs) = 4;
}
