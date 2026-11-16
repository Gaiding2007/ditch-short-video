package com.gaiding.ditchshortvideo.notify

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.gaiding.ditchshortvideo.R
import com.gaiding.ditchshortvideo.data.Prefs
import org.json.JSONObject

/**
 * Xiaomi's focus notification protocol is not officially available to
 * sideloaded APKs, so this is diagnostics-only. The normal notification still
 * works, and Android's promoted ongoing path is used independently.
 */
object XiaomiIsland {
    private const val PARAM_KEY = "miui.focus.param"
    private const val PICS_KEY = "miui.focus.pics"
    private const val PIC_KEY = "miui.focus.pic_icon"

    fun osName(): String? = systemProperty("ro.mi.os.version.name")

    fun islandFeature(): String? = systemProperty("persist.sys.feature.island")

    fun focusProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            "notification_focus_protocol",
            0
        )
    }.getOrDefault(0)

    fun canShowFocus(context: Context): Boolean = runCatching {
        val result: Bundle? = context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus",
            null,
            Bundle().apply { putString("package", context.packageName) }
        )
        result?.getBoolean("canShowFocus", false) ?: false
    }.getOrDefault(false)

    fun attachExperimental(
        context: Context,
        notification: Notification,
        title: String,
        body: String
    ) {
        if (!Prefs(context).focusParamExperimental) return
        if (!runCatching { isXiaomiDevice() }.getOrDefault(false)) return

        runCatching {
            notification.extras.putBundle(
                PICS_KEY,
                Bundle().apply {
                    putParcelable(
                        PIC_KEY,
                        Icon.createWithResource(context, R.drawable.ic_notification)
                    )
                }
            )
            notification.extras.putString(PARAM_KEY, buildJson(title, body))
        }
    }

    fun describe(context: Context): String = buildString {
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        appendLine("HyperOS：${osName() ?: "未识别"}")
        appendLine("超级岛特性：${islandFeature() ?: "未声明"}")
        appendLine("焦点协议：${focusProtocol(context)}")
        appendLine("canShowFocus：${canShowFocus(context)}（侧载仍可能被忽略）")
    }.trim()

    private fun isXiaomiDevice(): Boolean {
        val mark = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return mark.contains("xiaomi") || mark.contains("redmi") || mark.contains("poco")
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getDeclaredMethod("get", String::class.java)
        get.invoke(null, key) as? String
    }.getOrNull()

    private fun buildJson(title: String, body: String): String {
        val safeTitle = title.take(40)
        val safeBody = body.take(120)
        val baseInfo = JSONObject()
            .put("type", 1)
            .put("title", safeTitle)
            .put("content", safeBody)

        val textInfo = JSONObject()
            .put("title", safeTitle)
            .put("content", safeBody)

        val picInfo = JSONObject()
            .put("type", 1)
            .put("pic", PIC_KEY)

        val imageText = JSONObject()
            .put("type", 1)
            .put("picInfo", picInfo)
            .put("textInfo", textInfo)

        val bigIsland = JSONObject()
            .put("imageTextInfoLeft", imageText)
            .put("textInfo", textInfo)

        val paramIsland = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", 30)
            .put("bigIslandArea", bigIsland)
            .put("smallIslandArea", JSONObject().put("picInfo", picInfo))

        return JSONObject()
            .put(
                "param_v2",
                JSONObject()
                    .put("protocol", 1)
                    .put("business", "reminder")
                    .put("updatable", false)
                    .put("islandFirstFloat", true)
                    .put("enableFloat", false)
                    .put("timeout", 30)
                    .put("filterWhenNoPermission", false)
                    .put("ticker", safeTitle)
                    .put("aodTitle", safeTitle)
                    .put("aodPic", PIC_KEY)
                    .put("param_island", paramIsland)
                    .put("baseInfo", baseInfo)
            )
            .toString()
    }
}
