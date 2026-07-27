# Kixyu Book

Kixyu Book 是一款完全离线的 Android 本地小说阅读器。应用不包含书城、账号、推荐或云同步，书籍和阅读数据始终由用户掌控。

## 主要能力

- 通过 Storage Access Framework 导入 TXT、EPUB，并按文件哈希去重
- TXT 多编码识别与原文编辑；EPUB 元数据、目录、XHTML 和图片读取
- 精确分页、连续滚动、跨章节翻页、目录、书签和全文搜索
- 阅读进度与统计、字体导入、完整备份和跨设备恢复
- Material 3 / MIUIX 界面风格、Material You 动态取色和自定义阅读配色
- Edge-to-Edge、高刷新率、手势导航与 Predictive Back

## 工程结构

项目使用 Kotlin、Jetpack Compose、MVVM、Clean Architecture、Hilt、Coroutines、Room、DataStore 和 Navigation Compose。Feature 之间不直接依赖，公共能力由 Core 提供。

```text
app
core/
  core-common  core-ui  core-designsystem  core-navigation
  core-database  core-datastore  core-reader-engine
feature/
  feature-home  feature-library  feature-reader  feature-settings
```

阅读链路与文件格式解耦，并按章节加载：

```text
File → Parser → Document Model → Measured Layout/Pagination → Compose Renderer
```

## 构建与发布

需要 JDK 17、Android SDK 37 和支持当前 Android Gradle Plugin 的 Android Studio。

```powershell
.\gradlew.bat :app:assembleDebug testDebugUnitTest :app:lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Release 签名信息保存在仓库外的 `%USERPROFILE%/.kixyubook/signing.properties`，也可通过 `KIXYU_SIGNING_PROPERTIES` 指定。文件包含 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

`branding/` 是 App Icon 的 SVG 唯一源文件。当前开发版本不保留旧数据库迁移链，数据结构不兼容时会重建本地数据库；重要数据请先导出完整备份。
