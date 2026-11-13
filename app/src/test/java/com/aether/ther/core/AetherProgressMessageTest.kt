package com.aether.ther.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AetherProgressMessageTest {
    @Test
    fun mapsCoreMilestonesWithoutCallingCandidateProbeConnected() {
        assertEquals(
            "正在扫描 WARP 网关…",
            aetherProgressMessage("[*] hunting for a working MASQUE gateway"),
        )
        assertEquals(
            "已选定候选网关，正在建立 MASQUE 传输…",
            aetherProgressMessage("[+] selected MASQUE gateway 162.159.198.1:443"),
        )
        assertEquals(
            "MASQUE 传输已建立，正在验证端到端数据…",
            aetherProgressMessage("[+] [masque] MASQUE transport: HTTP/3 (QUIC)"),
        )
        assertEquals(
            "隧道数据面已验证，正在准备本地代理…",
            aetherProgressMessage("[+] masque tunnel validated (end-to-end data confirmed)"),
        )
        assertEquals(
            "本地 SOCKS5 代理已就绪，正在完成连接验证…",
            aetherProgressMessage("[+] socks5 server listening on 127.0.0.1:1819"),
        )
        assertNull(aetherProgressMessage("[+] candidate ok 162.159.198.1:443"))
    }

    @Test
    fun ignoresUnrecognizedAndEmptyLogLines() {
        assertNull(aetherProgressMessage(""))
        assertNull(aetherProgressMessage("ordinary diagnostic line"))
    }
}
