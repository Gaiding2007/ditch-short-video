package com.gaiding.ditchshortvideo.monitor

/**
 * A small, UI-framework-free description of the visible accessibility tree.
 * Keeping this pure makes the rules easy to reason about and to self-test.
 */
data class UiNode(
    val className: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val clickable: Boolean = false,
    val focused: Boolean = false,
    val editable: Boolean = false,
    val depth: Int = 0
)

data class PageSnapshot(
    val packageName: String,
    val activityName: String?,
    val nodes: List<UiNode>
)

data class Detection(
    val isShortVideo: Boolean,
    val reason: String,
    val confidence: Int
)
