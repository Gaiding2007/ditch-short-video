package com.gaiding.ditchshortvideo.monitor

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object SnapshotBuilder {
    private const val MAX_NODES = 320
    private const val MAX_DEPTH = 24

    fun capture(
        root: AccessibilityNodeInfo,
        packageName: String,
        activityName: String?
    ): PageSnapshot {
        val nodes = ArrayList<UiNode>(96)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            nodes.add(
                UiNode(
                    className = node.className?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty(),
                    contentDescription = node.contentDescription?.toString().orEmpty(),
                    viewId = runCatching { node.viewIdResourceName }.getOrNull().orEmpty(),
                    clickable = node.isClickable,
                    focused = node.isFocused,
                    editable = node.isEditable,
                    depth = depth
                )
            )

            if (depth >= MAX_DEPTH) continue
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                queue.add(child to depth + 1)
            }
        }

        return PageSnapshot(packageName, activityName, nodes)
    }
}
