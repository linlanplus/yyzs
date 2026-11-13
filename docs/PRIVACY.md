# 小克隐私说明 · Xiaoke Privacy

适用版本 / Applies to · **1.0.1**

## 声明 · Statement

- 小克没有“用户体验改进计划”，软件里也没有任何要求你上传内容或使用记录的条款。
- 小克不会向开发者的网站或服务器上传任何东西：截图、录音、对话、API Key、使用记录、崩溃日志都不会。
- 没有开发者的中转服务器，也没有广告。
- 翻译只发给你配置的模型，不经过手机厂商的翻译服务。

---

- Xiaoke has no "user experience improvement program", and the app has no terms asking you to upload content or usage records.
- Xiaoke uploads nothing to the developer's website or servers: no screenshots, recordings, chats, API keys, usage records, or crash logs.
- There is no developer relay server and no advertising.
- Translations go only to the model you configure, not through the phone vendor's translation service.

## 数据发到哪里 · Where data goes

| 功能 / Feature | 发送的内容 / What is sent | 发给谁 / Sent to |
| --- | --- | --- |
| 快速翻译 / Fast translation | OCR 文字 / OCR text | 你配置的模型 / your model |
| 深度翻译 / Deep translation | OCR 文字、坐标、缩小的截图 / OCR text, coordinates, downscaled screenshot | 你配置的模型 / your model |
| 对话 / Chat | 对话内容、工具结果、已安装应用名等上下文 / messages, tool results, context such as installed app names | 你配置的模型 / your model |
| 联网搜索 / Web search | 搜索词 / query | Bing |
| 读取网页 / Reading pages | 网址 / URL | 原网站，正文不全时用 Jina Reader / the site itself, or Jina Reader if the text is incomplete |
| 语音识别 / Speech recognition | 语音 / audio | 你在系统里选的识别服务 / the recognizer you picked in system settings |
| 朗读 / Read aloud | 回复文字 / reply text | 系统 TTS 引擎 / the system TTS engine |
| 实时语音 / Live voice | 会话音频 / session audio | 你配置的语音服务 / your voice service |
| OCR | 设备、应用信息和性能诊断数据 / device, app, and performance diagnostics | Google（[ML Kit 披露 / disclosure](https://developers.google.com/ml-kit/android-data-disclosure)） |

文字识别在手机上完成。语音识别和朗读用的是你在系统里选的服务，如果选的是厂商的服务，这部分数据由厂商处理。

Text recognition runs on the phone. Speech recognition and read-aloud use whatever services you picked in system settings; if those are the vendor's, the vendor handles that data.

## 手机上存了什么 · What stays on the phone

- API Key、服务地址和设置存在应用私有目录里。
- 聊天记录存在应用私有目录里，可以在应用内删除。
- 截图在内存里处理，只有你点保存时才写到 `Pictures/小克`。
- 关闭了系统自动备份（`allowBackup="false"`）。
- 无障碍服务只用来截图，不读取其他应用的界面内容。
- 没有后台唤醒词监听，不会定时截图或录音。

---

- Your API key, endpoint, and settings are stored in the app's private storage.
- Chat history is stored in the app's private storage and can be deleted in the app.
- Screenshots are processed in memory and written to `Pictures/小克` only when you tap save.
- System auto-backup is turned off (`allowBackup="false"`).
- The accessibility service is used only for screenshots and does not read other apps' UI.
- No background wake-word listening, and no scheduled screenshots or recordings.

问题反馈 · Contact: support@lqstudio.uk
