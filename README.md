# Kixyu Book

Kixyu Book 是一款完全离线的 Android 本地小说阅读器，使用 Kotlin、Jetpack Compose 与 Material 3 构建。应用不提供书城、账号、推荐或云同步，书籍和阅读数据均由用户掌控。

## 功能

- 通过 Storage Access Framework 导入 TXT、EPUB，并检测重复文件
- 支持分页、连续滚动、跨章节翻页、目录、书签与全文搜索
- 自动识别常见 TXT 编码；TXT 可编辑，EPUB 保持只读
- 支持阅读进度、阅读统计、字体导入、Material You 与自定义阅读配色
- 支持完整备份和跨设备恢复，包括原始书籍、设置、字体与阅读数据
- 支持 Edge-to-Edge、Material Motion 和 Android 14 Predictive Back

## 架构

项目采用 MVVM、Clean Architecture、Hilt、Coroutines、Room、DataStore 和 Navigation Compose。Feature 之间互不依赖，共享能力统一位于 Core。

```text
app
core/
  core-common · core-ui · core-designsystem · core-navigation
  core-database · core-datastore · core-reader-engine
feature/
  feature-home · feature-library · feature-reader · feature-settings
```

阅读界面不直接处理文件格式：

```text
File → TXT/EPUB Parser → Document Model → Layout/Pagination → Compose Renderer
```

内容按章节读取并使用 Lazy Layout 渲染；`core-reader-engine` 为 Markdown、PDF 和漫画等后续格式预留扩展边界。

## 构建

需要 Android Studio、JDK 17 和项目声明版本的 Android SDK。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Release 签名从仓库外的 `%USERPROFILE%/.kixyubook/signing.properties` 读取，也可通过 `KIXYU_SIGNING_PROPERTIES` 指定路径。生成发布产物：

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

`branding/` 保存 App Icon 的 SVG 唯一源文件。除非用户主动导出备份，应用不会上传书籍、字体或阅读记录。
