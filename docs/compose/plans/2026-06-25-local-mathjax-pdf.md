# Local MathJax PDF Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PDF export render Markdown formulas reliably without depending on network CDN availability.

**Architecture:** Keep the existing `MarkdownPdfExporter` WebView + Android print adapter flow. Bundle MathJax v3 assets under `app/src/main/assets/mathjax/` and load `tex-svg.js` through `file:///android_asset/...`, preserving the current Markdown conversion and MathJax startup wait before printing.

**Tech Stack:** Kotlin, Android WebView, Android assets, MathJax v3 SVG output, Gradle Android assets packaging.

## Global Constraints

- Do not replace the existing WebView print export pipeline.
- Do not change `MarkdownToHtml` behavior except if required by local MathJax loading.
- Preserve supported formula delimiters: `$...$`, `$$...$$`, `\\(...\\)`, `\\[...\\]`.
- Run `./gradlew :app:compileDebugKotlin` after modification.
- Run `./gradlew :app:installDebug` if a device/emulator is available.

---

### Task 1: Bundle local MathJax assets

**Covers:** Approved design in conversation.

**Files:**
- Create: `app/src/main/assets/mathjax/tex-svg.js`
- Create: `app/src/main/assets/mathjax/input/tex/extensions/*.js` as required by MathJax loader
- Create: `app/src/main/assets/mathjax/output/svg/fonts/tex/*.js` as required by SVG font loader

**Interfaces:**
- Consumes: MathJax v3 browser component files.
- Produces: `file:///android_asset/mathjax/tex-svg.js` available to WebView HTML.

- [ ] **Step 1: Create assets directory**

Run: `New-Item -ItemType Directory -Force "app/src/main/assets/mathjax"`
Expected: directory exists.

- [ ] **Step 2: Add MathJax component files**

Copy the MathJax v3 `es5/tex-svg.js` component and its loader dependencies into `app/src/main/assets/mathjax/`, preserving the paths expected by MathJax. Use the installed package/cache if available; otherwise fetch the official npm package during implementation.

- [ ] **Step 3: Confirm asset entry file**

Run: `Test-Path "app/src/main/assets/mathjax/tex-svg.js"`
Expected: `True`.

### Task 2: Load MathJax from assets in PDF HTML

**Covers:** Approved design in conversation.

**Files:**
- Modify: `app/src/main/java/com/lingji/app/util/MarkdownPdfExporter.kt:147-156`

**Interfaces:**
- Consumes: local asset URL `file:///android_asset/mathjax/tex-svg.js` from Task 1.
- Produces: unchanged public `MarkdownPdfExporter` API with offline formula rendering.

- [ ] **Step 1: Replace CDN script URL**

Change:

```html
<script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
```

To:

```html
<script src="file:///android_asset/mathjax/tex-svg.js"></script>
```

- [ ] **Step 2: Keep MathJax configuration unchanged**

Preserve:

```kotlin
MathJax = {
    tex: {
        inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
        displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']]
    },
    svg: { fontCache: 'global' }
};
```

- [ ] **Step 3: Keep render wait unchanged**

Preserve:

```kotlin
view.evaluateJavascript(
    "(window.MathJax && MathJax.startup ? MathJax.startup.promise : Promise.resolve()).then(function(){ return true; })"
) {
    printWebView(context, docTitle, view)
}
```

### Task 3: Verify build and device sync

**Covers:** Approved design in conversation.

**Files:**
- Verify only.

**Interfaces:**
- Consumes: completed Tasks 1-2.
- Produces: verified Kotlin build and installed debug APK when possible.

- [ ] **Step 1: Compile Kotlin**

Run: `./gradlew :app:compileDebugKotlin`
Expected: build succeeds.

- [ ] **Step 2: Install debug APK**

Run: `./gradlew :app:installDebug`
Expected: installs if a device/emulator is available. If it fails because no devices are connected, record that reason and do not treat it as code failure.
