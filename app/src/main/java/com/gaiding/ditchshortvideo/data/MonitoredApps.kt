package com.gaiding.ditchshortvideo.data

import android.graphics.Color

/**
 * The only apps v0.0.1 knows how to recognise. Adding an app means adding a
 * detector rule; it is deliberately not a free-form package picker.
 */
data class MonitoredApp(
    val packageName: String,
    val label: String,
    val note: String,
    val letter: String,
    val fallbackColor: Int
)

object MonitoredApps {
    val all: List<MonitoredApp> = listOf(
        MonitoredApp(
            packageName = "com.ss.android.ugc.aweme",
            label = "抖音",
            note = "推荐 / 发现页",
            letter = "抖",
            fallbackColor = Color.rgb(28, 28, 32)
        ),
        MonitoredApp(
            packageName = "com.smile.gifmaker",
            label = "快手",
            note = "发现 / 精选页",
            letter = "快",
            fallbackColor = Color.rgb(255, 106, 0)
        ),
        MonitoredApp(
            packageName = "com.tencent.mm",
            label = "微信视频号",
            note = "只统计视频号",
            letter = "视",
            fallbackColor = Color.rgb(7, 193, 96)
        ),
        MonitoredApp(
            packageName = "com.xingin.xhs",
            label = "小红书",
            note = "视频笔记",
            letter = "红",
            fallbackColor = Color.rgb(255, 36, 66)
        ),
        MonitoredApp(
            packageName = "tv.danmaku.bili",
            label = "哔哩哔哩",
            note = "故事 / 竖屏视频",
            letter = "B",
            fallbackColor = Color.rgb(251, 114, 153)
        )
    )

    private val byPackage = all.associateBy { it.packageName }

    fun find(packageName: String?): MonitoredApp? = byPackage[packageName]

    fun labelFor(packageName: String?): String? = find(packageName)?.label

    fun isKnown(packageName: String?): Boolean = byPackage.containsKey(packageName)

    fun defaultPackages(): Set<String> = all.mapTo(LinkedHashSet()) { it.packageName }
}
