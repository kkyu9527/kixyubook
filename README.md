# Kixyu Book

一款专注、安静、完全离线的 Android 本地小说阅读器。

Kixyu Book 使用 Kotlin、Jetpack Compose 与 Material 3 构建，兼顾 Apple Books 的精致、Kindle 的专注和现代 Android 的原生体验。应用不包含书城、登录、推荐或云同步，书籍与阅读数据始终由用户掌控。

## 当前能力

- 通过 Android Storage Access Framework 导入 TXT、EPUB，可一次选择多本书
- 使用永久 UUID 标识书籍，并通过文件 Hash 与 UUID 避免重复导入
- 首页展示继续阅读、最近阅读、今日阅读、总阅读时长、阅读字数与连续阅读天数
- 书库支持搜索、分类、删除、阅读进度和书籍管理
- 支持连续滚动与分页阅读；点击和滑动使用同一套跨章节翻页逻辑
- 章节目录使用 Material 3 Bottom Sheet，可直接跳转到章节开头
- 阅读页默认沉浸，提供目录、退出和阅读设置控制层
- 显示模式支持跟随系统、日间和夜间；自定义 Hex 阅读配色独立启用，并分别维护日间与夜间色板
- 支持 Android 12+ Material You 壁纸动态色，以及静谧青、雾海蓝、暮光紫、暖琥珀预设色板
- 支持字体大小、行高、字距、页边距，以及用户字体导入和管理
- 自动保存并恢复章节、阅读位置和阅读统计
- 支持完整备份与恢复，可迁移到另一台设备

## 内容与编辑规则

书籍不会使用文件路径或文件名作为身份。每本书在首次导入时获得永久 UUID；文件移动、完整备份恢复或再次导入后，阅读进度、统计与用户编辑仍可保持关联。

TXT 是可编辑格式。解析器优先使用 Android ICU 自动判断编码，并内置 UTF-8/16/32、GB18030/GBK/GB2312、Big5、Shift-JIS、EUC-JP、EUC-KR、Windows-125x、KOI8-R 等主流编码回退。书名、作者与简介会从正文中剥离；目录解析会将“卷 / 部 / 篇”作为分卷边界，将“章 / 节 / 回 / 话”作为实际章节，但不会在每个章节名前重复显示卷名。已导入的 TXT 可在书籍管理中选择“重新解析正文”，无需删除后重新导入。正文修改会直接写入 App 持有并参与备份的 TXT 原始文件，随后重新解析目录与正文，不再使用用户补丁表。

EPUB 是只读格式。书名、作者、简介、章节、XHTML、CSS 和图片资源均保持原样；应用只读取书内 metadata 与正文，并保存阅读进度和统计。

## Reader Engine

阅读内核位于 `core-reader-engine`，界面不直接依赖 TXT 或 EPUB：

```text
本地文件
   ↓
Parser（TXT / EPUB）
   ↓
统一 Document Model（Book / Chapter / Paragraph）
   ↓
Layout Engine
   ↓
Pagination + Position Manager
   ↓
Compose Reader Renderer
```

解析与排版按章节进行，不会一次性加载整本书；长内容使用 Lazy Layout 渲染。未来可在不重构阅读界面的前提下接入 Markdown、PDF 或漫画文档模型。

## 模块结构

```text
app                         应用装配、Hilt、导航、Edge-to-Edge

core/
├── core-common             领域模型、仓库契约、通用类型
├── core-ui                 跨功能 UI 组件
├── core-designsystem       颜色、排版、形状与统一主题
├── core-navigation         路由契约
├── core-database           Room、导入、统计、备份与字体
├── core-datastore          阅读偏好与设置持久化
└── core-reader-engine      文档模型、解析、排版、分页、定位与渲染

feature/
├── feature-home            继续阅读、最近阅读、统计与目标
├── feature-library         书库、导入、搜索、分类与管理
├── feature-reader          沉浸阅读、目录、翻页与实时预览
└── feature-settings        设置、外观、字体与完整备份恢复
```

Feature 模块之间不互相依赖，公共能力统一下沉到 Core；所有页面共享同一套 Design System。

## Android 原生体验

- Activity 使用 `enableEdgeToEdge()`，状态栏与导航栏透明
- 页面只保留 `safeDrawing` 的水平 Insets，列表底部为手势导航区域预留系统 Insets
- 首页、书库和设置使用 `LargeTopAppBar`；外观等二级页面使用 `MediumTopAppBar`
- 顶栏由 Material 3 `exitUntilCollapsedScrollBehavior` 原生驱动：向上滚动收缩为固定小标题栏，反向滚动自然展开
- 阅读页的返回优先级为 Bottom Sheet → 设置浮层 → 阅读控制层 → 退出阅读页
- 页面导航与返回使用连续的 Material Motion 动画，并支持 Android 14 Predictive Back

## 备份与恢复

`.kixyubackup` 完整备份默认包含：

- 原始 TXT、EPUB 与封面资源
- 书籍 metadata 与永久 UUID
- 阅读进度、阅读时长、阅读字数和连续阅读记录
- 用户直接修改后的 TXT 原始文件
- 阅读设置、自定义主题与用户字体

恢复过程会重建全部关联数据，以便在另一台设备上继续阅读。敏感文件不会上传到远程服务。

## 品牌与图标

`branding/` 是图标的唯一矢量源：

- `logo.svg`：1024 × 1024 全彩图标
- `foreground.svg`：Android Adaptive Icon 前景
- `monochrome.svg`：Android 13 主题图标单色前景

图标采用低饱和 Material You 背景、占画布 50% 的中心圆形 Surface，以及由两条抽象书页曲线和一道光线组成的极简符号。在圆形、圆角矩形和方形遮罩下均保留充足留白；Android VectorDrawable 由这些 SVG 源同步维护。

## 开发与验证

环境要求：Android Studio、JDK 17，以及项目所声明版本的 Android SDK。

```powershell
# 编译调试包
.\gradlew.bat :app:assembleDebug

# 运行 JVM 单元测试
.\gradlew.bat testDebugUnitTest

# 编译数据库迁移设备测试
.\gradlew.bat :core:core-database:assembleDebugAndroidTest

# 有模拟器或设备时执行迁移测试
.\gradlew.bat :core:core-database:connectedDebugAndroidTest

# 运行 Android Lint
.\gradlew.bat :app:lintDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## Release 签名

签名文件与密码独立保存在仓库之外。Gradle 默认读取 `%USERPROFILE%/.kixyubook/signing.properties`，也可通过 `KIXYU_SIGNING_PROPERTIES` 指向其他配置文件：

```properties
storeFile=C:/secure/location/release.jks
storePassword=请勿提交到仓库
keyAlias=kixyubook
keyPassword=请勿提交到仓库
```

生成签名 APK 与 AAB：

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

输出位置：

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## 隐私原则

Kixyu Book 只做本地阅读。应用不需要账号，不提供在线书城，不分析阅读偏好，也不依赖第三方阅读引擎作为核心。除非用户主动导出备份，书籍、字体、编辑内容和阅读记录都只保存在设备上。
