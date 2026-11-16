package com.gaiding.ditchshortvideo.monitor

/**
 * Strict, deliberately conservative page recognition.
 *
 * The accessible view tree is noisy and the five apps change their UI often.
 * A page only counts when we see a strong combination of signals, never on a
 * package name alone. When in doubt, the timer pauses: false negatives are
 * preferable to spying on chats or counting a whole social app.
 */
object ShortVideoDetector {
    private val durationPattern = Regex("\\b\\d{1,2}:\\d{2}\\b")

    fun evaluate(page: PageSnapshot): Detection {
        if (page.nodes.isEmpty()) {
            return Detection(false, "页面结构为空", 0)
        }

        val activity = page.activityName.orEmpty()
        if (activity.isBlank()) {
            return Detection(false, "还没有拿到当前页面名称", 0)
        }

        val labels = page.nodes.asSequence()
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()

        val classes = page.nodes.map { it.className.lowercase() }
        val ids = page.nodes.map { it.viewId.lowercase() }
        val focusedEditor = page.nodes.any {
            it.focused && (it.editable || it.className.contains("EditText", ignoreCase = true))
        }

        if (activity.contains("search", ignoreCase = true)) {
            return Detection(false, "当前是搜索页", 10)
        }

        return when (page.packageName) {
            "com.ss.android.ugc.aweme" -> douyin(activity, labels, classes, focusedEditor)
            "com.smile.gifmaker" -> kuaishou(activity, labels, classes, focusedEditor)
            "com.tencent.mm" -> wechatFinder(activity, labels, focusedEditor)
            "com.xingin.xhs" -> xiaohongshu(activity, labels, classes, focusedEditor)
            "tv.danmaku.bili" -> bilibili(activity, ids, focusedEditor)
            else -> Detection(false, "未适配的 App", 0)
        }
    }

    private fun douyin(
        activity: String,
        labels: List<String>,
        classes: List<String>,
        focusedEditor: Boolean
    ): Detection {
        val simple = activity.substringAfterLast('.')
        if (!simple.contains("MainActivity", ignoreCase = true)) {
            return Detection(false, "抖音当前不在主页面（$simple）", 10)
        }
        if (activity.contains("search", true) ||
            activity.contains("detail", true) ||
            activity.contains("friend", true) ||
            activity.contains("profile", true) ||
            activity.contains("live", true)
        ) {
            return Detection(false, "抖音当前不是推荐流", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }

        val rail = railCategoryCount(labels)
        val hasFeedHint = labels.any {
            it.contains("推荐") || it.contains("精选") || it.contains("关注")
        }
        val hasVideo = hasVideoSurface(classes)
        // The interaction rail is the strongest signal, but some builds expose
        // only a surface and one or two labels. Keep the fallback narrow.
        if (rail >= 2 || (hasVideo && (rail >= 1 || hasFeedHint))) {
            return Detection(
                true,
                "抖音推荐流：互动栏 $rail 项${if (hasVideo) " · 视频画面" else ""}",
                78
            )
        }
        return Detection(false, "缺少点赞/评论/分享互动栏（$rail 项）", 30)
    }

    private fun kuaishou(
        activity: String,
        labels: List<String>,
        classes: List<String>,
        focusedEditor: Boolean
    ): Detection {
        val simple = activity.substringAfterLast('.')
        if (!simple.contains("HomeActivity", ignoreCase = true)) {
            return Detection(false, "快手当前不在首页（$simple）", 10)
        }
        if (activity.contains("search", true) ||
            activity.contains("profile", true) ||
            activity.contains("detail", true) ||
            activity.contains("live", true)
        ) {
            return Detection(false, "快手当前不是发现流", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }

        val rail = railCategoryCount(labels)
        val hasTopTab = labels.any {
            it.contains("关注") || it.contains("发现") || it.contains("精选") || it.contains("推荐")
        }
        val hasVideo = hasVideoSurface(classes)
        if (hasTopTab && (rail >= 2 || hasVideo)) {
            return Detection(
                true,
                "快手发现流：互动栏 $rail 项${if (hasVideo) " · 视频画面" else ""}",
                76
            )
        }
        return Detection(false, "缺少发现流特征（互动 $rail 项）", 30)
    }

    private fun wechatFinder(activity: String, labels: List<String>, focusedEditor: Boolean): Detection {
        val isFinderHome =
            activity.contains("FinderHomeAffinityUI", true) ||
                (activity.contains("Finder", true) && activity.contains("Home", true))
        if (!isFinderHome) {
            return Detection(false, "微信当前不在视频号首页", 10)
        }
        val excluded = listOf(
            "Profile", "Search", "Live", "Comment", "FeedDetail", "FinderFeed",
            "SnsTimeLine", "LauncherUI", "Chatting"
        )
        if (excluded.any { activity.contains(it, true) }) {
            return Detection(false, "微信当前不是视频号推荐流", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }
        val hint = if (labels.any { it.contains("推荐") || it.contains("关注") || it.contains("朋友") }) {
            "带推荐/关注标签"
        } else {
            "活动页命中"
        }
        return Detection(true, "微信视频号首页：$hint", 72)
    }

    private fun xiaohongshu(
        activity: String,
        labels: List<String>,
        classes: List<String>,
        focusedEditor: Boolean
    ): Detection {
        if (!activity.contains("NoteDetail", ignoreCase = true)) {
            return Detection(false, "小红书当前不在笔记详情页", 10)
        }
        if (activity.contains("Comment", true)) {
            return Detection(false, "小红书当前是评论页", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }

        val hasVideoSurface = hasVideoSurface(classes)
        // A video surface is often not exposed in the accessibility tree. A
        // seek bar, duration text or play/pause label is video-specific enough
        // to stand in for it; a generic ProgressBar (loading spinner) is not.
        val hasProgress = classes.any { it.contains("seekbar") } ||
            labels.any { durationPattern.containsMatchIn(it) } ||
            labels.any { it.contains("播放") || it.contains("暂停") }
        val hasInteraction = labels.any {
            it.contains("点赞") || it.contains("收藏") || it.contains("评论") || it.contains("分享")
        }

        if ((hasVideoSurface || hasProgress) && (hasInteraction || hasProgress)) {
            return Detection(true, "小红书视频笔记：画面/进度 + 播放/互动线索", 85)
        }
        return Detection(
            false,
            "笔记详情但缺少视频证据（画面=$hasVideoSurface，进度=$hasProgress）",
            25
        )
    }

    private fun bilibili(
        activity: String,
        ids: List<String>,
        focusedEditor: Boolean
    ): Detection {
        val isStory =
            activity.contains("StoryVideoActivity", true) ||
                activity.contains("StoryTransparentActivity", true)
        if (!isStory) {
            return Detection(false, "B 站当前不是故事/竖屏播放页", 10)
        }
        val excluded = listOf(
            "VideoDetailsActivity", "UnitedBizDetailsActivity", "MainActivityV2", "LiveRoom"
        )
        if (excluded.any { activity.contains(it, true) }) {
            return Detection(false, "B 站当前不是故事模式", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }
        val hasTitle = ids.any { it.contains("story_title") }
        return Detection(
            true,
            if (hasTitle) "B 站故事模式：标题栏命中" else "B 站故事播放页",
            88
        )
    }

    private fun hasVideoSurface(classes: List<String>): Boolean = classes.any {
        it.contains("surfaceview") ||
            it.contains("textureview") ||
            it.contains("videoview") ||
            it.contains("playerview") ||
            it.contains("exoplayer") ||
            it.contains("videocontainer")
    }

    private fun railCategoryCount(labels: List<String>): Int {
        fun hasAny(vararg words: String): Boolean =
            labels.any { label -> words.any { label.contains(it) } }

        var count = 0
        if (hasAny("点赞", "赞")) count++
        if (hasAny("评论")) count++
        if (hasAny("分享", "转发")) count++
        if (hasAny("收藏", "喜欢")) count++
        return count
    }

    private fun normalize(value: String?): String =
        value.orEmpty()
            .lowercase()
            .replace(Regex("\\s+"), "")
            .trim()
}
