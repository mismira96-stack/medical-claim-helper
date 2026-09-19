package com.example.meritzshortcut.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MeritzAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MeritzA11y"
        private var lastClickTime = 0L

        fun resetAttempt() {
            lastClickTime = 0L
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "=== MeritzAccessibilityService CONNECTED ===")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != "com.kr.meritzfire") return

        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString() ?: "null"
        Log.d(TAG, "Event: $eventType, Class: $className")

        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow is NULL")
            return
        }

        Log.i(TAG, "rootInActiveWindow retrieved successfully. Traversing tree...")
        val allNodes = mutableListOf<NodeDump>()
        traverseNode(root, 0, allNodes)

        Log.i(TAG, "=== Total nodes extracted: ${allNodes.size} ===")
        val textNodes = allNodes.filter { it.text.isNotBlank() || it.contentDesc.isNotBlank() }
        Log.i(TAG, "=== Nodes with text (${textNodes.size}): ===")
        for (node in textNodes) {
            Log.d(TAG, "  -> [${node.className}] text='${node.text}', desc='${node.contentDesc}', id='${node.viewId}', clickable=${node.isClickable}, bounds=${node.bounds}")
        }

        // Search for target claim button
        val targetNode = allNodes.find {
            val combined = (it.text + " " + it.contentDesc).trim()
            combined.contains("보험금 청구") || combined.contains("보험금청구") || combined == "청구"
        }

        if (targetNode != null) {
            Log.i(TAG, "🎯 TARGET FOUND: text='${targetNode.text}', desc='${targetNode.contentDesc}', clickable=${targetNode.isClickable}, bounds=${targetNode.bounds}")
            
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 3000) {
                lastClickTime = now
                val bounds = targetNode.bounds
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()

                Log.i(TAG, "👉 Step 1: Testing performAction(ACTION_CLICK)...")
                val clickResult = targetNode.rawNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "performAction(ACTION_CLICK) result: $clickResult")

                Log.i(TAG, "👉 Step 2: Dispatching gesture touch click at ($centerX, $centerY)...")
                clickAt(centerX, centerY)
            } else {
                Log.d(TAG, "Click debounced (${now - lastClickTime}ms ago). Skipping duplicate.")
            }
        } else {
            Log.d(TAG, "Target '보험금 청구' not found in current frame.")
        }
    }

    private fun clickAt(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "✅ Gesture click COMPLETED at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "❌ Gesture click CANCELLED at ($x, $y)")
            }
        }, null)
        Log.i(TAG, "dispatchGesture initiated: $dispatched at ($x, $y)")
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, depth: Int, result: MutableList<NodeDump>) {
        if (node == null) return
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val dump = NodeDump(
            className = node.className?.toString() ?: "",
            text = node.text?.toString() ?: "",
            contentDesc = node.contentDescription?.toString() ?: "",
            viewId = node.viewIdResourceName ?: "",
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            bounds = rect,
            rawNode = node
        )
        result.add(dump)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, depth + 1, result)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "=== MeritzAccessibilityService INTERRUPTED ===")
    }

    data class NodeDump(
        val className: String,
        val text: String,
        val contentDesc: String,
        val viewId: String,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val bounds: Rect,
        val rawNode: AccessibilityNodeInfo
    )
}
