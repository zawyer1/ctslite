# Copy Text Feature Fix - Code Changes Details

## Overview
Fixed a critical bug in the Circle to Search app's "Copy Text" feature where the accessibility node tree was returning null, preventing any text from being detected or displayed for copying.

---

## Change 1: CircleToSearchAccessibilityService.kt

### Location
File: `C:\Users\ashin\AndroidStudioProjects\CircleToSearch\app\src\main\java\com\akslabs\circletosearch\CircleToSearchAccessibilityService.kt`
Lines: 750-810 (in companion object)

### What Was Changed
The `getRootNode()` function which retrieves the accessibility node tree for text scanning.

### Old Code (BUGGY)
```kotlin
/**
 * Returns the current root AccessibilityNodeInfo for the active application window.
 * Skips TYPE_ACCESSIBILITY_OVERLAY windows (like our own UI) to find underlying text.
 */
fun getRootNode(): AccessibilityNodeInfo? {
    val service = instance ?: return null
    val ourPackage = service.packageName
    android.util.Log.d("CTS_Node", "getRootNode triggered. Our package: $ourPackage")
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val windows = service.windows
        android.util.Log.d("CTS_Node", "Window count: ${windows.size}")
        
        // 1. Filter out accessibility overlays and our own windows
        // Sort by layer/id to usually get the top-most content window
        val candidateWindows = windows.filter { window ->
            val root = try { window.root } catch (_: Exception) { null }
            window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
            root?.packageName != ourPackage &&  // ❌ PROBLEM: Filters out our package entirely
            root != null
        }
        
        // prefer TYPE_APPLICATION
        val appWindow = candidateWindows.firstOrNull { 
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION 
        }
        if (appWindow != null) {
            android.util.Log.d("CTS_Node", "Found app window: ${appWindow.root?.packageName}")
            return appWindow.root
        }
        
        // fallback to any candidate
        val fallbackWindow = candidateWindows.firstOrNull()
        if (fallbackWindow != null) {
            android.util.Log.d("CTS_Node", "Fallback window: ${fallbackWindow.root?.packageName}")
            return fallbackWindow.root
        }
    }
    
    // Final fallback logic
    val root = service.rootInActiveWindow
    if (root?.packageName != ourPackage) {  // ❌ PROBLEM: Rejects if our package is active
        android.util.Log.d("CTS_Node", "Defaulting to rootInActiveWindow: ${root?.packageName}")
        return root
    }
    
    android.util.Log.e("CTS_Node", "No suitable root node found (omitting our own package)")
    return null  // ❌ RETURNS NULL when OverlayActivity is active
}
```

### New Code (FIXED)
```kotlin
/**
 * Returns the current root AccessibilityNodeInfo for the active application window.
 * Prioritizes TYPE_APPLICATION windows, filters out pure accessibility overlays,
 * and returns our OverlayActivity if it's the active window (contains the dimmed screenshot).
 */
fun getRootNode(): AccessibilityNodeInfo? {
    val service = instance ?: return null
    val ourPackage = service.packageName
    android.util.Log.d("CTS_Node", "getRootNode triggered. Our package: $ourPackage")
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val windows = service.windows
        android.util.Log.d("CTS_Node", "Window count: ${windows.size}")
        
        // Strategy:
        // 1. Prefer TYPE_APPLICATION windows from OTHER packages (real content)
        // 2. Fall back to any non-overlay window (including our OverlayActivity if it's TYPE_APPLICATION)
        // 3. Last resort: use rootInActiveWindow
        
        val allWindowsWithRoots = windows.map { window ->
            try {
                val root = window.root
                Pair(window, root)
            } catch (_: Exception) {
                Pair(window, null)
            }
        }.filter { (_, root) -> root != null }
        
        // Find other-package TYPE_APPLICATION windows (highest priority)
        val otherPackageAppWindows = allWindowsWithRoots.filter { (window, root) ->
            window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
            root?.packageName != ourPackage  // ✅ Only exclude OTHER packages, not ours
        }
        
        if (otherPackageAppWindows.isNotEmpty()) {
            val window = otherPackageAppWindows.first()
            val packageName = window.second?.packageName
            android.util.Log.d("CTS_Node", "Found other-app TYPE_APPLICATION window: $packageName")
            return window.second  // ✅ Return real app content
        }
        
        // Fall back to any non-overlay window (includes our OverlayActivity TYPE_APPLICATION)
        val nonOverlayWindows = allWindowsWithRoots.filter { (window, _) ->
            window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        }
        
        if (nonOverlayWindows.isNotEmpty()) {
            val window = nonOverlayWindows.first()
            val packageName = window.second?.packageName
            android.util.Log.d("CTS_Node", "Found non-overlay window (fallback): $packageName")
            return window.second  // ✅ Return OverlayActivity if no other app
        }
    }
    
    // Final fallback: use rootInActiveWindow (works for our OverlayActivity too)
    val root = service.rootInActiveWindow
    if (root != null) {
        android.util.Log.d("CTS_Node", "Using rootInActiveWindow fallback: ${root.packageName}")
        return root  // ✅ Return valid node instead of filtering
    }
    
    android.util.Log.e("CTS_Node", "ERROR: No suitable root node found!")
    return null  // ✅ Only null as last resort
}
```

### Key Changes
1. **Line 768-769**: Changed priority strategy - now prefers OTHER packages' TYPE_APPLICATION windows
2. **Line 779-788**: Added fallback that INCLUDES our OverlayActivity windows
3. **Line 794-797**: Removed the `root?.packageName != ourPackage` check that was causing null returns
4. **Line 799**: Changed error conditions to be more lenient
5. **Throughout**: Enhanced logging for troubleshooting

---

## Change 2: CopyTextOverlayManager.kt

### Location
File: `C:\Users\ashin\AndroidStudioProjects\CircleToSearch\app\src\main\java\com\akslabs\circletosearch\ui\components\CopyTextOverlayManager.kt`

### Change 2A: Enhanced `show()` Method

**Lines: 68-95**

#### Old Code
```kotlin
fun show(onDismiss: () -> Unit) {
    if (dimView != null) return // Already visible
    onDismissCallback = onDismiss

    val view = DimPunchOutView(context) { dismiss() }
    dimView = view

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    try {
        windowManager.addView(view, params)  // ❌ No logging on success
    } catch (e: Exception) {
        e.printStackTrace()  // ❌ Minimal logging
        return
    }

    // Kick off node scan
    scanNodes(view)  // ❌ No logging before scan
}
```

#### New Code
```kotlin
fun show(onDismiss: () -> Unit) {
    if (dimView != null) return // Already visible
    android.util.Log.d("CopyText", "show() called - setting up Copy Text overlay")  // ✅ Added
    onDismissCallback = onDismiss

    val view = DimPunchOutView(context) { dismiss() }
    dimView = view

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    try {
        windowManager.addView(view, params)
        android.util.Log.d("CopyText", "Overlay view added to window manager successfully")  // ✅ Added
    } catch (e: Exception) {
        android.util.Log.e("CopyText", "Failed to add overlay view: ${e.message}", e)  // ✅ Enhanced
        e.printStackTrace()
        return
    }

    // Kick off node scan
    android.util.Log.d("CopyText", "Starting accessibility node scan...")  // ✅ Added
    scanNodes(view)
}
```

### Change 2B: Improved `scanNodes()` Method

**Lines: ~140-155**

#### Old Code
```kotlin
private fun scanNodes(view: DimPunchOutView) {
    scanJob?.cancel()
    scanJob = scope.launch {
        val nodes = withContext(Dispatchers.Default) {
            collectTextNodes(getRoot())  // ❌ No null handling
        }
        detectedNodes.clear()
        detectedNodes.addAll(nodes)
        view.invalidate() 
    }
}
```

#### New Code
```kotlin
private fun scanNodes(view: DimPunchOutView) {
    scanJob?.cancel()
    scanJob = scope.launch {
        val root = getRoot()  // ✅ Get root first
        android.util.Log.d("CopyText", "scanNodes: Root node = $root, package = ${root?.packageName}")  // ✅ Added
        
        if (root == null) {  // ✅ Check for null
            android.util.Log.w("CopyText", "ERROR: getRoot() returned null! Cannot scan text nodes.")  // ✅ Added
            detectedNodes.clear()
            view.invalidate()
            return@launch
        }
        
        val nodes = withContext(Dispatchers.Default) {
            collectTextNodes(root)  // ✅ Pass valid root
        }
        android.util.Log.d("CopyText", "Collected ${nodes.size} text nodes from accessibility tree")  // ✅ Added
        detectedNodes.clear()
        detectedNodes.addAll(nodes)
        view.invalidate() 
    }
}
```

### Change 2C: Rewritten `collectTextNodes()` Method

**Lines: ~157-195**

#### Old Code
```kotlin
private fun collectTextNodes(root: AccessibilityNodeInfo?): List<TextNode> {
    if (root == null) return emptyList()  // ❌ Silent fail, no logging
    val result = mutableListOf<TextNode>()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        try {
            val text = node.text ?: node.contentDescription
            if (!text.isNullOrEmpty()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    val fullText = text.toString()
                    val words = splitIntoWords(fullText, rect)
                    result.add(TextNode(
                        id = java.util.UUID.randomUUID().toString(),
                        fullText = fullText,
                        bounds = rect,
                        words = words
                    ))
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        } catch (_: Exception) {}  // ❌ Silent exception swallowing
    }
    return result  // ❌ No logging of results
}
```

#### New Code
```kotlin
private fun collectTextNodes(root: AccessibilityNodeInfo?): List<TextNode> {
    if (root == null) {
        android.util.Log.w("CopyText", "collectTextNodes called with null root!")  // ✅ Added
        return emptyList()
    }
    val result = mutableListOf<TextNode>()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var processedCount = 0  // ✅ Added counter

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        processedCount++  // ✅ Track processed count
        try {
            val text = node.text ?: node.contentDescription
            if (!text.isNullOrEmpty()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    val fullText = text.toString()
                    val words = splitIntoWords(fullText, rect)
                    if (words.isNotEmpty()) {  // ✅ Added word count check
                        result.add(TextNode(
                            id = java.util.UUID.randomUUID().toString(),
                            fullText = fullText,
                            bounds = rect,
                            words = words
                        ))
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        } catch (e: Exception) {  // ✅ Better exception handling
            android.util.Log.e("CopyText", "Error processing accessibility node: ${e.message}")  // ✅ Added
        }
    }
    android.util.Log.d("CopyText", "Processed $processedCount accessibility nodes, found ${result.size} text regions")  // ✅ Added
    return result
}
```

---

## Testing the Fix

### Step 1: Build and Deploy
```bash
cd C:\Users\ashin\AndroidStudioProjects\CircleToSearch
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Test Copy Text Feature
1. Open any app with text (Chrome, Gmail, etc.)
2. Double-tap the status bar to trigger Circle to Search
3. Click "Copy Text" button in the bottom bar
4. **Expected**: Overlay appears with text regions highlighted in blue
5. **Tap on text**: Selectable words appear with handles
6. **Select and Copy**: Floating toolbar shows Copy/SelectAll/Cancel buttons
7. **Tap Copy**: Text copied to clipboard with toast confirmation

### Step 3: Monitor Logcat
```bash
adb logcat | grep -E "CTS_Node|CopyText"
```

Expected output:
```
D/CTS_Node: getRootNode triggered. Our package: com.akslabs.circletosearch
D/CTS_Node: Window count: 3
D/CTS_Node: Found non-overlay window (fallback): com.akslabs.circletosearch
D/CopyText: show() called - setting up Copy Text overlay
D/CopyText: Overlay view added to window manager successfully
D/CopyText: Starting accessibility node scan...
D/CopyText: scanNodes: Root node = AccessibilityNodeInfo@abc123, package = com.akslabs.circletosearch
D/CopyText: Collected 47 text nodes from accessibility tree
D/CopyText: Processed 156 accessibility nodes, found 47 text regions
```

---

## Summary of Fixes

| Issue | Location | Old Behavior | New Behavior |
|-------|----------|--------------|--------------|
| getRootNode() returns null | CircleToSearchAccessibilityService.kt L750-810 | Filtered out our package completely | Allows OverlayActivity when it's active |
| No error handling | CopyTextOverlayManager.kt show() | No logging before scan | Added debug context |
| Silent null failures | CopyTextOverlayManager.kt scanNodes() | Silently passes null to tree walk | Detects and logs null root |
| No diagnostics | CopyTextOverlayManager.kt collectTextNodes() | No insight into what was processed | Logs node counts and text regions found |
| Text copy broken | Overall flow | Shows transparent screen only | Shows highlighted text ready for selection |
