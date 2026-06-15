# LingBook Android

LingBook 的 Android 客户端，一款面向知识碎片、笔记与学习计划的轻量级学习工具。

## 技术栈

- **语言**：Kotlin 1.9.24
- **UI 框架**：Jetpack Compose（BOM 2024.10.00，Compose UI 1.7.x）
- **架构组件**：ViewModel、Navigation、Room
- **依赖注入**：Hilt 2.50
- **网络与存储**：Retrofit、OkHttp、Room
- **Markdown 渲染**：Markwon
- **底部毛玻璃效果**：Haze 1.0.2

## 设计约定

- 强制浅色模式：`MainActivity` 中 `LingjiTheme(darkTheme = false)`。
- 配色对齐 Web 端：
  - 暖白纸张背景 `Paper = #FDFBF7`
  - 主色 `Stone800 = #292524`
  - AI 强调色 `Teal600 = #0D9488`
- 标题使用 `FontFamily.Serif`（Noto Serif CJK SC），正文使用默认无衬线字体。
- 卡片：白底 + 细边框 + 1dp 阴影 + 16dp 圆角。
- 底部 AI 输入栏采用 **Floating Action Bar** 风格，配合 Haze 实现真正的 backdrop blur 毛玻璃效果。

## 构建与运行

在 Android 模拟器或真机上运行调试版本：

```bash
./gradlew :app:installDebug
```

安装后手动启动应用，或执行：

```bash
adb shell am start -n com.lingji.app/.MainActivity
```

查看已连接设备：

```bash
adb devices
```

## 项目结构

```
app/src/main/java/com/lingji/app/
├── data/          # 数据层：网络、数据库、Repository
├── domain/        # 领域层：模型与业务逻辑
├── ui/            # UI 层：Screens、Components、Theme、ViewModel
└── MainActivity.kt

app/src/main/res/
├── font/          # 自定义字体
├── values/        # 字符串、颜色、主题
└── ...
```

## 许可证

本项目为 LingBook 项目的一部分。
