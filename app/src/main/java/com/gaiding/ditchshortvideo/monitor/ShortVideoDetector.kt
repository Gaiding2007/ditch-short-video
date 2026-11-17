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

    // Some OEM accessibility stacks report the first window of a page as
    // FrameLayout/View (or with no Activity at all). The detectors for these
    // apps carry a strict structural fallback, so a blank Activity is not an
    // automatic veto; the page still needs video/feed evidence.
    private val structuralFallbackPackages = setOf(
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.xingin.xhs",
        "tv.danmaku.bili"
    )

    fun needsActivityFallback(pkg: String): Boolean = pkg in structuralFallbackPackages

    // Only these two have a structural detector that is stronger than a stale
    // Activity name. Clearing the Activity on every overlay is harmful for
    // Douyin/Kuaishou: ColorOS reports transient FrameLayout/View windows while
    // the feed is still on screen, and clearing MainActivity sends the detector
    // down the weaker "Activity missing" path.
    private val activityClearPackages = setOf("com.xingin.xhs", "tv.danmaku.bili")

    fun shouldClearActivityOnOverlay(pkg: String): Boolean = pkg in activityClearPackages

    fun evaluate(page: PageSnapshot): Detection {
        if (page.nodes.isEmpty()) {
            return Detection(false, "页面结构为空", 0)
        }
        val activity = page.activityName.orEmpty()
        if (activity.isBlank() && page.packageName !in structuralFallbackPackages) {
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
            "tv.danmaku.bili" -> bilibili(activity, labels, classes, ids, focusedEditor)
            else -> Detection(false, "未适配的 App", 0)
        }
    }

    /**
     * A short-video feed often opens comments as a dialog or Activity. That
     * page is not a video, but treating it as "left the app" made the timer
     * stop while the user was still one tap away from the feed. Only give the
     * long grace period to pages that actually look like comments.
     */
    fun isCommentLike(page: PageSnapshot): Boolean {
        val activity = page.activityName.orEmpty()
        if (activity.contains("Comment", ignoreCase = true)) return true

        val labels = page.nodes.flatMap { node ->
            listOf(node.text, node.contentDescription)
        }.filter { it.isNotBlank() }
        val markers = listOf(
            "全部评论", "评论区", "说点什么", "留下你的评论",
            "发送评论", "条评论", "展开更多回复", "回复 @"
        )
        if (labels.any { label -> markers.any { label.contains(it) } }) return true

        val focusedCommentEditor = page.nodes.any {
            it.editable && it.focused &&
                (it.text.contains("评论") || it.contentDescription.contains("评论"))
        }
        return focusedCommentEditor
    }

    private fun douyin(
        activity: String,
        labels: List<String>,
        classes: List<String>,
        focusedEditor: Boolean
    ): Detection {
        val simple = activity.substringAfterLast('.')
        val activityKnown = activity.isNotBlank()
        if (activityKnown && !simple.contains("MainActivity", ignoreCase = true)) {
            return Detection(false, "抖音当前不在主页面（$simple）", 10)
        }
        if (activityKnown && (activity.contains("search", true) ||
                activity.contains("detail", true) ||
                activity.contains("friend", true) ||
                activity.contains("profile", true) ||
                activity.contains("live", true))
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

        if (!activityKnown) {
            // OnePlus/ColorOS can report the first feed window as
            // FrameLayout/View. Require a stronger combination than the known
            // MainActivity path so a messages/profile page cannot sneak in.
            if (hasFeedHint && (rail >= 3 || (hasVideo && rail >= 2))) {
                return Detection(
                    true,
                    "抖音推荐流（Activity 缺失兜底）：互动栏 $rail 项${if (hasVideo) " · 视频画面" else ""}",
                    66
                )
            }
            return Detection(false, "Activity 为空，等待更强的推荐流证据（互动 $rail 项）", 25)
        }

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
        val activityKnown = activity.isNotBlank()
        if (activityKnown && !simple.contains("HomeActivity", ignoreCase = true)) {
            return Detection(false, "快手当前不在首页（$simple）", 10)
        }
        if (activityKnown && (activity.contains("search", true) ||
                activity.contains("profile", true) ||
                activity.contains("detail", true) ||
                activity.contains("live", true))
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

        if (!activityKnown) {
            if (hasTopTab && (rail >= 3 || (hasVideo && rail >= 2))) {
                return Detection(
                    true,
                    "快手发现流（Activity 缺失兜底）：互动栏 $rail 项${if (hasVideo) " · 视频画面" else ""}",
                    64
                )
            }
            return Detection(false, "Activity 为空，等待发现流特征（互动 $rail 项）", 25)
        }

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
        val excluded = listOf("Comment", "Search", "Login", "Chat", "Profile")
        if (excluded.any { activity.contains(it, true) }) {
            return Detection(false, "小红书当前不是视频播放页", 20)
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
        val rail = railCategoryCount(labels)
        val videoEvidence = hasVideoSurface || hasProgress

        // Xiaohongshu has a dedicated full-screen video feed as well as video
        // notes. Do not require NoteDetailActivity: the video feed often stays
        // on IndexActivityV2 (or reports no Activity at all).
        val videoPage = activity.isBlank() ||
            activity.contains("NoteDetail", true) ||
            activity.contains("Video", true) ||
            activity.contains("Player", true) ||
            activity.contains("Feed", true) ||
            activity.contains("IndexActivityV2", true)

        if (videoEvidence && videoPage &&
            (rail >= 2 || (hasProgress && rail >= 1) || (hasVideoSurface && hasProgress))
        ) {
            return Detection(
                true,
                "小红书视频页：${if (hasVideoSurface) "视频画面" else "播放进度"} + 互动 $rail 项",
                80
            )
        }
        if (!videoEvidence) {
            return Detection(
                false,
                "当前没有视频画面或播放进度，按图文/普通页面处理",
                25
            )
        }
        return Detection(false, "疑似小红书视频页，但缺少互动/播放证据", 30)
    }

    private fun bilibili(
        activity: String,
        labels: List<String>,
        classes: List<String>,
        ids: List<String>,
        focusedEditor: Boolean
    ): Detection {
        val excluded = listOf(
            "VideoDetailsActivity", "UnitedBizDetailsActivity", "LiveRoom"
        )
        if (excluded.any { activity.contains(it, true) }) {
            return Detection(false, "B 站当前不是故事模式", 20)
        }
        if (focusedEditor) {
            return Detection(false, "输入框正在使用", 15)
        }

        val hasTitle = ids.any { it.contains("story_title") }
        val hasStoryClass = classes.any { it.contains("story") }
        val isStoryActivity =
            activity.contains("StoryVideoActivity", true) ||
                activity.contains("StoryTransparentActivity", true)
        val hasVideo = hasVideoSurface(classes)
        val rail = railCategoryCount(labels)

        // Bilibili sometimes reports a FrameLayout (or nothing) when the story
        // page first opens; the story_title id appears before the Activity name
        // does. Treat that id as strong evidence, but do not let a generic
        // video page with likes/comments pass as a story.
        if (!hasTitle && !hasStoryClass && !isStoryActivity) {
            return Detection(false, "B 站当前不是故事/竖屏播放页", 10)
        }
        if (hasVideo || hasTitle || rail >= 2) {
            return Detection(
                true,
                "B 站故事模式：${if (hasTitle) "标题栏" else "播放页"}命中",
                88
            )
        }
        return Detection(false, "B 站故事页缺少播放/互动证据", 30)
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
