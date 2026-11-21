package com.astraisland.events

import java.util.concurrent.TimeUnit

/** 三代系统各用自己的接入件；系统版本取 ColorOS 官方公布的版本属性。 */
object FluidCloudVariants {
    data class Variant(val major: Int, val packageName: String, val name: String)

    val ALL = listOf(
        Variant(15, "com.astraflow.fluidcloud.c15", "流体云事件接入 · C15"),
        Variant(16, "com.astraflow.fluidcloud.c16", "流体云事件接入 · C16"),
        Variant(17, "com.astraflow.fluidcloud.c17", "流体云事件接入 · C17"),
    )

    const val RELEASE_PROPERTY = "ro.build.version.oplusrom"
    private var deviceVariant: Variant? = null

    fun forMajor(major: Int): Variant? = ALL.singleOrNull { it.major == major }

    fun forRelease(release: String): Variant? = release.trim().removePrefix("V")
        .takeWhile { it in '0'..'9' }.toIntOrNull()?.let(::forMajor)

    /** 基础版本只取大版本号；只保存成功结果，读取失败后下次查询重新读取。 */
    @Synchronized
    fun current(): Variant? {
        deviceVariant?.let { return it }
        return runCatching {
            val process = ProcessBuilder("/system/bin/getprop", RELEASE_PROPERTY).start()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                process.inputStream.bufferedReader().use { forRelease(it.readLine().orEmpty()) }
            } else null
        }.getOrNull().also { deviceVariant = it }
    }
}
