# 翻译助手 · YYZS

面向 Android 的屏幕翻译与语音助手。手机本地识别屏幕文字，调用你配置的模型翻译，再将译文绘制回截图中的对应位置。

当前源码版本：**1.0.1**。采用 Kotlin、Jetpack Compose 和 ML Kit。

> 源码可用：允许实质改进后的商业开发，限制没有实质改进的套壳收费。详见 [LICENSE](LICENSE) 和 [许可示例](docs/LICENSING.md)。本项目不采用“全面禁止商用”的许可证。

## 功能

- **屏幕翻译**：本地 OCR、段落重组、断行合并、译文原位绘制；复制、分享、保存及切换原文。
- **快速 / 深度翻译**：快速模式发送识别文字；深度模式额外发送截图和坐标，让支持图片的模型结合画面处理。
- **流式 / 一次性显示**：设置 → 翻译 → 流式输出，默认开启；关闭后等全部结果完成再一次显示。该开关控制屏幕译文呈现方式。
- **文本和语音输入**：输入“翻译”或“屏幕翻译”触发屏幕翻译，也可以直接输入待译文字。
- **助手面板和聊天记录**：文本对话、语音输入、回复朗读和历史会话。
- **手机工具**：打开应用、打开系统设置、调整媒体音量、设置闹钟 / 倒计时、导航和打开拨号界面；实际支持取决于设备和权限。
- **可配置接口**：自行填写模型服务地址、模型名和 API Key。文本服务按 Chat Completions 风格接口调用；不同服务商的扩展参数和工具调用兼容性需要验证。
- **实验性实时语音**：包含可配置 WebSocket 语音会话实现，需要与当前客户端协议兼容的服务。它不是后台唤醒词功能。

## 开始使用

1. 安装自行构建的 APK，进入设置 → 模型接口，填写自己的服务地址、模型名和 API Key，保存并测试连接。
2. 进入设置 → 唤醒方式，开启“翻译助手 屏幕截图”无障碍服务。
3. 在系统快捷设置面板添加“屏幕翻译”磁贴；也可设置为默认数字助理，使用系统提供的助手入口。
4. 打开待译页面，触发翻译；在设置 → 翻译中选择目标语言、自动翻译和输出方式。

应用最低支持 Android 10（API 29）；**无障碍截图翻译需要 Android 11（API 30）及以上**。当前构建只包含 `arm64-v8a`。

长按电源键、默认数字助理和无障碍服务能否持续生效，取决于手机系统。部分厂商会覆盖默认助手或限制后台服务；快捷设置磁贴可作为另一入口。受系统保护的窗口可能无法截图。

## 数据如何流转

| 功能 | 本机处理 | 可能发送到外部服务的数据 |
| --- | --- | --- |
| 快速屏幕翻译 | 截图、OCR、排版绘制 | OCR 文字及翻译要求，发送给配置的模型服务 |
| 深度屏幕翻译 | 截图、OCR、排版绘制 | OCR 文字、区域坐标及缩放后的截图，发送给配置的模型服务 |
| 助手对话 | 历史记录和工具执行 | 对话内容、工具结果及用于匹配应用的已安装应用名称等上下文 |
| 系统语音识别 | 由选定的识别服务处理 | 是否联网及发送何种音频，取决于所选识别服务 |
| 实时语音 | 麦克风采集、音频播放 | 会话期间的音频及必要会话数据，发送给配置的语音服务 |
| 网页搜索和阅读 | 结果解析 | 查询发送给搜索服务；部分页面读取会使用 Jina Reader |

API Key 保存在手机应用私有设置中，不写入本仓库。应用关闭了系统自动备份。截图 OCR 可在本机进行，**当前模型翻译仍依赖所配置服务，不能将整个应用视为完全离线翻译软件**。

## 构建

需要兼容本项目的 Android Studio、JDK 17 或更高版本，以及 Android SDK Platform 36。项目使用 Gradle Wrapper；依赖版本见 `gradle/libs.versions.toml`。

```bash
git clone https://github.com/linlanplus/yyzs.git
cd yyzs
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

通过 Android Studio 配置 SDK 路径，或在本机创建 `local.properties`。该文件不提交。

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Debug 应用包名：`yyzs.lqkj.uk.ocrpreview`，可与正式版并行安装。
- Release 应用包名：`yyzs.lqkj.uk`

### 发布签名

复制 `keystore.properties.example` 为本机的 `keystore.properties`，填写自己的签名信息，将密钥库放到被忽略的 `keystore/` 目录，再运行：

```powershell
.\gradlew.bat :app:assembleRelease
```

目前构建脚本在缺少发布签名配置时会退回 Debug 签名；正式分发请使用自己的发布签名。自建 APK 与原作者 APK 的签名不同，通常不能直接覆盖原作者版本。

### 检查与回归

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`app/src/androidTest` 包含真机 OCR、段落归并、擦除与排版、翻译结果 ID 校验及两种输出模式的回归用例。编译测试 APK 不等于已经在设备上执行测试。

当前 `UpdateChecker` 的仓库配置留空，应用内自动检查更新尚未启用；源码发布不自动生成 APK Release。可在需要发布安装包时配置仓库并完善更新流程。

## 目录

| 路径 | 内容 |
| --- | --- |
| `app/src/main/java/yyzs/lqkj/uk/ocr` | 本地 OCR 和段落重组 |
| `app/src/main/java/yyzs/lqkj/uk/render` | 原文字形擦除与译文排版 |
| `app/src/main/java/yyzs/lqkj/uk/translate` | 模型请求和翻译结果协议 |
| `app/src/main/java/yyzs/lqkj/uk/assist` | 默认助手、无障碍截图和快捷磁贴 |
| `app/src/main/java/yyzs/lqkj/uk/agent` | 对话和手机工具 |
| `app/src/main/java/yyzs/lqkj/uk/live` | 实验性实时语音 |
| `app/src/main/java/yyzs/lqkj/uk/ui` | 助手面板和翻译结果界面 |

## 许可与贡献

原创代码和原创资源采用 [翻译助手源码使用许可证 1.0](LICENSE)，属于 **Source Available**，并非 OSI 批准的开源许可证。

允许个人和企业使用、免费分享、实质改进后商业发布，以及将部分代码整合进自行开发的产品。改进后的产品仍以翻译为核心，或与本项目竞争，不会单独导致商用被禁止。单纯改名换图标、加支付和会员入口后售卖原有功能，不获许可。

本许可证不强制公开修改后的源码。分发须保留适用的许可和版权声明。第三方内容按其自己的条款处理，见 [第三方说明](THIRD_PARTY_NOTICES.md)。

提交原创贡献时，请确认你有权按本项目许可证提供该贡献；引入第三方代码或素材时一并附上来源及许可说明。
