# 小克：隐私与数据流说明

适用版本：1.0.2。更新日期：2026-11-13。

这份说明基于本项目源码和依赖公开文档核查，并不等同于已经完成全设备网络抓包或第三方独立隐私审计。

## 开发者声明

小克不设置“用户体验改进计划”，不包含要求用户为产品改进、行为统计或模型训练而向开发者上传内容和使用记录的条款。

本项目没有向开发者的网站、服务器或开发者自建分析平台自动回传截图、录音、对话、API Key、使用记录或崩溃日志的机制。开发者没有为本应用设置必须经过的模型中转服务，应用内也无广告

用户发起的功能请求，以及当前采用的第三方 SDK，会按下面列明的用途与外部服务通信。

## 各功能的数据去向

| 功能 / 触发时机 | 涉及的数据 | 去向与范围 | 对应实现 |
| --- | --- | --- | --- |
| 打开助手或触发截图 | 当前屏幕像素 | 系统截图接口交给应用，在本机处理；深度翻译另见下行 | `assist/AssistSessionService.kt`、`assist/ScreenAccessibilityService.kt` |
| 快速屏幕翻译 | OCR 文字、翻译要求 | 发送到用户配置的模型接口；应用层不附带截图 | `translate/Translator.kt`、`translate/DeepSeekClient.kt` |
| 深度屏幕翻译 | OCR 文字、坐标、缩放后的截图 | 发送到用户配置且支持图片输入的模型接口 | `translate/Translator.kt` |
| 文本或助手对话 | 输入、会话历史、工具结果；可用应用名称、时间、电量等上下文 | 发送到配置的对话模型；以代码实际拼接的上下文为准 | `agent/AssistantAgent.kt`、`agent/PhoneActions.kt` |
| 按住说话 / 系统识别代理 | 用户语音 | 交给选定的系统识别服务；其是否联网由该服务实现决定 | `speech/Recognizers.kt`、`speech/VoiceInput.kt`、`assist/ProxyRecognitionService.kt` |
| 实时语音会话 | 会话音频、转写及必要任务数据 | 发送到配置的语音接口；委托给助手的任务还会交给对话模型 | `live/LiveSession.kt` |
| 朗读回复 | 待朗读的文本 | 交给系统 TTS 引擎，可能使用在线音色或服务 | `speech/SpeechOutput.kt` |
| 联网搜索 | 查询文字及网络请求信息 | 搜索服务，当前实现使用 Bing | `agent/WebTools.kt` |
| 读取网页 | 目标网址及网络请求信息 | 原站点；正文不足时会请求 Jina Reader | `agent/WebTools.kt` |
| OCR SDK 运行 | 设备与应用信息、安装级标识、性能等诊断数据 | 当前 ML Kit SDK 按其官方披露发送至 Google；本机识别不代表没有 SDK 诊断联网 | `ocr/OcrEngine.kt`、依赖声明 |
| 打开外部应用 | 用户要求的搜索词、目的地或号码等 | 通过 Intent 交给用户设备上的浏览器、地图、拨号等应用 | `agent/PhoneActions.kt` |
| 主动保存 / 分享结果 | 译文或译图 | 手机图片目录、分享缓存及用户选择的接收应用 | `ui/ImageActions.kt` |
| 查看在线文档 | 页面访问请求 | 用户点击后由浏览器访问 GitHub | `SettingsActivity.kt` |

表中的源码路径均相对于 `app/src/main/java/yyzs/lqkj/uk/`。

API Key 用于向用户所配置的接口鉴权。如果配置的是第三方中转地址，接收方就是该中转服务，并非自动变成模型原厂。使用自建模型服务时，仍须验证该服务与客户端的接口兼容性；

## Google SDK 的诊断数据

本项目采用打包进应用的 ML Kit 文字识别模型。图片识别在本机进行，但 Google 官方明确披露 SDK 会收集设备、应用、安装标识及性能诊断等信息，用于诊断与使用分析。项目没有把这类 SDK 行为描述成“零统计”，也没有验证其诊断流量已被关闭。

来源：[ML Kit 数据披露](https://developers.google.com/ml-kit/android-data-disclosure)、[ML Kit 条款](https://developers.google.com/ml-kit/terms)。


## 本机保存

- API Key、服务地址及偏好保存在应用私有 SharedPreferences 中；未额外实现应用层密钥加密，依赖 Android 应用沙箱保护。
- 聊天记录保存在应用私有文件目录，可在应用内删除会话。
- 截图主要在内存中处理；用户主动保存时写入 `Pictures/小克`，主动分享时生成应用缓存文件。旧版已经保存到 `Pictures/翻译助手` 的文件不会因更名自动搬移。
- `AndroidManifest.xml` 中设置了 `android:allowBackup="false"`；这不是对 Root、设备管理软件或所有厂商迁移工具的绝对保证。
- 无障碍配置 `canRetrieveWindowContent="false"`、`canTakeScreenshot="true"`；实现忽略无障碍事件，不遍历其他应用的界面节点。截图能力仍可能包含屏幕上当前可见的个人内容。
- 当前没有后台唤醒词监听，也没有为使用统计而定时截图或录音的任务。实时语音只在用户开启的会话期间采集音频。


## 可自行复查的方法

在源码中检查网络入口、依赖和合并后的 Manifest；在测试设备上分别观察待机、快速翻译、深度翻译、语音和联网搜索。网络实验应使用自己制作的公开测试文本、自己的测试端点或授权抓包环境，不上传真实私人屏幕。

复现清单见 [实验与测试](EXPERIMENTS.md)。
