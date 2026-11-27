package com.gasser.fajr

import org.junit.Assert.assertEquals
import org.junit.Test

class RingDeadlineTest {
    @Test fun sessionEndsAtTwentyMinutes() {
        val start=10_000L
        val deadline=RingDeadline.start(start)
        assertEquals(1_200_000L,deadline-start)
        assertEquals(1L,RingDeadline.remaining(deadline,deadline-1))
        assertEquals(0L,RingDeadline.remaining(deadline,deadline))
        assertEquals(0L,RingDeadline.remaining(deadline,deadline+30_000))
    }
    @Test fun remainingTimeDoesNotRestartForAQueuedAlarm() {
        val deadline=RingDeadline.start(100_000)
        assertEquals(600_000L,RingDeadline.remaining(deadline,700_000))
    }
}
