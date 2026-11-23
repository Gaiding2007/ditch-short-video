package com.gaiding.ditchshortvideo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.gaiding.ditchshortvideo.data.DayUtils
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.notify.ReminderNotifier
import com.gaiding.ditchshortvideo.system.SystemStatus
import com.gaiding.ditchshortvideo.ui.Ui
import kotlin.math.max

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageStore
    private lateinit var limitValue: TextView
    private lateinit var reminderSwitch: Switch
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var preview: TextView
    private lateinit var permissionsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        usage = UsageStore(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshLimit()
        refreshPermissions()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.colorOf(this@SettingsActivity, R.color.background))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = Ui.column(this).apply {
            setPadding(Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 12),
                Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 36))
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        Ui.edgeToEdge(this, content)

        content.addView(buildHeader())
        content.addView(buildLimitCard(), topMargin(16))
        content.addView(buildReminderCard(), topMargin(14))
        content.addView(buildPermissionsCard(), topMargin(14))
        content.addView(buildRulesCard(), topMargin(14))
        content.addView(buildDataCard(), topMargin(14))
        content.addView(buildAboutCard(), topMargin(14))
        content.addView(
            Ui.text(this, "v${BuildConfig.VERSION_NAME} · 数据只在本机", 10f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL },
            topMargin(18)
        )
        return root
    }

    private fun buildHeader(): View {
        val row = Ui.row(this)
        val back = Ui.text(this, "←", 23f, Ui.colorOf(this, R.color.ink)).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 2),
                Ui.dp(this@SettingsActivity, 10), Ui.dp(this@SettingsActivity, 4))
            contentDescription = "返回"
            setOnClickListener { finish() }
        }
        row.addView(back)
        row.addView(
            Ui.text(this, "设置", 24f, Ui.colorOf(this, R.color.ink), bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun buildLimitCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "每日额度",
                "每天 0 点重置。五个 App 合并计算。"
            )
        )
        val row = Ui.row(this)
        limitValue = Ui.text(this, "", 30f, Ui.colorOf(this, R.color.accent_deep), bold = true)
        row.addView(
            limitValue,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val edit = Ui.ghostButton(this, "修改额度")
        edit.setOnClickListener { showLimitDialog() }
        row.addView(edit)
        card.addView(row, topMargin(10))
        card.addView(
            Ui.text(this, "到点只提醒一次，不会强制锁机或关闭 App。", 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(6)
        )
        return card
    }

    private fun buildReminderCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "超时提醒",
                "标题和正文都可以自定义，支持 {used} 和 {limit} 占位符。"
            )
        )

        val switchRow = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@SettingsActivity, 12), 0, Ui.dp(this@SettingsActivity, 4))
        }
        val switchText = Ui.column(this)
        switchText.addView(
            Ui.text(this, "开启超时提醒", 14f, Ui.colorOf(this, R.color.ink), bold = true)
        )
        switchText.addView(
            Ui.text(this, "系统允许时尝试上小米超级岛，否则普通通知", 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(3)
        )
        switchRow.addView(
            switchText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        reminderSwitch = Switch(this).apply {
            isChecked = prefs.notificationsEnabled
            contentDescription = "开启超时提醒"
            styleSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                prefs.notificationsEnabled = checked
                if (!checked) ReminderNotifier.cancelAll(this@SettingsActivity)
                if (checked && !SystemStatus.notificationsAllowed(this@SettingsActivity)) {
                    Ui.toast(this@SettingsActivity, "还需要在系统设置里允许通知")
                }
            }
        }
        switchRow.addView(reminderSwitch)
        card.addView(switchRow)

        card.addView(Ui.divider(this), topMargin(8))

        titleInput = editText(singleLine = true).apply {
            filters = arrayOf(InputFilter.LengthFilter(40))
            setText(prefs.reminderTitle)
            hint = "标题，例如：今天的额度用完了"
        }
        card.addView(field("提醒标题", titleInput), topMargin(12))

        bodyInput = editText(singleLine = false).apply {
            filters = arrayOf(InputFilter.LengthFilter(140))
            setText(prefs.reminderBody)
            hint = "正文，可用 {used} 和 {limit}"
            minLines = 3
            maxLines = 4
        }
        card.addView(field("提醒正文", bodyInput), topMargin(10))

        preview = Ui.text(this, "", 11f, Ui.colorOf(this, R.color.ink_soft))
        preview.setLineSpacing(Ui.dp(this, 2).toFloat(), 1.25f)
        card.addView(preview, topMargin(8))

        val actions = Ui.row(this)
        val save = Ui.button(this, "保存文案", primary = true)
        save.setOnClickListener { saveReminderText(showToast = true) }
        actions.addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val test = Ui.button(this, "发送测试", primary = false)
        val testParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        testParams.leftMargin = Ui.dp(this, 8)
        test.setOnClickListener { sendTest() }
        actions.addView(test, testParams)
        card.addView(actions, topMargin(12))

        val reset = Ui.ghostButton(this, "恢复默认文案")
        reset.setOnClickListener {
            prefs.resetReminderText()
            titleInput.setText(prefs.reminderTitle)
            bodyInput.setText(prefs.reminderBody)
            refreshPreview()
            Ui.toast(this, "已恢复默认")
        }
        card.addView(reset, topMargin(4))
        refreshPreview()
        return card
    }

    private fun buildPermissionsCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "权限与后台",
                "系统升级或清理后台后，回来检查一下"
            )
        )
        permissionsContainer = Ui.column(this)
        card.addView(permissionsContainer, topMargin(6))
        refreshPermissions()
        return card
    }

    private fun buildRulesCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "计时规则",
                "只在你真的刷短视频时计时"
            )
        )
        val rules = listOf(
            "识别到短视频页面，连续 2 秒后才开始计时。",
            "离开页面、切 App、锁屏、熄屏或无障碍断开，会暂停；滑动造成的短暂页面抖动保留 3 秒。",
            "视频评论区等临时页面最多继续计 30 秒，超过后暂停。",
            "聊天、搜索、个人主页不计时；停在短视频页即计时，不判断视频暂停。"
        )
        rules.forEach { rule ->
            val row = Ui.row(this)
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Ui.colorOf(this@SettingsActivity, R.color.accent))
                }
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 5), Ui.dp(this@SettingsActivity, 5))
            }
            row.addView(dot)
            val text = Ui.text(this, rule, 12f, Ui.colorOf(this, R.color.ink_soft))
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            params.leftMargin = Ui.dp(this, 8)
            row.addView(text, params)
            card.addView(row, topMargin(8))
        }
        return card
    }

    private fun buildDataCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "数据",
                "每日记录只保存在这台手机，不会上传。"
            )
        )
        card.addView(aboutRow("每日曲线与计划", "查看每天时长，设置希望控制到的额度") {
            startActivity(Intent(this, DataActivity::class.java))
        }, topMargin(10))
        card.addView(Ui.divider(this), topMargin(8))
        val clear = Ui.button(this, "清空全部记录", primary = false).apply {
            setTextColor(Ui.colorOf(this@SettingsActivity, R.color.danger))
        }
        clear.setOnClickListener { confirmClear() }
        card.addView(clear, topMargin(12))
        return card
    }

    private fun buildAboutCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.sectionHeader(this, "关于", "扔掉短视频 · 把时间拿回来"))
        card.addView(aboutRow("诊断与页面规则", "查看识别状态，运行规则自检") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }, topMargin(10))
        card.addView(Ui.divider(this), topMargin(8))
        card.addView(aboutRow("项目主页", "GitHub · Gaiding2007/ditch-short-video") {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Gaiding2007/ditch-short-video")))
            }.onFailure { Ui.toast(this, "没有可用的浏览器") }
        }, topMargin(8))
        card.addView(
            Ui.text(this, "没有账号、没有云同步，也没有联网权限。", 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(12)
        )
        return card
    }

    private fun aboutRow(title: String, subtitle: String, action: () -> Unit): View {
        val row = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 6))
            isClickable = true
            setOnClickListener { action() }
        }
        val textBox = Ui.column(this)
        textBox.addView(Ui.text(this, title, 14f, Ui.colorOf(this, R.color.ink), bold = true))
        textBox.addView(
            Ui.text(this, subtitle, 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(3)
        )
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.text(this, "›", 20f, Ui.colorOf(this, R.color.ink_faint)))
        return row
    }

    private fun refreshLimit() {
        limitValue.text = "${prefs.dailyLimitMinutes} 分钟"
    }

    private fun refreshPermissions() {
        if (!::permissionsContainer.isInitialized) return
        permissionsContainer.removeAllViews()
        permissionRow(
            "页面识别",
            "无障碍服务，读取页面结构",
            SystemStatus.accessibilityEnabled(this)
        ) { SystemStatus.openAccessibilitySettings(this) }
        permissionRow(
            "通知权限",
            "超时提醒和测试提醒",
            SystemStatus.notificationsAllowed(this)
        ) { SystemStatus.openNotificationSettings(this) }
        permissionRow(
            "后台运行",
            "避免计时服务被系统清理",
            SystemStatus.batteryUnrestricted(this)
        ) { SystemStatus.openBatterySettings(this) }
    }

    private fun permissionRow(
        title: String,
        description: String,
        ok: Boolean,
        action: () -> Unit
    ) {
        val row = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@SettingsActivity, 9), 0, Ui.dp(this@SettingsActivity, 9))
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.colorOf(this@SettingsActivity, if (ok) R.color.accent else R.color.warning))
            }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 7), Ui.dp(this@SettingsActivity, 7))
        }
        row.addView(dot)
        val textBox = Ui.column(this)
        textBox.addView(Ui.text(this, title, 13f, Ui.colorOf(this, R.color.ink), bold = true))
        textBox.addView(
            Ui.text(this, description, 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(2)
        )
        val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        textParams.leftMargin = Ui.dp(this, 9)
        row.addView(textBox, textParams)

        val actionText = Ui.text(
            this,
            if (ok) "已开启" else "去开启",
            12f,
            Ui.colorOf(this, if (ok) R.color.accent else R.color.accent_deep),
            bold = true
        ).apply {
            setPadding(Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 6),
                Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 6))
            if (!ok) {
                isClickable = true
                setOnClickListener { action() }
            }
        }
        row.addView(actionText)
        permissionsContainer.addView(row)
    }

    private fun showLimitDialog() {
        val box = Ui.column(this).apply {
            setPadding(Ui.dp(this@SettingsActivity, 22), Ui.dp(this@SettingsActivity, 4),
                Ui.dp(this@SettingsActivity, 22), 0)
        }
        val input = editText(singleLine = true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText(prefs.dailyLimitMinutes.toString())
            setSelection(text.length)
        }
        box.addView(input, topMargin(8))
        val note = Ui.text(
            this,
            "1 – ${Prefs.MAX_LIMIT} 分钟。每天 0 点重置。",
            11f,
            Ui.colorOf(this, R.color.ink_soft)
        )
        box.addView(note, topMargin(8))

        val dialog = AlertDialog.Builder(this)
            .setTitle("每天最多看多久")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toIntOrNull()
                if (value == null || value !in Prefs.MIN_LIMIT..Prefs.MAX_LIMIT) {
                    input.error = "请输入 1 – ${Prefs.MAX_LIMIT} 之间的整数"
                    return@setOnClickListener
                }
                prefs.dailyLimitMinutes = value
                if (prefs.planEnabled) {
                    prefs.clearPlan()
                    Ui.toast(this, "已手动修改额度，自动计划已关闭")
                }
                refreshLimit()
                refreshPreview()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveReminderText(showToast: Boolean): Boolean {
        val title = titleInput.text.toString().trim()
        val body = bodyInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = "标题不能为空"
            return false
        }
        if (body.isEmpty()) {
            bodyInput.error = "正文不能为空"
            return false
        }
        prefs.reminderTitle = title
        prefs.reminderBody = body
        refreshPreview()
        if (showToast) Ui.toast(this, "提醒文案已保存")
        return true
    }

    private fun refreshPreview() {
        if (!::preview.isInitialized) return
        val limit = prefs.dailyLimitMinutes.toLong()
        // Use a realistic full-limit sample when there is no usage yet.
        val used = usage.todaySeconds()
            .takeIf { it > 0L }
            ?.let { max(1L, DayUtils.minutesRoundedUp(it)) }
            ?: limit
        val title = prefs.renderedTitle(used, limit)
        val body = prefs.renderedBody(used, limit)
        preview.text = "预览：\n$title\n$body"
    }

    private fun sendTest() {
        if (!saveReminderText(showToast = false)) return
        if (!ReminderNotifier.sendTest(this, prefs)) {
            Ui.toast(this, "发不出去：请先允许通知")
            SystemStatus.openNotificationSettings(this)
            return
        }
        Ui.toast(this, "测试提醒已发送")
    }

    private fun confirmClear() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("清空全部记录？")
            .setMessage("会删除每日观看记录并关闭自动计划，重新允许今天再次提醒。额度、提醒文案和监控 App 设置会保留。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setTextColor(Ui.colorOf(this, R.color.danger))
            button.setOnClickListener {
                usage.clearAll()
                prefs.lastReminderDate = null
                ReminderNotifier.cancelAll(this)
                Ui.toast(this, "记录已清空")
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun editText(singleLine: Boolean): EditText = EditText(this).apply {
        setTextColor(Ui.colorOf(this@SettingsActivity, R.color.ink))
        setHintTextColor(Ui.colorOf(this@SettingsActivity, R.color.ink_faint))
        textSize = 14f
        background = getDrawable(R.drawable.bg_input)
        setPadding(Ui.dp(this@SettingsActivity, 14), Ui.dp(this@SettingsActivity, 10),
            Ui.dp(this@SettingsActivity, 14), Ui.dp(this@SettingsActivity, 10))
        if (singleLine) {
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setSingleLine(true)
        } else {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
        }
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
    }

    private fun field(label: String, input: EditText): View {
        val box = Ui.column(this)
        box.addView(Ui.text(this, label, 12f, Ui.colorOf(this, R.color.ink_soft), bold = true))
        box.addView(input, topMargin(6))
        return box
    }

    private fun styleSwitch(sw: Switch) {
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.colorOf(this, R.color.accent), Ui.colorOf(this, R.color.white))
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.colorOf(this, R.color.accent_soft), Ui.colorOf(this, R.color.divider))
        )
    }

    private fun topMargin(dp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(this, dp) }
}
