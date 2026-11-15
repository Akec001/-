package com.aether.ther.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aether.ther.data.LogBus

/**
 * 只观察物理接入网络，不判断哪个网络当前承载 VPN，也不修改路由或重连。
 *
 * 不使用 registerDefaultNetworkCallback：对 VPN 应用而言，应用默认网络可能是
 * VPN 自身的虚拟网络。这里仅订阅 Wi-Fi/蜂窝/以太网，避免把 TRANSPORT_VPN
 * 错报成物理接入网络。
 */
internal class PhysicalNetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val states = HashMap<Network, String>()
    @Volatile private var registered = false

    private val request = NetworkRequest.Builder()
        .clearCapabilities()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(states) {
                states[network] = "等待网络能力"
            }
            LogBus.i("Net", "发现物理网络，等待能力信息")
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val summary = physicalNetworkSummary(
                wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
            val changed = synchronized(states) {
                if (states[network] == summary) {
                    false
                } else {
                    states[network] = summary
                    true
                }
            }
            if (changed) LogBus.i("Net", "物理网络状态：$summary")
        }

        override fun onLost(network: Network) {
            val previous = synchronized(states) { states.remove(network) }
            if (previous != null) {
                val kind = previous.substringBefore(" · ").takeIf { it != "等待网络能力" } ?: "未知类型"
                LogBus.w("Net", "物理网络断开：$kind")
            }
        }
    }

    fun start() {
        if (registered) return
        val cm = manager
        if (cm == null) {
            LogBus.w("Net", "物理网络监测未启动：ConnectivityManager 不可用")
            return
        }
        try {
            cm.registerNetworkCallback(request, callback)
            registered = true
            LogBus.i("Net", "物理网络监测已启动（Wi-Fi/蜂窝/以太网）")
        } catch (error: RuntimeException) {
            LogBus.w("Net", "物理网络监测启动失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        val cm = manager
        if (cm != null) {
            runCatching { cm.unregisterNetworkCallback(callback) }
                .onFailure { LogBus.w("Net", "物理网络监测停止异常：${it.message}") }
        }
        synchronized(states) { states.clear() }
        LogBus.d("Net", "物理网络监测已停止")
    }
}

/** 纯文本映射，便于单元测试；不包含 IP、SSID 或 Network handle 等敏感标识。 */
internal fun physicalNetworkSummary(
    wifi: Boolean,
    cellular: Boolean,
    ethernet: Boolean,
    internet: Boolean,
    validated: Boolean,
    captivePortal: Boolean,
    metered: Boolean,
): String {
    val transports = buildList {
        if (wifi) add("Wi-Fi")
        if (cellular) add("蜂窝网络")
        if (ethernet) add("以太网")
    }.ifEmpty { listOf("其他物理网络") }.joinToString(" + ")

    val reachability = when {
        captivePortal -> "需要登录"
        validated -> "已验证"
        internet -> "有互联网能力，尚未验证"
        else -> "无互联网能力"
    }
    val cost = if (metered) "计费网络" else "非计费网络"
    return "$transports · $reachability · $cost"
}
