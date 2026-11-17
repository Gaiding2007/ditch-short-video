package com.gaiding.ditchshortvideo.monitor

/**
 * Small in-app test suite for the pure recognition rules. It runs on-device
 * from Diagnostics so it can be checked without installing five huge apps.
 */
object DetectorSelfTest {
    data class Case(
        val name: String,
        val page: PageSnapshot,
        val expected: Boolean
    )

    data class Failure(
        val name: String,
        val expected: Boolean,
        val actual: Boolean,
        val reason: String
    )

    data class Report(
        val total: Int,
        val passed: Int,
        val failures: List<Failure>
    ) {
        val ok: Boolean get() = failures.isEmpty()
    }

    val caseCount: Int get() = buildCases().size

    fun run(): Report {
        val cases = buildCases()
        val failures = ArrayList<Failure>()
        var passed = 0

        cases.forEach { case ->
            val result = ShortVideoDetector.evaluate(case.page)
            if (result.isShortVideo == case.expected) {
                passed++
            } else {
                failures.add(
                    Failure(case.name, case.expected, result.isShortVideo, result.reason)
                )
            }
        }
        return Report(cases.size, passed, failures)
    }

    private fun buildCases(): List<Case> = listOf(
        Case(
            "抖音推荐流应计时",
            page(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.MainActivity",
                node("android.widget.TextView", desc = "点赞 1.2万"),
                node("android.widget.TextView", desc = "评论 3456"),
                node("android.widget.TextView", desc = "分享 789"),
                node("android.widget.TextView", text = "推荐"),
                node("android.widget.TextView", text = "首页"),
                node("android.widget.TextView", text = "消息")
            ),
            true
        ),
        Case(
            "抖音评论区（未输入）仍应计时",
            page(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.MainActivity",
                node("android.view.SurfaceView"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享"),
                node("android.widget.TextView", text = "全部评论")
            ),
            true
        ),
        Case(
            "快手视频画面兜底应计时",
            page(
                "com.smile.gifmaker",
                "com.yxcorp.gifshow.HomeActivity",
                node("android.view.SurfaceView"),
                node("android.widget.TextView", text = "发现"),
                node("android.widget.TextView", desc = "赞")
            ),
            true
        ),
        Case(
            "抖音消息页不应计时",
            page(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.MainActivity",
                node("android.widget.TextView", text = "消息"),
                node("android.widget.TextView", text = "朋友"),
                node("android.widget.TextView", text = "我"),
                node("android.widget.TextView", text = "私信")
            ),
            false
        ),
        Case(
            "抖音搜索页不应计时",
            page(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.search.SearchResultActivity",
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享")
            ),
            false
        ),
        Case(
            "快手发现流应计时",
            page(
                "com.smile.gifmaker",
                "com.yxcorp.gifshow.HomeActivity",
                node("android.widget.TextView", text = "发现"),
                node("android.widget.TextView", text = "关注"),
                node("android.widget.TextView", desc = "赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享")
            ),
            true
        ),
        Case(
            "快手个人页不应计时",
            page(
                "com.smile.gifmaker",
                "com.yxcorp.gifshow.ProfileActivity",
                node("android.widget.TextView", desc = "赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享")
            ),
            false
        ),
        Case(
            "微信视频号首页应计时",
            page(
                "com.tencent.mm",
                "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
                node("android.widget.TextView", text = "推荐"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "转发")
            ),
            true
        ),
        Case(
            "微信聊天页不应计时",
            page(
                "com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI",
                node("android.widget.TextView", text = "微信"),
                node("android.widget.TextView", text = "通讯录"),
                node("android.widget.TextView", text = "发现"),
                node("android.widget.TextView", text = "我")
            ),
            false
        ),
        Case(
            "微信视频号个人页不应计时",
            page(
                "com.tencent.mm",
                "com.tencent.mm.plugin.finder.profile.FinderProfileUI",
                node("android.widget.TextView", text = "推荐"),
                node("android.widget.TextView", desc = "点赞")
            ),
            false
        ),
        Case(
            "小红书视频笔记应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.NoteDetailActivity",
                node("android.view.SurfaceView"),
                node("android.widget.SeekBar"),
                node("android.widget.TextView", text = "00:12"),
                node("android.widget.TextView", desc = "点赞")
            ),
            true
        ),
        Case(
            "小红书视频笔记无进度条也应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.NoteDetailActivity",
                node("android.view.SurfaceView"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "收藏")
            ),
            true
        ),
        Case(
            "小红书视频笔记无画面节点也应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.NoteDetailActivity",
                node("android.widget.SeekBar"),
                node("android.widget.TextView", text = "00:12"),
                node("android.widget.TextView", desc = "点赞")
            ),
            true
        ),
        Case(
            "小红书图文笔记不应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.NoteDetailActivity",
                node("android.widget.ImageView"),
                node("android.widget.TextView", text = "这是一篇图文笔记"),
                node("android.widget.TextView", desc = "点赞")
            ),
            false
        ),
        Case(
            "小红书视频流（非笔记详情）应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.IndexActivityV2",
                node("android.view.SurfaceView"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论")
            ),
            true
        ),
        Case(
            "小红书图文信息流不应计时",
            page(
                "com.xingin.xhs",
                "com.xingin.xhs.IndexActivityV2",
                node("android.widget.ImageView"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论")
            ),
            false
        ),
        Case(
            "B 站故事模式应计时",
            page(
                "tv.danmaku.bili",
                "com.bilibili.video.story.StoryVideoActivity",
                node("android.widget.TextView", id = "tv.danmaku.bili:id/story_title"),
                node("android.view.SurfaceView")
            ),
            true
        ),
        Case(
            "B 站故事页 Activity 缺失也应靠标题识别",
            page(
                "tv.danmaku.bili",
                "",
                node("android.widget.TextView", id = "tv.danmaku.bili:id/story_title"),
                node("android.view.SurfaceView"),
                node("android.widget.TextView", desc = "点赞")
            ),
            true
        ),
        Case(
            "B 站普通视频缺少故事标题不应计时",
            page(
                "tv.danmaku.bili",
                "",
                node("android.view.SurfaceView"),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享")
            ),
            false
        ),
        Case(
            "B 站故事页 Activity 残留主界面也应靠标题识别",
            page(
                "tv.danmaku.bili",
                "tv.danmaku.bili.MainActivityV2",
                node("android.widget.TextView", id = "tv.danmaku.bili:id/story_title"),
                node("android.view.SurfaceView")
            ),
            true
        ),
        Case(
            "B 站普通视频详情不应计时",
            page(
                "tv.danmaku.bili",
                "tv.danmaku.bili.VideoDetailsActivity",
                node("android.widget.TextView", id = "tv.danmaku.bili:id/story_title")
            ),
            false
        ),
        Case(
            "输入框聚焦时不应计时",
            page(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.MainActivity",
                node("android.widget.EditText", text = "搜索", focused = true, editable = true),
                node("android.widget.TextView", desc = "点赞"),
                node("android.widget.TextView", desc = "评论"),
                node("android.widget.TextView", desc = "分享")
            ),
            false
        )
    )

    private fun page(pkg: String, activity: String, vararg nodes: UiNode) =
        PageSnapshot(pkg, activity, nodes.toList())

    private fun node(
        className: String,
        text: String = "",
        desc: String = "",
        id: String = "",
        focused: Boolean = false,
        editable: Boolean = false
    ) = UiNode(
        className = className,
        text = text,
        contentDescription = desc,
        viewId = id,
        focused = focused,
        editable = editable
    )
}
