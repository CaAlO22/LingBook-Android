# PDF Paper Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make non-force-white PDF export use Lingji's paper background color.

**Architecture:** Keep the existing WebView-based PDF export path. Change only `MarkdownPdfExporter` so normal exports set the WebView and HTML print background to `#FDFBF7`, while force-white exports remain pure white.

**Tech Stack:** Kotlin, Android WebView print adapter, CSS print rules.

## Global Constraints

- Use Lingji paper color exactly: `#FDFBF7`.
- Do not change force-white PDF export behavior.
- Run `./gradlew :app:compileDebugKotlin` after modification.
- Run `./gradlew :app:installDebug` if a device/emulator is available.

---

### Task 1: PDF export background

**Covers:** Approved design in conversation.

**Files:**
- Modify: `app/src/main/java/com/lingji/app/util/MarkdownPdfExporter.kt:52-159`

**Interfaces:**
- Consumes: existing `buildHtml(docTitle, sections, forcePrintWhite)` and `exportSectionsToPdf(...)`.
- Produces: unchanged public exporter API.

- [ ] **Step 1: Add a private color constant**

```kotlin
private const val PaperBackgroundColor = "#FDFBF7"
```

- [ ] **Step 2: Use paper color for non-force-white WebView background**

```kotlin
setBackgroundColor(
    android.graphics.Color.parseColor(if (forcePrintWhite) "#FFFFFF" else PaperBackgroundColor)
)
```

- [ ] **Step 3: Add normal export background CSS**

```kotlin
val paperBackgroundCss = if (forcePrintWhite) "" else """
    html, body { background: $PaperBackgroundColor; }
    @media print {
        html, body { background: $PaperBackgroundColor; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    }
""".trimIndent()
```

- [ ] **Step 4: Include CSS before force-white override**

```css
$paperBackgroundCss
$printWhiteCss
```

- [ ] **Step 5: Verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: build succeeds.

Run: `./gradlew :app:installDebug`
Expected: installs if a device/emulator is available; otherwise record no-device reason.
