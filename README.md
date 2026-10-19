# Sticker Kaleidoscope

Windows 优先的跨端表情管理器，使用 Kotlin + Compose Multiplatform。

当前已实现本地表情库：文件/文件夹批量导入、哈希去重、静态缩略图、标题与标签编辑、搜索、收藏，以及可恢复的回收站。同步与 AI 尚未实现。

已完成、部分完成、待验收及未完成的完整清单见 [功能状态文档](docs/feature-status.md)。

左右分隔条支持拖拽调整侧栏宽度，悬停显示调整光标与高亮；宽度目前仅在本次运行内保留。
顶部布局开关可以分别显示或隐藏分类、表情和属性栏，至少保留一栏；恢复侧栏时保留本次运行中调整的宽度。
导航、搜索、空库和属性区域使用 Compose Material 预设图标。
网格支持点击复制、Ctrl/Shift 多选、空白区域拖动框选与右键批量操作。
框选以完整图库的内容坐标计算，按住左键滚动时保留选区中的离屏项，反向滚动可缩小选区。
可将图片或文件夹拖入窗口导入；静态图复制提供图片和原文件，GIF 保留原始动图数据，多张复制为文件列表。
界面文案使用 Compose Resources，提供中文和英文，跟随系统语言；其他语言回退英文。参见 [多语言约定](docs/localization.md)。

## Windows 开发

安装 JDK 21，或直接使用 Android Studio 自带的 JBR 21。在 PowerShell 中执行：

```powershell
# 编译桌面端
.\scripts\dev.ps1 :composeApp:desktopMainClasses
# 启动桌面端
.\scripts\dev.ps1
```

脚本默认使用 `C:\Program Files\Android\Android Studio\jbr`，只在执行期间设置 JAVA_HOME，不修改系统配置。
自定义路径可使用 `-JdkHome '你的 JDK 21 路径'` 或环境变量 `STICKER_JAVA_HOME`。
Android Studio 中打开项目根目录，并将 Gradle JDK 设为相同的 JBR 21。
若用户级 Gradle 配置了未运行的代理，可加 `-DirectNetwork` 临时直连；不修改全局代理配置。

Android Studio 的 JBR 可能不包含 `jpackage`，仅编译与开发运行不受影响。
生成独立应用目录时，使用包含 `jpackage` 的完整 JDK：

```powershell
.\scripts\dev.ps1 -DirectNetwork '-PpackagingJdkHome=C:\Program Files\Java\jdk-25' :composeApp:createDistributable
```

此参数只指定应用打包工具及其运行时，Gradle 和 Kotlin 编译仍使用 JBR/JDK 21。

工程暂以 Kotlin 2.1.20、Compose 1.8.2、Gradle 8.13 作为可复现基线，并非追踪最新版本。
首次构建需要下载依赖。共享界面位于 `commonMain`，Windows 入口位于 `desktopMain`；Android 目标稍后接入。

## 已确定的方向

- Windows 是主要使用端：批量导入、整理、搜索、AI 分类、复制和拖拽发送。
- Android 是伴侣端：浏览、搜索、分享导入与分享发送。
- 两端共享设计系统，布局与输入交互分别适配。
- 本地优先，跨端同步为必要能力，使用自有服务器。
- AI 整理由 PC 发起，人工确认的数据优先于 AI 建议。

## 技术决策

- 客户端：Kotlin + Compose Multiplatform。
- Windows 桌面目标使用 JVM；Android 使用 Jetpack Compose。
- 本地存储：SQLite，Windows 实现使用 JDBC；共享业务通过 Repository 接口访问。
- 服务端与 AI Provider 暂不锁定，通过明确的协议接口解耦。
- Windows 原生能力优先使用现有可靠接口，必要时增加少量原生桥接。

## 首个里程碑

实现并验证 Windows 桌面原型：紧凑三栏布局、缩略图网格、预览、托盘、全局快捷键，以及 QQ/微信的复制和拖拽兼容性。

参见 [开发计划](docs/plan.md)。

## 本地图库与验证

默认存储在 `%LOCALAPPDATA%\StickerKaleidoscope\library`；可使用 `STICKER_DATA_DIR` 指定独立目录。
显示名称中的空格不改变内部存储标识，后续改名不会迁移或丢失现有图库。

- 原图复制到图库后保持字节不变，删除源文件不影响图库。
- 支持 PNG/JPEG/GIF/WebP，GIF/WebP 在网格中使用静态封面。
- 文件夹递归导入，不跟随符号链接；单个失败不阻断其他文件。
- 限制单文件 50 MiB、单边 16,384 像素、总像素 6,400 万；解码前检查尺寸并采样。
- 搜索标题、原文件名与标签；「未分类」目前指未打标签的表情。
- 删除仅写入回收站状态，保留原图；重复导入不会悄悄恢复回收站中的表情。
- 元数据变更和本地变更日志在同一事务内提交。日志为同步打基础，当前不执行远端同步。
- 导入文件先落暂存区；异常中断可能留下未引用素材，后续维护功能再做清理，不自动删除原图。

```powershell
.\scripts\dev.ps1 -DirectNetwork :composeApp:desktopTest
```

新打包的程序位于 `composeApp/build/compose/binaries/main/app/Sticker Kaleidoscope/Sticker Kaleidoscope.exe`。

## 真实样本验证

```powershell
# 默认使用 Windows 已知文件夹“图片”下的 piggy，兼容图片目录重定向。
.\scripts\validate-samples.ps1 -DirectNetwork
# 或指定已授权读取的样本目录。
.\scripts\validate-samples.ps1 -SourceDirectory 'D:\your-samples' -DirectNetwork
```

脚本在 `artifacts` 下创建全新测试图库，并验证导入、重启、全部缩略图、原图哈希、重复导入、标签搜索、收藏和回收站。
前后核对源文件列表与哈希，既不修改源目录，也不写入默认正式图库。
未显式设置样本路径时，常规测试会跳过这项真实文件测试。样本原图和验证报告不纳入 Git。
