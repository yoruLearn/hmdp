package com.hmdp.Lock;

public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
