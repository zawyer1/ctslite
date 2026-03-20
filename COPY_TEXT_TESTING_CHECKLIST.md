# Copy Text Feature Fix - Verification Checklist

## Pre-Deployment Verification

### Code Changes
- [x] CircleToSearchAccessibilityService.kt - getRootNode() rewritten
- [x] CopyTextOverlayManager.kt - Enhanced error handling and logging
- [x] No syntax errors (verified with get_errors)
- [x] All changes in production code (not debug-only)

### Logic Verification
- [x] getRootNode() now returns valid nodes when OverlayActivity is active
- [x] Proper window priority: Other apps > Our non-overlay windows > fallback
- [x] Null safety improved at multiple levels
- [x] Debug logging added for troubleshooting

---

## Post-Deployment Testing Checklist

### Test Environment Setup
```bash
Device: [Your Android Phone/Emulator]
Android Version: [e.g., Android 13, 14, 15]
App Version: [Build from fixed code]
Accessibility Service: [CircleToSearchAccessibilityService enabled]
```

### Test 1: Basic Copy Text Functionality
- [ ] Open Chrome and load a webpage with text
- [ ] Double-tap status bar to trigger Circle to Search
- [ ] Click "Copy Text" button in bottom bar
- [ ] Verify: Text regions appear with blue highlighting
- [ ] Verify: Pulsing glow animation on text regions
- [ ] Close the overlay (tap close button)
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 2: Text Selection
- [ ] Repeat Test 1 up to step 4
- [ ] Tap on one text region to enter it
- [ ] Verify: Text becomes darker blue (selected word style)
- [ ] Verify: Selection handles appear at start and end
- [ ] Verify: Floating toolbar appears with Copy/SelectAll/Cancel buttons
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 3: Word-by-Word Selection
- [ ] Repeat Test 2 up to step 5
- [ ] Tap on different words within the text region
- [ ] Verify: Selection expands/changes to include tapped word
- [ ] Verify: Handles move to new start/end positions
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 4: Handle Dragging
- [ ] Repeat Test 2 up to step 5
- [ ] Drag the left (start) handle to different words
- [ ] Verify: Selection updates in real-time
- [ ] Verify: Haptic feedback triggers on handle move
- [ ] Drag the right (end) handle to extend selection
- [ ] Verify: Selection range updates correctly
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 5: Select All Function
- [ ] Repeat Test 2 up to step 5
- [ ] Tap "Select All" in the floating toolbar
- [ ] Verify: All words in the region become selected
- [ ] Verify: Handles move to cover entire text region
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 6: Copy to Clipboard
- [ ] Repeat Test 2 up to step 5
- [ ] Tap "Copy" button in floating toolbar
- [ ] Verify: Toast appears saying "Text copied ✓"
- [ ] Verify: Overlay automatically dismisses
- [ ] Open Notes app (or any text editor)
- [ ] Long-press to open paste menu
- [ ] Paste the content (Ctrl+V or app's paste button)
- [ ] Verify: Selected text appears in the app
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 7: Different App Scenarios
Test with multiple apps to ensure broad compatibility:

#### Test 7A: Gmail
- [ ] Open Gmail and read an email
- [ ] Trigger Copy Text overlay
- [ ] Verify: Email text is highlighted
- [ ] Select and copy a sentence
- [ ] Verify: Copied text is accurate

#### Test 7B: WhatsApp/Messenger
- [ ] Open chat conversation
- [ ] Trigger Copy Text overlay
- [ ] Verify: Chat messages are highlighted
- [ ] Select and copy a message
- [ ] Verify: Copied text matches exactly

#### Test 7C: Settings App
- [ ] Open Settings app
- [ ] Trigger Copy Text overlay
- [ ] Verify: Settings text appears highlighted
- [ ] Copy a setting name/description

#### Test 7D: Web Browser
- [ ] Open any website
- [ ] Trigger Copy Text overlay
- [ ] Verify: Webpage text is highlighted (including headers, body, etc.)
- [ ] Copy multiple paragraphs

### Test 8: Scrollable Content
- [ ] Open long webpage or document
- [ ] Trigger Copy Text overlay when text is visible
- [ ] Scroll up/down in the app (below the overlay)
- [ ] Verify: Highlighted regions update as content scrolls
- [ ] Verify: New text regions appear as they scroll into view
- [ ] Result: ✅ PASS / ❌ FAIL

### Test 9: Edge Cases

#### Test 9A: App with No Text
- [ ] Open an app with mostly images/UI elements
- [ ] Trigger Copy Text overlay
- [ ] Verify: Overlay appears with dim background only
- [ ] Verify: No text highlights appear (expected)
- [ ] Verify: Close button is still functional

#### Test 9B: Rapid Activation/Deactivation
- [ ] Open Copy Text overlay
- [ ] Immediately close it (before scan completes)
- [ ] Verify: No crashes or error messages
- [ ] Verify: Overlay cleanly dismisses

#### Test 9C: Switching Between Apps
- [ ] Open Copy Text in App A
- [ ] While overlay is visible, switch to App B
- [ ] Verify: Overlay shows text from currently active app
- [ ] Verify: Text updates appropriately

### Test 10: Accessibility Service Status
- [ ] Go to Settings → Accessibility
- [ ] Verify: CircleToSearchAccessibilityService is enabled
- [ ] Grant any requested permissions if needed
- [ ] Disable and re-enable the service
- [ ] Verify: Copy Text still works after re-enabling

---

## Logcat Verification

### Enable Logcat Filtering
```bash
adb logcat | grep -E "CTS_Node|CopyText"
```

### Expected Log Patterns

#### Successful Text Detection
```
D/CTS_Node: getRootNode triggered. Our package: com.akslabs.circletosearch
D/CTS_Node: Window count: 3
D/CTS_Node: Found non-overlay window (fallback): com.akslabs.circletosearch
D/CopyText: show() called - setting up Copy Text overlay
D/CopyText: Overlay view added to window manager successfully
D/CopyText: Starting accessibility node scan...
D/CopyText: scanNodes: Root node = AccessibilityNodeInfo@..., package = com.akslabs.circletosearch
D/CopyText: Collected 47 text nodes from accessibility tree
D/CopyText: Processed 156 accessibility nodes, found 47 text regions
```

#### No Text Found (Edge Case)
```
D/CopyText: show() called - setting up Copy Text overlay
D/CopyText: Overlay view added to window manager successfully
D/CopyText: Starting accessibility node scan...
D/CopyText: scanNodes: Root node = AccessibilityNodeInfo@..., package = com.someapp.package
D/CopyText: Collected 0 text nodes from accessibility tree
D/CopyText: Processed 23 accessibility nodes, found 0 text regions
```

#### Error Scenarios (Should NOT see these)
```
❌ ERROR: getRoot() returned null! Cannot scan text nodes.
❌ ERROR: getRootNode triggered. Our package: ...
❌ No suitable root node found (omitting our own package)
```

---

## Performance Verification

### Metrics to Monitor

#### Overlay Appearance Time
- [ ] Overlay should appear within **500ms** of tapping "Copy Text"
- [ ] Measure: From button tap to first visual update

#### Text Detection Time
- [ ] Text regions should highlight within **1-2 seconds** for normal amount of text
- [ ] Measure: From overlay appearance to text highlights visible

#### Memory Usage
- [ ] No significant memory leak when repeatedly opening/closing overlay
- [ ] Monitor: adb shell dumpsys meminfo com.akslabs.circletosearch

### Acceptable Performance Ranges
| Operation | Target | Max Acceptable |
|-----------|--------|---|
| Overlay activation | 500ms | 1000ms |
| Text scanning | 1s | 3s |
| Selection update (drag) | <100ms | 200ms |
| Copy action | <100ms | 500ms |

---

## Regression Testing

### Verify Existing Features Still Work
- [ ] Status bar overlay rendering
- [ ] Double-tap gesture detection
- [ ] Circle drawing and image selection
- [ ] Reverse image search (Google Lens)
- [ ] Search results display in bottom sheet
- [ ] WebView tab switching
- [ ] Dark mode toggle
- [ ] Desktop mode toggle
- [ ] Share functionality
- [ ] Other bottom bar buttons

---

## Issue Reporting Template

If you encounter issues during testing, please report:

```
**Issue Title**: [Brief description]

**Device Info**:
- Phone Model: [e.g., Samsung Galaxy S24]
- Android Version: [e.g., Android 15]
- App Build: [Date/Version]

**Steps to Reproduce**:
1. [Step 1]
2. [Step 2]
3. ...

**Expected Behavior**: 
[What should happen]

**Actual Behavior**: 
[What actually happens]

**Logcat Output**:
[adb logcat output with CTS_ and CopyText tags]

**Screenshots/Videos**: 
[If available]

**Severity**: [Critical/High/Medium/Low]
```

---

## Sign-Off

- [ ] **QA Tester**: [Name] Date: [Date]
- [ ] **Developer**: [Name] Date: [Date]
- [ ] All tests passed
- [ ] No regressions found
- [ ] Ready for production deployment

---

## Post-Deployment (Production)

### Monitor
- [ ] User reports of "Text not showing" issues
- [ ] Crash reports related to CopyTextOverlayManager
- [ ] Accessibility service crashes or disabling
- [ ] Performance issues (slow text detection)

### Support Troubleshooting
If users report issues:
1. Have them enable Settings → Accessibility logging
2. Collect logcat with CTS_ and CopyText filters
3. Check if CircleToSearchAccessibilityService is enabled
4. Try disabling and re-enabling the service
5. Check if device has newer Android version (compatibility)
