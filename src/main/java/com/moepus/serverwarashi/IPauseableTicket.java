package com.moepus.serverwarashi;

public interface IPauseableTicket {
    int PAUSE_REASON_MANUAL = 1;
    int PAUSE_REASON_AUTO = 2;

    int serverWarashi$getPauseMask();
    void serverWarashi$setPauseMask(int mask);
    boolean serverWarashi$isPaused();
    boolean serverWarashi$needUpdate();
    void serverWarashi$clearDirty();
    int serverWarashi$getLevel();
    Object serverWarashi$getKey();
}
