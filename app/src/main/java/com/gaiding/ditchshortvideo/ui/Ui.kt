package com.gaiding.ditchshortvideo.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gaiding.ditchshortvideo.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tiny view helpers. The app intentionally uses platform views only, so there
 * is no Compose or AndroidX dependency to download or keep in sync.
 */
object Ui {
    fun dp(context: Context, value: Number): Int =
        (value.toFloat() * context.resources.displayMetrics.density).roundToInt()

    fun colorOf(context: Context, colorRes: Int): Int = context.getColor(colorRes)

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float = 14f,
        color: Int = colorOf(context, R.color.ink),
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        includeFontPadding = false
        typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        setLineSpacing(0f, 1.22f)
    }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(context: Context, paddingDp: Int = 18): LinearLayout =
        column(context).apply {
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp))
            elevation = dp(context, 1).toFloat()
        }

    fun add(
        parent: LinearLayout,
        child: View,
        top: Int = 0,
        bottom: Int = 0,
        left: Int = 0,
        right: Int = 0
    ) {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(left, top, right, bottom)
        parent.addView(child, params)
    }

    fun button(context: Context, label: String, primary: Boolean = true): TextView =
        text(
            context,
            label,
            14f,
            colorOf(context, if (primary) R.color.white else R.color.ink),
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(
                if (primary) {
                    R.drawable.bg_button_primary
                } else {
                    R.drawable.bg_button_secondary
                }
            )
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            minHeight = dp(context, 46)
            isClickable = true
            isFocusable = true
        }

    fun ghostButton(context: Context, label: String): TextView =
        text(context, label, 13f, colorOf(context, R.color.accent_deep), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
            isClickable = true
            isFocusable = true
        }

    fun stepButton(context: Context, label: String): TextView =
        text(context, label, 23f, colorOf(context, R.color.ink)).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_button_secondary)
            isClickable = true
            isFocusable = true
        }

    fun pill(
        context: Context,
        label: String,
        backgroundRes: Int,
        textColor: Int
    ): TextView = text(context, label, 12f, textColor, bold = true).apply {
        gravity = Gravity.CENTER
        setBackgroundResource(backgroundRes)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        maxLines = 1
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(colorOf(context, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            max(1, dp(context, 1))
        )
    }

    fun sectionHeader(context: Context, title: String, subtitle: String? = null): LinearLayout {
        val box = column(context)
        box.addView(text(context, title, 18f, colorOf(context, R.color.ink), bold = true))
        if (!subtitle.isNullOrBlank()) {
            val sub = text(context, subtitle, 12f, colorOf(context, R.color.ink_soft))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(context, 5)
            box.addView(sub, params)
        }
        return box
    }

    fun avatar(context: Context, letter: String, color: Int): TextView {
        val size = dp(context, 40)
        return text(context, letter, 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    fun legendSwatch(context: Context, color: Int): View {
        val size = dp(context, 11)
        return View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 3).toFloat()
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    fun edgeToEdge(activity: Activity, content: View) {
        val window = activity.window
        if (Build.VERSION.SDK_INT < 30) {
            if (Build.VERSION.SDK_INT == 26) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorOf(activity, R.color.ink)
            }
            return
        }

        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        // Touch decorView first: Window.getInsetsController() dereferences mDecor and
        // throws an NPE if the decor has not been installed yet.
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )

        val baseLeft = content.paddingLeft
        val baseTop = content.paddingTop
        val baseRight = content.paddingRight
        val baseBottom = content.paddingBottom

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
            content.setPadding(
                baseLeft,
                baseTop + bars.top,
                baseRight,
                baseBottom + max(bars.bottom, ime)
            )
            insets
        }
        window.decorView.requestApplyInsets()
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun hideKeyboard(activity: Activity, view: View) {
        val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
}
