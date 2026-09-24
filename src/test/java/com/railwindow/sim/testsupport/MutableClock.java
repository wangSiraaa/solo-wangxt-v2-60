package com.railwindow.sim.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可由测试驱动的时钟：固定在某一时刻，或显式推进。演练时间相关场景（跨午夜、迟到消息）
 * 通过它注入，替代系统时钟。
 */
public class MutableClock extends Clock {

    private Instant current;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    public MutableClock(Instant start, ZoneId zone) {
        this.current = start;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    public void advanceSeconds(long seconds) {
        this.current = current.plusSeconds(seconds);
    }

    public void advanceMinutes(long minutes) {
        this.current = current.plusSeconds(minutes * 60);
    }

    public void advanceHours(long hours) {
        this.current = current.plusSeconds(hours * 3600);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
