<p align="center">
  <img src="branding/logo.svg" width="112" height="112" alt="Kixyu Book Logo">
</p>

<h1 align="center">Kixyu Book</h1>

<p align="center">高级、安静、专注的 Android 本地小说阅读器</p>

<p align="center">
  <a href="https://github.com/kkyu9527/kixyubook/releases/latest"><img src="https://img.shields.io/github/v/release/kkyu9527/kixyubook?display_name=tag&amp;sort=semver&amp;label=Release" alt="Latest release"></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin 2.4"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://github.com/kkyu9527/kixyubook/releases/latest">下载</a> ·
  <a href="#功能">功能</a> ·
  <a href="#构建">构建</a> ·
  <a href="#架构">架构</a> ·
  <a href="#参与贡献">参与贡献</a>
</p>

Kixyu Book 是一款 local-first Android 小说阅读器，专注 TXT、EPUB、本地书库和沉浸阅读。应用没有在线书城、推荐流或广告，离线状态下所有核心阅读功能均可使用。

> [!NOTE]
> Google Drive 同步完全可选。未登录 Google 账号时，书籍、阅读进度与设置仍只保存在本机。

## 下载

前往 [GitHub Releases](https://github.com/kkyu9527/kixyubook/releases/latest) 下载最新 APK。应用支持 Android 8.0（API 26）及以上版本，并提供应用内更新检查。

当前提供 Android 版本，不提供 Google Play、Windows、iOS 或 Web 版本。请只从本仓库 Release 页面下载安装包。

## 功能

### 阅读

- 连续滚动与左右分页，支持点击、滑动和音量键翻页
- 跨章节连续翻页、分卷目录、书签和全文搜索
- 字号、行距、字距、页边距、自定义字体与阅读配色
- 日间、夜间、跟随系统和自定义主题
- 章节按需解析、相邻章节缓存与长章节分页优化

### 书库

- 通过 Android Storage Access Framework 批量导入本地书籍
- 使用永久 UUID 标识书籍，并通过内容 Hash 避免重复导入
- 列表与网格布局、搜索、分类、批量管理和阅读进度
- 最近打开、导入时间、书名与自定义拖动排序
- 支持隐藏分类及独立隐藏书架；隐藏书籍不会出现在首页的继续阅读中
- TXT 多编码识别
- EPUB metadata、目录、XHTML、图片和统一语义样式

### 首页

- 独立阅读仪表盘
- 继续阅读、最近阅读、每日目标和阅读统计集中呈现
- 手机、平板与折叠屏根据可用窗口宽度自适应排布

### 数据与同步

- 自动保存阅读位置和阅读时长
- 完整手动导出与恢复，包括原始书籍文件
- 可选 Google Drive 应用专属空间增量同步
- 支持同步书籍、进度、统计、书签、阅读与书架设置、阅读提醒、字体和原始文件
- 无法自动合并的书籍信息、书签或设置冲突由用户选择本机或云端版本
- 删除记录采用永久墓碑，避免已删除数据被其他设备重新恢复

### Android 体验

- Material 3 / MIUIX 双界面体系
- Material You 动态取色与自定义强调色
- Edge-to-Edge、透明手势导航区和 Predictive Back
- 高刷新率、Baseline Profile 与启动性能优化
- 手机、平板、折叠屏、横屏和多窗口自适应布局
- 平板横向阅读支持左右双页

## 支持格式

| 格式 | 支持情况 | 说明 |
| --- | --- | --- |
| TXT | 支持 | 自动识别 UTF 系列、GBK 等常见编码 |
| EPUB | 支持 | 支持 metadata、目录、XHTML 与图片；不支持 DRM 内容 |

目前不支持 PDF、Markdown、MOBI 和漫画格式。

## 隐私

- 本地阅读不需要账号或网络连接。
- 导入文件会复制到应用私有存储，不会修改来源文件。
- Google 同步仅在用户主动登录并授权后启用。
- 云端数据保存在 Google Drive 应用专属空间，Kixyu Book 不访问普通 Drive 文件。
- 手动备份由用户选择导出位置，恢复操作不会自动上传数据。

## 架构

项目采用 Kotlin、Jetpack Compose、Material 3、MIUIX、MVVM、Clean Architecture、Hilt、Coroutines、Room、DataStore、WorkManager 和 Navigation Compose。Feature 之间不直接依赖，共享能力统一放在 Core。

```text
kixyubook/
├─ app/                         # Application、导航、DI、更新与系统窗口
├─ core/
│  ├─ core-common/             # 领域模型与 Repository contract
│  ├─ core-ui/                 # 通用 UI 工具
│  ├─ core-designsystem/       # Material 3 / MIUIX 组件、主题与自适应尺寸
│  ├─ core-navigation/         # 路由定义
│  ├─ core-database/           # Room、导入、索引、统计与手动备份
│  ├─ core-datastore/          # 阅读、外观与书架偏好设置
│  ├─ core-reader-engine/      # Parser、Document Model、Layout 与 Pagination
│  └─ core-sync/               # Google 身份、Drive appData 与增量同步
├─ feature/
│  ├─ feature-home/            # 阅读仪表盘、继续阅读、目标与统计
│  ├─ feature-library/         # 书架、布局、排序、分类、隐藏与批量管理
│  ├─ feature-reader/          # 阅读器、目录、书签、搜索与阅读设置
│  └─ feature-settings/        # 外观、阅读、备份、同步与更新设置
├─ baselineprofile/            # 启动、进入阅读器与翻页的 Baseline Profile
└─ branding/                   # App Icon SVG 唯一源文件
```

阅读器 UI 不直接处理文件格式：

```text
File → Parser → Document Model → Layout / Pagination → Compose Renderer
```

原始文件按书籍独立保存；章节正文按需解析并写入派生缓存，不会一次性加载整本 EPUB。

## 大屏与多窗口

布局只依据当前应用窗口，不依赖设备型号：

| Window width | 布局 |
| --- | --- |
| `< 600dp` | Compact 手机布局 |
| `600–839dp` | Medium 大屏布局与 Navigation Rail |
| `≥ 840dp` | Expanded 布局；横向阅读支持双页 |

Manifest 允许 Activity resize，并声明 HyperOS 大屏和自由窗口能力。分屏、自由窗口或跨设备模式若只为应用分配手机宽度窗口，应用会按 Compact 模式显示。

## 构建

### 环境要求

- JDK 17 或更高版本
- Android SDK 37
- 支持当前 Android Gradle Plugin 的 Android Studio

### Debug

```powershell
git clone https://github.com/kkyu9527/kixyubook.git
cd kixyubook
.\gradlew.bat :app:assembleDebug testDebugUnitTest :app:lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Google 同步通过设备上的 Google Play services 完成账号选择和 Drive 授权。自行构建时，需要在 Google Cloud 启用 Drive API，并为包名 `com.kixyu9527.kixyubook` 及自己的 Debug / Release 签名 SHA-1 注册 Android OAuth Client。

### Release

签名信息必须保存在仓库外的 `%USERPROFILE%/.kixyubook/signing.properties`，也可以通过 `KIXYU_SIGNING_PROPERTIES` 指定：

```properties
storeFile=/absolute/path/to/keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

生成应用级 Baseline Profile：

```powershell
.\gradlew.bat :app:generateBaselineProfile
```

Profile 任务会在目标设备上安装和卸载测试 APK，建议使用专用设备或 Emulator。

## 参与贡献

欢迎通过 [Issues](https://github.com/kkyu9527/kixyubook/issues) 报告问题或提出建议。提交问题时请尽量附上 Android 版本、设备型号、应用版本、复现步骤和必要的日志或录屏。

如需提交代码，请先创建 Issue 说明目标，确保改动保持模块边界、复用 Design System，并同时验证 Material 3 与 MIUIX 两种界面风格。

## 许可证

Kixyu Book 基于 [MIT License](LICENSE) 开源。

Copyright © 2026 [kkyu9527](https://github.com/kkyu9527)
