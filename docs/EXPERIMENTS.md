# 小克：实验方法与测试记录

记录日期：2026-11-13。这里区分“实际做过的实验”“本次版本检查”和“可复现方法”，不把计划写成已完成。

## 验证范围

| 项目 | 实际范围 |
| --- | --- |
| 手机 | HONOR AAK-AN00，一台 |
| 系统 | Android 16 / API 36 |
| 被替换助手 | 荣耀 YOYO，包名 `com.hihonor.magicvoice` |
| 小克正式包名 | `yyzs.lqkj.uk` |
| 独立测试包名 | `yyzs.lqkj.uk.ocrpreview` |
| 完整 MagicOS 固件版本 | 此次记录未完整保留，不据此推断其他固件兼容 |
| 其他厂商 | 未验证原厂助手替换，不列入支持结论 |

## 1. 原厂助手入口替换

开发期观察到：设置小克为默认助手后，YOYO 曾把 `voice_interaction_service` 改回自身。仅写默认助手值不足以稳定接管，反复抢写会形成循环。

在该荣耀实验中，完成原状态备份后，可恢复地停用当前用户的 YOYO 包，再设置助手角色、语音交互服务及电源键入口，能够让原厂入口转向自己的助手。用户已在开发过程中实测过真实电源键唤起，并提供了 [替换前](images/yoyo-before.png) 与 [替换后](images/xiaoke-after.png) 的界面对比。

历史检查还记录过正式包的助手服务和无障碍服务已绑定。当前这一轮只读核对能看到正式包仍持有助手角色、YOYO 包处于停用状态，但无障碍服务当时未启用。因此，“永久无障碍授权”或“系统永不回写”不属于已验证结论。

具体备份、配置、核对及恢复命令见 [Agent 配置指南](AGENT_SETUP.md)。早期原型使用过不同包名，不能把早期命令不经检查复制到当前安装。

### 手工复现步骤

1. 使用不含私人内容的英文测试页面，先记录原助手长按电源键的表现。
2. 配对 ADB，确认唯一目标设备；保存角色、语音服务、厂商包启用状态和电源键原值。
3. 安装并配置小克，在系统界面选择默认数字助理。先测试标准方式是否已可用。
4. 仅对已确认的荣耀 YOYO 回写，按配置指南和用户授权进行可恢复处理。
5. 回到测试页面，真实长按电源键，确认出现小克。`KEYCODE_ASSIST` 仅可辅助排查，不能替代这一步。
6. 输入“翻译”及“屏幕翻译”，分别验证快速翻译；再用支持图片的模型验证深度翻译。
7. 检查两种输出模式、复制、保存、原文切换；用实际语音检验识别服务。
8. 切换应用后再次检查角色和真实绑定，断开 ADB 后重复操作，确认日常使用不依赖电脑。
9. 重启、长时间息屏、应用升级和系统升级后的结果分别记录，不以短期成功推断长期稳定。

测试“粗口等材料是否被拒译”时，应固定同一测试文本、目标语言、模型和参数，并记录实际输出或错误。本文没有给出原厂助手与模型的系统性拒译率统计，也没有声称任何模型永不拒绝。

## 2. 已完成的历史 OCR / 排版回归

历史版本 `1.0-translation-toggle` 曾在上述荣耀设备上通过 18 组回归检查，原图为 1272 × 2800，识别到 15 个区域，报告未列出被截断区域。

脱敏记录：[honor-layout-historical.json](test-results/honor-layout-historical.json)。记录只保留测试信息，不含手机序列号、真实 IP、配对码或 API Key。

| 检查组 | 针对的问题 |
| --- | --- |
| 尾词、段落边界、多列、连字符 | `turned out.` 的 `out.` 遗落、跨段或跨列错误拼接 |
| 缺失 ID 补译、重复 / 未知 ID、空译文、重试耗尽、取消 | 模型结果漏项、乱序或失败时的处理 |
| 半个字形、相邻图标、文字溢出 | 原文字形残留和擦除 / 排版越界 |
| 头像、emoji、插图保留，输出顺序 | 不该改动的区域保持像素一致 |
| 流式与一次性输出、默认开关、失败及取消 | 一次性模式不泄露半成品，两种模式最终内容一致 |

测试使用固定译文替身来隔离 OCR 和绘制逻辑，**没有调用翻译模型 API，也没有读取用户 API Key**。ML Kit SDK 本身的诊断流量并未在该测试中抓包或关闭。

记录中的 `elapsedMs` 是该次本地回归的执行时间，不是模型翻译延迟、全天性能基准或联网隐私审计结果。

## 3. 小克 1.0.2 本次检查

记录：[xiaoke-1.0.2-validation.json](test-results/xiaoke-1.0.2-validation.json)。

| 检查 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过 |
| `:app:assembleDebugAndroidTest` | 通过，生成了测试 APK |
| `:app:assembleRelease` | 通过 |
| `:app:lintDebug` | 通过；0 错误，27 警告 |
| 新版真机回归 | 未执行：安装独立测试版时，设备返回 `INSTALL_FAILED_ABORTED: User rejected permissions` |
| 覆盖正式版 / 清除用户数据 | 本次没有执行 |

新版安装被拒绝后，没有卸载原版、清除 Key 或修改助手绑定来强行继续。旧版本的 18 组通过记录不替代 1.0.2 的真机测试，也不证明新版物理电源键路径已经重新验证。

## 4. 如何运行现有回归

先完成 [无线调试配对](AGENT_SETUP.md)，明确 `$DeviceSerial`。使用独立 Debug 包，避免覆盖用户正式版：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
& $Adb -s $DeviceSerial install -r --no-streaming '.\app\build\outputs\apk\debug\app-debug.apk'
& $Adb -s $DeviceSerial install -r --no-streaming '.\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'

# 原图：包含段落、尾词、字形擦除、输出模式及协议断言。
& $Adb -s $DeviceSerial shell am instrument -w -e phase after -e fixture original 'yyzs.lqkj.uk.ocrpreview.test/yyzs.lqkj.uk.TranslationRegression'

# 列表图：包含列表字号及头像、emoji、底图等保留断言。
& $Adb -s $DeviceSerial shell am instrument -w -e phase after -e fixture list 'yyzs.lqkj.uk.ocrpreview.test/yyzs.lqkj.uk.TranslationRegression'
```

每步成功后才继续；若设备拒绝安装或签名不符，停止本次测试并记录原因。不要因为已经存在旧测试包就直接执行，否则可能误测旧版本。

自定义 Instrumentation 使用 `Activity.RESULT_OK`，因此成功时最后可见 `INSTRUMENTATION_CODE: -1`。原图还应有 `PASS: 18 regression checks`，列表图应有 `List fixture:`，且没有 `FAILED:`。必须结合输出判断，不能只看 ADB 进程退出码。

读取测试专用目录的报告，不读取正式应用的私人设置：

```powershell
& $Adb -s $DeviceSerial shell run-as yyzs.lqkj.uk.ocrpreview cat files/regression-after.json
& $Adb -s $DeviceSerial shell run-as yyzs.lqkj.uk.ocrpreview cat files/regression-list-after.json
```

同目录还会产生 `regression-after.png` 和 `regression-list-after.png`。通过二进制安全的方式导出查看，避免旧版 PowerShell 文本重定向损坏 PNG。

## 5. 隐私数据流实验方法

这部分目前是复查方法，**未完成全流程网络抓包验证**。

1. 准备虚构、可公开的文本和截图，以及自己控制的测试模型端点；不要用真实个人消息做抓包样本。
2. 静置应用，记录进程、目标域名、请求时间和权限；不能把整台手机的网络连接都算作小克请求。
3. 快速模式检查自有模型端点收到的请求应包含文本，应用层不应含截图图片字段。
4. 深度模式应额外包含图片及坐标；对比两种模式的数据差异。
5. 分别测试网页工具、语音识别、TTS 和实时语音，记录各自服务提供方；系统服务可能运行在另一个进程。
6. 按 [ML Kit 官方披露](https://developers.google.com/ml-kit/android-data-disclosure) 单列 SDK 诊断。不要把“本地 OCR”当成“SDK 绝不联网”。
7. 核对应用是否向开发者运营的域名自动回传数据；与 [隐私说明](PRIVACY.md) 和源码中的请求入口对照。

仅有 DNS 或连接域名不能确定 TLS 请求体；一次未观察到连接也不能证明未来永不上传。记录工具版本、条件和观测窗口，使别人能够复查。

## 6. 仍待验证

- 其他荣耀机型 / 固件，以及小米、OPPO、vivo、三星等厂商助手替换。
- 反复重启、系统升级、长时间息屏与后台清理后的稳定性。
- “你好小克”等后台唤醒词：尚未实现。
- 本地 Qwen 服务端到端兼容性，以及不同语音服务和实时语音接口。
- 严格限定网络仅访问模型的模式、第三方 SDK 全量流量审计。
- 完整翻译质量评测、模型拒译率对比和电池消耗基准。

问题及 Bug 请联系 [support@lqstudio.uk](mailto:support@lqstudio.uk)，按 [反馈模板](FEEDBACK.md) 提供脱敏材料。
