# Kixyu Book

Kixyu Book 是一款 local-first 的 Android 小说阅读器，专注 TXT / EPUB、本地书库和沉浸阅读。没有在线书城、推荐流或广告；离线状态下所有核心功能均可使用。

## 功能

- SAF 批量导入 TXT、EPUB，使用内容 Hash 避免重复导入
- TXT 多编码识别；EPUB metadata、目录、XHTML、图片与统一语义样式
- 连续滚动、左右分页、跨章节翻页、目录分卷、书签和全文搜索
- 阅读进度、阅读时长统计、自定义字体、完整手动备份与恢复
- Material 3 / MIUIX 双界面体系，支持 Material You 与自定义阅读配色
- Edge-to-Edge、透明手势导航区、Predictive Back 和高刷新率
- 手机、平板、折叠屏、多窗口与横屏双页阅读

## 架构

项目采用 Kotlin、Jetpack Compose、Material 3、MIUIX、MVVM、Clean Architecture、Hilt、Coroutines、Room、DataStore、WorkManager 和 Navigation Compose。Feature 之间不直接依赖，共享能力统一放在 Core。

```text
kixyubook/
├─ app/                         # Application、导航、DI、更新与系统窗口
├─ core/
│  ├─ core-common/             # 领域模型、Repository contract
│  ├─ core-ui/                 # 通用 UI 工具
│  ├─ core-designsystem/       # Material 3 / MIUIX 组件、主题、尺寸与自适应布局
│  ├─ core-navigation/         # 路由定义
│  ├─ core-database/           # Room、书籍文件、导入、索引、统计与手动备份
│  ├─ core-datastore/          # 阅读与外观设置
│  ├─ core-reader-engine/      # Parser、Document Model、Layout、Pagination、Renderer
│  └─ core-sync/               # Google 身份、Drive appData 与增量同步
├─ feature/
│  ├─ feature-home/            # 继续阅读、最近阅读与统计
│  ├─ feature-library/         # 书库、搜索、分类、导入与批量删除
│  ├─ feature-reader/          # 阅读器、目录、书签、搜索与阅读设置
│  └─ feature-settings/        # 外观、阅读、备份、同步与更新设置
├─ baselineprofile/            # 启动、进入阅读器与翻页的 Baseline Profile
└─ branding/                   # App Icon SVG 唯一源文件
```

阅读器不直接处理 TXT / EPUB：

```text
File → Parser → Document Model → Layout / Pagination → Compose Renderer
```

原始文件按书籍保存；章节正文按需解析并写入派生缓存，不会一次加载整本 EPUB。

## 大屏与多窗口

- Manifest 显式允许 Activity resize，声明 HyperOS `embedded` 大屏能力，并启用 `CVW_MODE=1` 双向无极窗口。
- 布局依据当前应用窗口而不是设备型号：`< 600dp` 为 Compact，`600–839dp` 为 Medium，`≥ 840dp` 为 Expanded。
- Medium / Expanded 使用 NavigationRail；阅读器在横向且窗口宽度 `≥ 840dp` 时显示左右双页。
- 分屏、自由窗口和小米互联若只分配手机宽度窗口，会按 Compact 模式显示，这是窗口自适应的预期行为。

## 构建

需要 JDK 17、Android SDK 37 和支持当前 Android Gradle Plugin 的 Android Studio。

```powershell
.\gradlew.bat :app:assembleDebug testDebugUnitTest :app:lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Google 同步通过设备上的 Google Play services 完成账号选择和 Drive 授权，不需要在本地写入 Web Client ID。发布前仍需在 Google Cloud 启用 Drive API，并使用应用包名以及 Debug / Release 签名的 SHA-1 分别注册 Android OAuth Client。

## Release

签名信息保存在仓库外的 `%USERPROFILE%/.kixyubook/signing.properties`，也可通过 `KIXYU_SIGNING_PROPERTIES` 指定。文件包含 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

应用级 Baseline Profile：

```powershell
.\gradlew.bat :app:generateBaselineProfile
```

Profile 生成任务会安装和卸载测试 APK，请使用专用设备或 Emulator。当前开发分支不保留旧数据库迁移链；数据结构不兼容时会重建本地数据库，重要数据请先导出完整备份。
