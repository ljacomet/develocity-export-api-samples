package com.develocity.export

import java.time.Duration

class AverageDuration {
    long count = 0
    Duration sumDur = Duration.ZERO

    AverageDuration add(Duration duration) {
        count++
        sumDur = sumDur + duration
        this
    }

    Duration getAvg() {
        sumDur.dividedBy(count)
    }

    @Override
    String toString() {
        return "${count}x avg: $avg"
    }
}
