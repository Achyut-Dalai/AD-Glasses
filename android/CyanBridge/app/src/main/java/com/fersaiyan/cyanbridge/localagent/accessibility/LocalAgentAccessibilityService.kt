package com.fersaiyan.cyanbridge.localagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.memoryvault.MemoryModeManager
import com.fersaiyan.cyanbridge.memoryvault.MemoryVaultBootstrap
import com.fersaiyan.cyanbridge.memoryvault.VaultLockStateManager
import com.fersaiyan.cyanbridge.localagent.LocalAgentNodeBounds
import com.fersaiyan.cyanbridge.localagent.LocalAgentScreenNode
import com.fersaiyan.cyanbridge.localagent.LocalAgentScreenshotResult
import com.fersaiyan.cyanbridge.localagent.LocalAgentDeviceState
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryRoomIndex
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/*
 * MIT Attribution (PhoneClaw)
 *
 * This AccessibilityService and its automation primitives are inspired by the
 * PhoneClaw project, which demonstrates AI-driven Android automation using the
 * Accessibility framework.
 *
 * Project: https://github.com/phoneclaw/phoneclaw
 * License: MIT (as stated by the upstream project)
 */
class LocalAgentAccessibilityService : AccessibilityService() {

    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        startPeriodicAutoCapture()

        // Be explicit about what we want; the XML config is authoritative but some
        // devices apply extra constraints unless flags are set here as well.
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            }
        }

        Log.i(TAG, "LocalAgentAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Keep the callback lightweight; we only use it for optional periodic screen capture.
        maybeAutoCapture()
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onDestroy() {
        stopPeriodicAutoCapture()
        screenshotExecutor.shutdownNow()
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun maybeAutoCapture() {
        // MVP: periodic capture of accessibility text into local JSONL memory.
        if (!LocalAgentPrefs.isAutoCaptureEnabled(applicationContext)) return
        if (!MemoryModeManager.isScreenOcrCaptureEnabled(applicationContext)) return
        if (!LocalAgentDeviceState.isReady(applicationContext)) return
        if (VaultLockStateManager.isLocked(applicationContext)) return

        MemoryVaultBootstrap.ensureInitialized(applicationContext)

        val intervalMin = LocalAgentPrefs.getCaptureIntervalMin(applicationContext)
        val intervalMs = intervalMin.toLong().coerceAtLeast(1L) * 60_000L

        val now = System.currentTimeMillis()
        val last = lastAutoCaptureAtMs
        if (last > 0L && now - last < intervalMs) return

        // Package attribution and captured text must come from the exact same root.
        // Never fall back to a previous/event/launcher package because that can mislabel
        // content from a blacklisted app during window transitions.
        val root = rootInActiveWindow ?: return
        val pkg = normalizePackageName(root.packageName)
        if (pkg.isBlank()) return

        val blacklist = LocalAgentPrefs.getCaptureBlacklistPackages(applicationContext)
        if (blacklist.contains(pkg)) {
            Log.d(TAG, "Skipping auto-capture for blacklisted package: $pkg")
            return
        }

        if (isOverlayPackage(pkg)) {
            Log.d(TAG, "Skipping overlay/system package capture: $pkg")
            return
        }

        lastForegroundNonOverlayPackage = pkg
        val text = collectTextFromRoot(
            root = root,
            includeContentDescriptions = true,
            includeViewIds = false,
            maxNodes = 10_000,
        ).take(400).joinToString("\n").takeIf { it.isNotBlank() } ?: return
        if (text.isBlank()) return

        LocalAgentMemoryStore.appendScreenCapture(
            context = applicationContext,
            packageName = pkg,
            text = text,
            tsMs = now,
        )

        // Also index into Room (FTS5) for fast retrieval.
        LocalAgentMemoryRoomIndex.indexScreenCaptureAsync(
            context = applicationContext,
            packageName = pkg,
            text = text,
            tsMs = now,
        )

        lastAutoCaptureAtMs = now
        Log.i(TAG, "Auto-captured screen text: pkg=$pkg chars=${text.length} intervalMin=$intervalMin")
    }

    private fun extractPackageFromActiveWindow(): String {
        val allWindows = windows.orEmpty()
        if (allWindows.isEmpty()) return ""

        var fallback: String = ""
        for (win in allWindows) {
            val root = runCatching { win.root }.getOrNull() ?: continue
            val pkg = normalizePackageName(root.packageName)
            if (pkg.isBlank()) continue

            if (fallback.isBlank()) fallback = pkg

            val activeOrFocused = runCatching { win.isActive || win.isFocused }.getOrDefault(false)
            if (!activeOrFocused) continue

            if (!isOverlayPackage(pkg)) {
                return pkg
            }

            if (fallback.isBlank()) {
                fallback = pkg
            }
        }

        return fallback
    }

    private fun normalizePackageName(raw: CharSequence?): String {
        return raw?.toString()?.trim()?.lowercase().orEmpty()
    }

    private fun isOverlayPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (OVERLAY_PACKAGE_PREFIXES.any { pkg.startsWith(it) }) return true
        return OVERLAY_PACKAGE_NAMES.contains(pkg)
    }

    // --- Core automation primitives ---

    /**
     * Returns all user-visible text (and optionally content descriptions) discovered by
     * traversing the active accessibility tree.
     */
    fun getAllTextFromScreen(
        includeContentDescriptions: Boolean = true,
        includeViewIds: Boolean = false,
        maxNodes: Int = 10_000,
    ): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        return collectTextFromRoot(root, includeContentDescriptions, includeViewIds, maxNodes)
    }

    private fun collectTextFromRoot(
        root: AccessibilityNodeInfo,
        includeContentDescriptions: Boolean,
        includeViewIds: Boolean,
        maxNodes: Int,
    ): List<String> {
        val out = ArrayList<String>(256)
        val seen = LinkedHashSet<String>()

        fun add(s: CharSequence?) {
            val v = s?.toString()?.trim().orEmpty()
            if (v.isNotBlank() && seen.add(v)) out.add(v)
        }

        fun walk(node: AccessibilityNodeInfo?, depth: Int = 0, visited: IntArray) {
            if (node == null) return
            if (visited[0] >= maxNodes) return
            visited[0]++

            if (node.isPassword) {
                add("[password field redacted]")
            } else {
                add(node.text)
                if (includeContentDescriptions) add(node.contentDescription)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(node.hintText)
            }

            if (includeViewIds) {
                val id = node.viewIdResourceName
                if (!id.isNullOrBlank()) add("viewId=$id")
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1, visited)
                if (visited[0] >= maxNodes) return
            }
        }

        walk(root, visited = intArrayOf(0))
        return out
    }

    fun dumpScreenNodes(maxNodes: Int = 250): List<LocalAgentScreenNode> {
        val out = ArrayList<LocalAgentScreenNode>(maxNodes.coerceAtMost(250))
        val visited = intArrayOf(0)

        fun walk(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            if (visited[0] >= maxNodes) return
            visited[0]++

            val rect = Rect()
            runCatching { node.getBoundsInScreen(rect) }

            val text = if (node.isPassword) "" else node.text?.toString()?.trim().orEmpty()
            val desc = if (node.isPassword) "" else node.contentDescription?.toString()?.trim().orEmpty()
            val hintText = if (node.isPassword || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                ""
            } else {
                node.hintText?.toString()?.trim().orEmpty()
            }
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            val className = node.className?.toString()?.substringAfterLast('.')?.trim().orEmpty()
            val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
            val isZeroSize = rect.width() <= 0 || rect.height() <= 0
            val includeNode = visible && !isZeroSize && (text.isNotBlank() ||
                desc.isNotBlank() ||
                hintText.isNotBlank() ||
                viewId.isNotBlank() ||
                node.isClickable ||
                node.isEditable ||
                node.isScrollable ||
                node.isCheckable)

            if (includeNode) {
                out.add(
                    LocalAgentScreenNode(
                        index = out.size,
                        depth = depth,
                        text = text,
                        contentDescription = desc,
                        hintText = hintText,
                        className = className,
                        viewId = viewId,
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable,
                        isPassword = node.isPassword,
                        isCheckable = node.isCheckable,
                        isChecked = node.isChecked,
                        isFocused = node.isFocused,
                        bounds = LocalAgentNodeBounds(
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                        ),
                    )
                )
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
                if (visited[0] >= maxNodes) return
            }
        }

        rootInActiveWindow?.let { walk(it) }

        // The active app tree commonly excludes the IME. Include it separately so the
        // planner can see labeled Send/Search/Done buttons before choosing press_enter.
        for (window in windows.orEmpty()) {
            if (visited[0] >= maxNodes) break
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            runCatching { window.root }.getOrNull()?.let { walk(it) }
        }
        return out
    }

    fun getCurrentForegroundPackageName(): String? {
        val candidates = listOf(
            normalizePackageName(rootInActiveWindow?.packageName),
            extractPackageFromActiveWindow(),
            normalizePackageName(lastForegroundNonOverlayPackage),
        )
        return candidates.firstOrNull { it.isNotBlank() }
    }

    /** Exact package from the current root only; screenshot capture must not use stale fallback state. */
    fun getActiveWindowPackageName(): String? =
        normalizePackageName(rootInActiveWindow?.packageName).takeIf { it.isNotBlank() }

    /**
     * Captures a software bitmap only when Android granted this accessibility capability. The
     * caller owns the returned bitmap and must recycle it after writing its ephemeral file.
     */
    suspend fun takeScreenshotForPlanning(): LocalAgentScreenshotResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return LocalAgentScreenshotResult(error = "android_version_unsupported")
        }

        val deferred = CompletableDeferred<LocalAgentScreenshotResult>()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val result = runCatching {
                            val buffer = screenshot.hardwareBuffer
                            try {
                                val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                    ?: error("Unable to wrap screenshot buffer")
                                val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: error("Unable to copy screenshot bitmap")
                                LocalAgentScreenshotResult(bitmap = softwareBitmap)
                            } finally {
                                buffer.close()
                            }
                        }.getOrElse {
                            LocalAgentScreenshotResult(error = "screenshot_copy_failed")
                        }
                        if (!deferred.complete(result)) {
                            result.bitmap?.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        deferred.complete(LocalAgentScreenshotResult(error = "screenshot_error_$errorCode"))
                    }
                },
            )
        } catch (_: Exception) {
            return LocalAgentScreenshotResult(error = "screenshot_request_failed")
        }

        return try {
            withTimeoutOrNull(SCREENSHOT_CALLBACK_TIMEOUT_MS) { deferred.await() }
                ?: LocalAgentScreenshotResult(error = "screenshot_timeout")
        } finally {
            if (!deferred.isCompleted) deferred.cancel()
        }
    }

    /** Tap an absolute coordinate using gesture injection (API 24+; minSdk=24 in this app). */
    fun simulateClick(
        x: Int,
        y: Int,
        durationMs: Long = 40L,
        onComplete: ((success: Boolean) -> Unit)? = null,
    ): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(false)
                }
            },
            null,
        )
    }

    /**
     * Finds a node by visible text or contentDescription and clicks it.
     *
     * Implementation detail: if the matched node itself is not clickable, we walk
     * up the parent chain to find a clickable container.
     */
    fun clickByTextOrDesc(
        query: String,
        ignoreCase: Boolean = true,
        partialMatch: Boolean = true,
        maxNodes: Int = 10_000,
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val q = query.trim()
        if (q.isBlank()) return false

        val target = findFirstNodeMatching(root, q, ignoreCase, partialMatch, maxNodes)
            ?: return false

        return performClickBestEffort(target)
    }

    /**
     * Helper for ACTION_SET_TEXT.
     *
     * NOTE: This requires an editable/focusable node. For best results, call click/focus
     * on the field before setting text.
     */
    fun performSetText(node: AccessibilityNodeInfo, newText: CharSequence): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        // Some UIs require focus first.
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }
            .getOrDefault(false)
    }

    /** Scroll by gesture (swipe). */
    fun scrollGesture(
        direction: ScrollDirection,
        distanceRatio: Float = 0.65f,
        durationMs: Long = 350L,
    ): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        if (w <= 0 || h <= 0) return false

        val x = w / 2f
        val dy = (h * distanceRatio).coerceIn(50f, h.toFloat())

        val (startY, endY) = when (direction) {
            // "Scroll down" == move content down == swipe up.
            ScrollDirection.DOWN -> (h * 0.80f) to (h * 0.80f - dy)
            // "Scroll up" == move content up == swipe down.
            ScrollDirection.UP -> (h * 0.20f) to (h * 0.20f + dy)
        }

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Submit the focused input field, with an IME-button fallback for custom keyboards. */
    fun pressEnter(): Boolean {
        // Tier 1: Use ACTION_IME_ENTER on the focused input field.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (window in windows.orEmpty()) {
                val root = runCatching { window.root }.getOrNull() ?: continue
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                val submitted = focused?.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                ) == true
                if (submitted) return true
            }
        }

        // Tier 2: Find a labeled keyboard action button ("Search", "Go", "Done", etc.)
        for (window in windows.orEmpty()) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            val actionNode = findKeyboardActionNode(root)
            if (actionNode != null && performClickBestEffort(actionNode)) return true
        }

        // Tier 3: Tap the IME window's submit area by coordinates.
        // The submit button is typically in the bottom-right corner of the IME window.
        for (window in windows.orEmpty()) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val bounds = Rect()
            runCatching { window.getBoundsInScreen(bounds) }
            if (!bounds.isEmpty) {
                val x = bounds.right - (bounds.width() * 0.10f)
                val y = bounds.bottom - (bounds.height() * 0.14f)
                if (simulateClick(x.toInt(), y.toInt())) return true
            }
        }

        return false
    }

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Arbitrary swipe from (startX,startY) to (endX,endY). */
    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /** Long press at (x,y) with a longer duration. */
    fun longPress(
        x: Int,
        y: Int,
        durationMs: Long = 1000L,
    ): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    // --- internals ---

    private fun findFirstNodeMatching(
        root: AccessibilityNodeInfo,
        query: String,
        ignoreCase: Boolean,
        partialMatch: Boolean,
        maxNodes: Int,
    ): AccessibilityNodeInfo? {
        val q = query.trim()
        if (q.isBlank()) return null

        fun matches(value: CharSequence?): Boolean {
            val s = value?.toString()?.trim().orEmpty()
            if (s.isBlank()) return false
            return if (partialMatch) s.contains(q, ignoreCase = ignoreCase)
            else s.equals(q, ignoreCase = ignoreCase)
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++

            if (matches(node.text) || matches(node.contentDescription)) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    private fun performClickBestEffort(node: AccessibilityNodeInfo): Boolean {
        // 1) Click the node itself if it can.
        if (node.isClickable) {
            return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
        }

        // 2) Walk up the parent chain looking for a clickable container.
        var p: AccessibilityNodeInfo? = node
        var hops = 0
        while (hops < 12) {
            val current = p ?: break
            if (current.isClickable) {
                return runCatching { current.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    .getOrDefault(false)
            }
            p = current.parent
            hops++
        }

        // 3) Fallback: try coordinate click from bounds.
        val rect = Rect()
        runCatching { node.getBoundsInScreen(rect) }
        if (!rect.isEmpty) {
            return simulateClick(rect.centerX(), rect.centerY())
        }

        return false
    }

    enum class ScrollDirection { UP, DOWN }

    /** Compact best-effort text snapshot for the agent observer. */
    fun dumpActiveWindowText(maxLines: Int = 400): String? {
        val lines = getAllTextFromScreen(includeContentDescriptions = true)
        if (lines.isEmpty()) return null
        return lines.take(maxLines).joinToString("\n")
    }

    /** Best-effort typing: focused field first; otherwise first editable node. */
    fun typeTextBestEffort(text: CharSequence, fieldHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = focused
            ?: findEditableByHint(root, fieldHint)
            ?: findFirstEditable(root)
        target ?: return false

        return performSetText(target, text)
    }

    private fun findEditableByHint(node: AccessibilityNodeInfo?, fieldHint: String?): AccessibilityNodeInfo? {
        val hint = fieldHint?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (node == null) return null

        if (node.isEditable && matchesHint(node, hint)) return node
        for (i in 0 until node.childCount) {
            val found = findEditableByHint(node.getChild(i), hint)
            if (found != null) return found
        }
        return null
    }

    private fun matchesHint(node: AccessibilityNodeInfo, hint: String): Boolean {
        val hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString().orEmpty()
        } else {
            ""
        }
        val candidates = listOf(
            node.text?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty(),
            hintText,
            node.viewIdResourceName.orEmpty(),
        )
        return candidates.any { it.contains(hint, ignoreCase = true) }
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findKeyboardActionNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = node.text?.toString().orEmpty()
            .ifBlank { node.contentDescription?.toString().orEmpty() }
            .trim()
            .lowercase()
        if (node.isClickable && (label in KEYBOARD_ACTION_LABELS || label.endsWith(" search"))) {
            return node
        }

        for (i in 0 until node.childCount) {
            val found = findKeyboardActionNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null

    private fun startPeriodicAutoCapture() {
        stopPeriodicAutoCapture()
        val r = object : Runnable {
            override fun run() {
                // Use the same capture logic as events, but don't rely on events firing.
                runCatching { maybeAutoCapture() }
                handler.postDelayed(this, PERIODIC_TICK_MS)
            }
        }
        periodicRunnable = r
        handler.post(r)
    }

    private fun stopPeriodicAutoCapture() {
        periodicRunnable?.let { handler.removeCallbacks(it) }
        periodicRunnable = null
    }

    companion object {
        private const val TAG = "LocalAgentAccSvc"
        private const val PERIODIC_TICK_MS = 30_000L
        private const val SCREENSHOT_CALLBACK_TIMEOUT_MS = 5_000L
        private val OVERLAY_PACKAGE_NAMES = setOf(
            "com.android.systemui",
        )
        private val OVERLAY_PACKAGE_PREFIXES = setOf(
            "com.android.launcher",
            "com.google.android.launcher",
            "com.samsung.android.launcher",
        )
        private val KEYBOARD_ACTION_LABELS = setOf(
            "search", "enter", "go", "done", "send", "next", "submit", "confirm", "ok",
        )

        @Volatile
        var instance: LocalAgentAccessibilityService? = null
            private set

        @Volatile
        private var lastAutoCaptureAtMs: Long = 0L

        @Volatile
        private var lastForegroundNonOverlayPackage: String? = null

        fun isRunning(): Boolean = instance != null
    }
}
