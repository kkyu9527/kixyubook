# 不连接设备的自动化回归

在项目根目录运行：

```bash
./gradlew verifyHostTests --continue
```

这条任务只在电脑上的 JVM 中执行测试、截图比对和静态检查，不启动 Android Emulator，
不通过 ADB 安装应用，不生成 Baseline Profile，也不连接 Google Drive。首次运行可能需要
下载 Gradle/Maven/Robolectric 依赖；“不连接设备”不等于首次运行不需要网络。

## 覆盖范围

| 检查 | 实际执行方式 | 主要断言 |
| --- | --- | --- |
| EPUB/TXT 解析与分页 | 现有 reader-engine 单元测试 | 目录、正文、长行、图片、定位、缓存和跨章边界 |
| 全文搜索状态 | `ReaderSearchControllerTest`，受控搜索仓库 | 即时结果、阶段进度、增量去重、排序后选择稳定、取消、失败重试、返回原位置 |
| 搜索界面 | `ReaderSearchOverlayTest`，真实 Compose 弹层及控制器 | 两套主题下输入、点击搜索箭头、进度文字更新、点击结果、返回、键盘提交、无匹配结果、关闭 |
| 长按正文 | `ReaderSelectionInteractionTest`，真实正文渲染器和工具栏 | 两套主题下长按、标注子菜单、返回菜单、高亮回调、外部点击清除选区且不触发翻页 |
| 数据落盘 | `ReaderPersistenceTest`，真实文件 Room 数据库和批注仓库 | 关闭重开后书签、批注、笔记、进度及可搜索的正文保留，旧进度不覆盖新进度，删批注不改正文；搜索扫描另由 `BookSearchScannerTest` 覆盖，不依赖 FTS 镜像 |
| 旧 EPUB 目录补齐 | `EpubDirectoryUpgradeTest` | 章节身份、阅读进度、书签、批注、笔记及正文段落不丢失 |
| 连续翻页 | `ReaderPagerHandoffTest`、`ReaderPagedContentHandoffTest` | 页面窗口重排、图片首屏、缓存清理、连续正反翻页及显式正文定位 |
| 阅读恢复 | `ReaderRestorationTest`、`ReaderProgressWriterTest`、`ReaderViewModelRecoveryTest`、`ReaderReflowIntegrationTest`、`ReaderRotationReflowTest` | 保存状态重建、锁屏检查点重试、改字号真实重排、横竖屏切换、EPUB/TXT 重开后保留章节及字符位置 |
| 长段落恢复 | `ReaderAnchorReadinessTest` | 部分分页须到达保存的字符位置，图片不冒充正文锚点，空白间隙和过期锚点不阻塞打开 |
| 截图基线 | `KixyuDualThemeScreenshotTest`，Roborazzi | Material/MIUIX 操作栏：手机浅色、手机深色、平板浅色，共 6 张 |
| 代码规范 | Lint、资源、Edge-to-Edge 检查 | 编译期可检查的错误和项目容器规范 |
| 平板书库 | `LibraryFiltersLayoutTest`、`LibraryScreenshotTest` | 有界平板布局不会被筛选栏占满、书籍实际可见、分类可点击，覆盖双主题与横竖屏 |
| 磨砂材质 | `KixyuGlassLegibilityTest`、`KixyuUnifiedGlassTest`、`KixyuGlassControlsTest` | 工具栏和内容层过滤高频背景、保留大块背景颜色、全局 0–100% 实际改变渲染、两套组件共享材质 |
| 阅读配色 | `ReaderPaletteTest`、`ReaderThemeRestorationTest`、`ReaderPalettePersistenceTest`、`CloudReaderSettingsJsonTest` | 预设明暗对比度、动态取色、改配色不重排或丢字符位置、原自定义配置迁移、持久化及备份/同步往返 |
| 国际化 | `verifyTranslations`、`KixyuLocalizationTest`、`ReaderLocalizationTest`、诊断格式测试 | 四语言资源完整性、参数/复数、中文地区回退、双主题控件切换语言、日志分类不受语言影响 |
| 返回动画 | `KixyuBackMotionTest`、`KixyuPredictiveBackTest`，派发真实 Android 返回回调 | 线性手势、取消/完成、按钮返回无阴影、开关无位移、弹窗不瞬间结束、绘制层更新与重开复位、双主题嵌套弹层和慢速退出互不干扰 |
| 日志会话 | `DiagnosticLogSessionTest` | 分类复用快照、清空后的全局失效、语言切换重新格式化、取消解析后可重新加载 |
| 恢复回滚 | `RestoreJournalTest` | 安装中断、回滚再次中断、提交不可逆转、暂存阶段新 WAL 不被删除 |
| 数据与同步登记 | `AtomicReaderMutationTest`、`SettingsMutationJournalTest` | Room 写入/outbox 共同回滚、设置持久化投递与重放、组合书籍编辑原子性 |
| 统一编辑行为 | `UserOperationControllerTest`、`AnnotationOperationUiTest`、`ReaderActionLayoutTest` | 失败草稿、重试、删除确认、防双击、双主题四语言 2 倍字体按钮 |
| 缓存预算 | `ReaderCacheBudgetTest` | 字节 LRU、超预算不留存、磁盘预算不删除原书、低内存插图预算 |
| 定位链路 | `ReaderLocationRequestTest`、`ReaderJourneyTrackerTest`、`ReaderViewModelRecoveryTest` | 字符锚点、目录/批注/书签/搜索/历史串联、TXT/EPUB 会话重建、ready 回调耗时归属 |
| 云端书架与按需阅读 | `MetadataOnlyBookSyncTest`、`SyncedBookStoreTest`、`ReaderCloudOpenTest`、`CloudBookOfflineOpenTest` | 先显示书架条目、正文按需安装、恢复位置后呈现、失败重试、下载中删除不复活、已下载书籍离线打开不等待同步锁 |
| 同步重试与让位 | `CloudTombstoneReplayTest`、`DriveAuthorizationRetryTest`、`CloudForegroundRequestsTest`、`RemoteDeferredApplyTest` | 无关历史删除不下载、成功对象单独检查点、有限并发、401 只刷新重试一次、前台阅读优先、延迟到达的数据不丢失 |

顶层退出测试验证应用已释放返回回调、允许 Android 接管回到桌面的动画；电脑上的测试
不绘制系统 Launcher，因此不能据此声称已验证特定 ROM 的桌面缩放、系统合成或帧率。

搜索测试只替换仓库的异步搜索输入，不替换界面点击或控制器。未实现的仓库方法会直接失败，
避免测试悄悄绕开真正需要覆盖的调用。数据库测试只使用临时目录，不访问个人书库。

### 明确不证明的内容

- 数据库关闭重开测试不是 Android 进程死亡、Activity 恢复的完整模拟。
- 阅读恢复测试分别模拟 Compose 保存状态重建和真实 ReaderViewModel 销毁/新建，使用受控 I/O 边界；不声称复现系统锁屏时序、厂商杀进程或突然断电。
- 搜索点击测试验证到控制器发出的章节/段落位置；新增真实 ReaderViewModel 的跳转/历史/会话重建串联，尚未把屏幕点击、导航宿主和全部持久化边界组合成一个端到端真机测试。
- Robolectric 的 API 35 放大镜缺少 compositor Surface。`HostMagnifierShadow` 仅替代系统
  放大镜绘制；不替代选择算法、工具栏或 App 业务逻辑，因此不验证放大镜像素及厂商效果。
- 截图覆盖公共组件的稳定底色、布局与玻璃背景；`KixyuUnifiedGlassTest` 按 CONTROL / CONTENT 两种语义用途比较两套主题、明暗模式下公共入口的像素，防止同用途材质分叉。这不代表真机 GPU 折射效果、全部屏幕或动画帧率。
- `KixyuGlassLegibilityTest` 用高频黑白背景验证小控件中央保留细节、静止边缘确实折射背景、内容面板压制干扰及按钮可点击；`KixyuGlassTest` 验证共用折射几何、内容底色对比度和背景采样预算。这些是宿主渲染回归，不代替真机视觉与帧率验收。
- `verifyGlassStyleContracts` 在构建阶段阻止业务组件直接使用底层玻璃或单独覆盖模糊、折射与材质；`KixyuGlassSamplingTest` 验证背景采样的同源循环与跨窗口边界。
- Lint 和 Edge-to-Edge 规则不能穷尽所有视觉遮挡；系统手势、系统栏、厂商 ROM、真实云端同步仍不在这套检查范围。
- 云同步用例使用本地 Room、受控下载结果与授权回调，不读取 Google 账号或真实云盘，也不能证明真实网络速度、Google Play 授权弹窗或弱网下的厂商后台行为。

## 快速定位失败

### EPUB 本轮新增链路

| 用例 | 断言边界 |
| --- | --- |
| `ReaderEpubNavigationIntegrationTest` | 合成真实 EPUB → 真实解析器 → ViewModel；目录、前后章节按钮、双向连续翻页跳过连续纯标题页；旧书签、搜索、批注字符坐标及持久化重开不变 |
| `EpubStructureCompatibilityTest` | EPUB 2 NCX / EPUB 3 TOC、DTD、插图、卷扉页、锚点、CSS 背景页；不是按书名或文件名写例外 |
| `ReaderThemeRestorationTest` | 两套主题的真实翻页、系统明暗变化、阅读子树重建；断言实际沉降字符，不只检查设置状态 |
| `ReaderImageContrastIntegrationTest` | 两套主题、真实 ZIP/PNG 解码及原生像素；深色页保留透明背景、单色墨迹柔和提亮、浅色还原；全屏预取后显示和切主题不重复解码 |
| `ReaderImageLifetimeTest` | 两套主题、真实 Pager 拖动中间帧及跨章；回收缓存后保留实际图片像素，不重解码；选词开关反复切换、图片资源替换、加载中不闪 alt、失败仍有回退 |
| `ReaderRapidNavigationTest` | 两套主题、平移/覆盖与 Pad 双页；滑动中收到切章命令必须在沉降后执行；无逐次空闲等待的前后切章突发不丢目标、不积压动画 |
| `ReaderReadAheadReuseTest` | 前台跳转不重启同章必要预读；窗口轮换保留第二邻章任务；连续交替切章不重复请求已准备正文 |
| `ReaderPaginationFairnessTest` / `ReaderPagerHandoffTest` | 真实测量在页边界让出执行机会；进入未完成章节并逐批追加时，实际 Pager 不跳回首屏或旧叶片 |
| `ReaderAnnotationIndexTest` | 一万条批注的重复渲染查询不再扫描全书列表；保留笔记/下划线、跨页裁剪及纠错后的原文校验 |
| `EpubImageThemeTest` / `EpubImagePrefetchTest` | 单色/彩色/纹理/不透明负例；混合透明图的柔和底衬；全屏物理视口与小数密度取整一致 |
| `EpubNavigationPerformanceTest` / `EpubPresentationCacheTest` | 3000 个独立章节零正文探测；跨内存会话重开不再调解析器，换源/损坏/写缓存失败不误用旧目录、不阻止阅读 |
| `EpubSvgSupportTest` | 原生 SVG 真像素、行内顺序、外部引用/脚本/实体/循环拒绝及分配预算 |
| `BrowserMessageTest` / `BrowserSecurityTest` | 原生坐标校验、跨段复制内容重建、个人纠错反投影及实际 WebSettings 安全策略 |
| `ReaderSelectionInteractionTest` | 原生/浏览器共用菜单，双主题真实点击高亮与复制、清理选区；不只检查按钮可见 |

### 浏览器专用测试（无真机）

复杂 EPUB 的可信脚本由真实无头 Chromium 执行，不用 Robolectric 的 WebView
空实现代替浏览器布局。需要 Node.js 20+ 和 Chromium：

```bash
cd feature/feature-reader/src/test/browser
npm ci --ignore-scripts --no-audit --no-fund
npx playwright install chromium
npm test
```

已安装 Chrome 时可跳过下载，通过 `KIXYU_CHROME_PATH` 指定浏览器可执行文件。
该套覆盖字符级重排/主题恢复、原文搜索与批注范围、纠错不删插图、黑画对比度、
固定版式几何、预加载隔离、RTL/竖排双向翻页、全选跨段边界、异步跳转竞态和快速连翻。
它由独立的 `Reader browser contracts` 工作流执行，**不包含在 Gradle 的
`verifyHostTests` 内**；改动浏览器后端时两套均需通过。Chromium 测试不代替 Android
WebView 真机生命周期、厂商行为、手柄触摸或帧率验收。

本地用户提供的完整电子书只作为额外实书审计，不进入 Git 或 CI；对应结构必须同时
缩减成可公开的合成 fixture，避免本地大样本通过、公共回归却覆盖不到问题。

```bash
# 只运行搜索和长按操作链路
./gradlew :feature:feature-reader:testDebugUnitTest --tests '*ReaderSearch*Test' --tests '*ReaderSelectionInteractionTest'

# 只运行数据持久化
./gradlew :core:core-database:testDebugUnitTest --tests '*ReaderPersistenceTest'

# 只比对界面，不更新基线
./gradlew :core:core-designsystem:verifyRoborazziDebug
```

每个模块的 `build/reports/tests/testDebugUnitTest/index.html` 提供可读报告，
`build/test-results/testDebugUnitTest/` 保存 JUnit XML。Lint 报告位于模块 `build/reports/`。
截图差异以 Roborazzi 失败日志给出的路径为准。

截图失败应先检查差异。只有确认视觉变化符合预期，才对指定测试运行 `recordRoborazziDebug --tests ...`，
人工查看新图片并提交新基线；不要通过全量重录来掩盖回归。

## CI

普通 push/PR 使用相同的 `verifyHostTests` 命令，并保留原有编译、Release 和 Compose 指标检查。
报告会作为 `host-regression-reports` 上传。现有数据库模拟器测试没有删除，只改为手动触发工作流时
勾选 `run_device_tests` 才运行，默认关闭。
