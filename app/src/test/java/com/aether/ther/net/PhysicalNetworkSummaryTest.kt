package com.aether.ther.net

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalNetworkSummaryTest {
    @Test
    fun reportsValidatedUnmeteredWifi() {
        assertEquals(
            "Wi-Fi · 已验证 · 非计费网络",
            physicalNetworkSummary(
                wifi = true,
                cellular = false,
                ethernet = false,
                internet = true,
                validated = true,
                captivePortal = false,
                metered = false,
            ),
        )
    }

    @Test
    fun captivePortalTakesPrecedenceOverUnvalidatedInternet() {
        assertEquals(
            "蜂窝网络 · 需要登录 · 计费网络",
            physicalNetworkSummary(
                wifi = false,
                cellular = true,
                ethernet = false,
                internet = true,
                validated = false,
                captivePortal = true,
                metered = true,
            ),
        )
    }

    @Test
    fun doesNotCallUnvalidatedNetworkConnected() {
        assertEquals(
            "以太网 · 有互联网能力，尚未验证 · 非计费网络",
            physicalNetworkSummary(
                wifi = false,
                cellular = false,
                ethernet = true,
                internet = true,
                validated = false,
                captivePortal = false,
                metered = false,
            ),
        )
    }
}
