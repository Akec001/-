package com.aether.ther

import kotlin.math.roundToLong

/** 以设备用户视角定义的实时速率。 */
internal data class TrafficRate(
    val downloadBytesPerSecond: Long,
    val uploadBytesPerSecond: Long,
)

/**
 * 把核心累计字节计数转换为速率。
 * 首次采样、计数回退、时间戳异常或无效计数只重建基线，不显示负值或推算值。
 */
internal class TrafficRateCalculator {
    private var lastSampleAtMs = -1L
    private var lastDownloadBytes = -1L
    private var lastUploadBytes = -1L

    @Synchronized
    fun sample(downloadTotalBytes: Long, uploadTotalBytes: Long, nowMs: Long): TrafficRate? {
        if (downloadTotalBytes < 0L || uploadTotalBytes < 0L || nowMs < 0L) {
            resetLocked()
            return null
        }
        val elapsedMs = nowMs - lastSampleAtMs
        if (lastSampleAtMs < 0L || elapsedMs <= 0L ||
            downloadTotalBytes < lastDownloadBytes || uploadTotalBytes < lastUploadBytes
        ) {
            setBaseline(downloadTotalBytes, uploadTotalBytes, nowMs)
            return null
        }
        val rate = TrafficRate(
            downloadBytesPerSecond = (
                (downloadTotalBytes - lastDownloadBytes).toDouble() * 1_000.0 / elapsedMs
            ).roundToLong(),
            uploadBytesPerSecond = (
                (uploadTotalBytes - lastUploadBytes).toDouble() * 1_000.0 / elapsedMs
            ).roundToLong(),
        )
        setBaseline(downloadTotalBytes, uploadTotalBytes, nowMs)
        return rate
    }

    @Synchronized
    fun reset() = resetLocked()

    private fun setBaseline(downloadBytes: Long, uploadBytes: Long, atMs: Long) {
        lastDownloadBytes = downloadBytes
        lastUploadBytes = uploadBytes
        lastSampleAtMs = atMs
    }

    private fun resetLocked() {
        lastSampleAtMs = -1L
        lastDownloadBytes = -1L
        lastUploadBytes = -1L
    }
}
