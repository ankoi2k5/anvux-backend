package com.anvux.task;

import java.time.Duration;

public enum TaskRecurrence {
    NONE(null),
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7));

    private final Duration period;

    TaskRecurrence(Duration period) {
        this.period = period;
    }

    /** Khoảng cách giữa hai lần lặp, null nếu không lặp. */
    public Duration period() {
        return period;
    }
}