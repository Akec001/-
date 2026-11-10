package com.aether.ther

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficRateCalculatorTest {
    @Test
    fun firstSampleOnlySetsBaselineAndSecondCalculatesBytesPerSecond() {
        val calculator = TrafficRateCalculator()
        assertNull(calculator.sample(100L, 200L, 1_000L))
        assertEquals(
            TrafficRate(downloadBytesPerSecond = 1_024L, uploadBytesPerSecond = 512L),
            calculator.sample(1_124L, 712L, 2_000L),
        )
    }

    @Test
    fun counterRollbackResetsBaselineInsteadOfShowingNegativeSpeed() {
        val calculator = TrafficRateCalculator()
        assertNull(calculator.sample(1_000L, 2_000L, 1_000L))
        assertNull(calculator.sample(10L, 20L, 2_000L))
        assertEquals(
            TrafficRate(downloadBytesPerSecond = 1_000L, uploadBytesPerSecond = 1_000L),
            calculator.sample(1_010L, 1_020L, 3_000L),
        )
    }

    @Test
    fun invalidCountersAndNonIncreasingTimeNeverProduceRates() {
        val calculator = TrafficRateCalculator()
        assertNull(calculator.sample(10L, 10L, 2_000L))
        assertNull(calculator.sample(-1L, 20L, 3_000L))
        assertNull(calculator.sample(20L, 20L, 3_000L))
        assertNull(calculator.sample(30L, 50L, 3_000L))
        assertEquals(
            TrafficRate(downloadBytesPerSecond = 10L, uploadBytesPerSecond = 30L),
            calculator.sample(40L, 80L, 4_000L),
        )
    }
}
