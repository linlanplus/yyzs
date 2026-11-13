# 用无线调试和 AI Agent 配置小克

这里的 Agent 指电脑上能够读取工程、执行终端命令的 AI 编程助手，例如 Codex 或 Claude Code。普通的纯聊天窗口不一定能执行 ADB。它协助完成安装和系统配置，日常使用小克不需要电脑、ADB 或后台 Python。

**本项目仅在一台荣耀设备上验证过替换 YOYO。** 型号和记录见 [实验与测试](EXPERIMENTS.md)。以下荣耀专用设置不是通用 Android 配方；其他机型应先调查自己的系统实现。

## 1. 先准备手机与电脑

1. 手机和电脑连接同一可信局域网，手机需支持 Android 11 及以上的无线调试。
2. 在手机“关于手机”中按系统提示开启开发者选项，再进入“开发者选项 → 无线调试”。不同系统的菜单名称可能不同。
3. 电脑安装 Android SDK Platform Tools。Android Studio 默认安装后，Windows 常见 ADB 路径为 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`。
4. 下载或克隆 [本仓库](https://github.com/linlanplus/yyzs)，用 Android Studio / Gradle 构建，或使用你信任、能够核实来源与签名的安装包。仓库有源码不等于已经发布 APK Release。

参考：[Android 官方无线调试说明](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi)。

## 2. 配对，再连接

手机点“使用配对码配对设备”，会显示 **IP、配对端口、六位配对码**。仅有配对码不能连接。弹窗不要提前关闭，配对码会过期。

PowerShell 示例；尖括号内容必须替换为手机当前显示的值：

```powershell
$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $Adb version
& $Adb pair '<手机IP>:<配对端口>'
# 按终端提示输入当前六位配对码，不把它写进脚本或仓库。

# 回到“无线调试”主页面，读取“IP 地址和端口”。
& $Adb connect '<手机IP>:<连接端口>'
& $Adb devices -l
```

**配对端口与连接端口通常不同。** `adb pair` 成功后未出现在设备列表中，可按主页面的连接端口执行 `adb connect`；网络或调试状态改变后，连接端口也可能变化。

从 `devices -l` 中确认品牌与型号，选定一个序列号。它可能是 `IP:端口`，也可能是 `_adb-tls-connect._tcp` 服务名。不要把序列号、真实局域网地址或配对码放进公共 Issue。

```powershell
$DeviceSerial = '<已确认的目标设备序列号>'
function Invoke-DeviceAdb {
    & $Adb -s $DeviceSerial @args
    if ($LASTEXITCODE -ne 0) { throw 'ADB 命令失败，停止后续步骤并检查输出' }
}
Invoke-DeviceAdb shell getprop ro.product.manufacturer
Invoke-DeviceAdb shell getprop ro.product.model
Invoke-DeviceAdb shell getprop ro.build.version.release
Invoke-DeviceAdb shell getprop ro.build.version.sdk
Invoke-DeviceAdb shell am get-current-user
```

## 3. 再把任务交给 Agent

把下面提示词复制给能执行本地命令的 Agent，填入自己的路径和已确认设备。API Key 留在手机填写，不需要给 Agent。

```text
请配置“小克（语音助手）”，工程目录是：<本机仓库目录>。
先阅读 AGENTS.md、docs/AGENT_SETUP.md、docs/EXPERIMENTS.md 和 docs/PRIVACY.md。

我已经完成无线调试配对。目标设备是：<设备序列号>，品牌/型号：<型号>。
所有 adb 命令都必须通过 -s 指向它，不要操作其他设备。

目标：让系统助手入口（优先长按电源键）打开小克；“翻译 / 屏幕翻译”
在小克内走截图、OCR、模型翻译与原位显示，不交给原厂助手转发。

请先做只读检查：确认系统版本、当前用户、已安装包和签名、assistant
角色、voice_interaction_service 的真实绑定、语音识别服务、无障碍状态。
备份原值和包启用状态至本机 work/device-backups，提供对应恢复方法。

安装采用同包名、同签名覆盖，保留原有 Key、设置和记录。签名不匹配或
手机拒绝安装时停止该安装步骤，说明原因；不得卸载原版或清除数据。
模型 Key 由我在手机里填写，不要读取应用私有配置。

先通过系统设置选择小克为默认助手并验证。如果确认是本文测试过的荣耀
YOYO 回写问题，我同意在完成备份后按文档可恢复地停用当前用户的
com.hihonor.magicvoice 并重新绑定小克；我知晓这会停用该用户的 YOYO 功能。
其他厂商包、其他系统安全机制不在授权范围内，不能照搬停用。

不要添加高频抢写设置、永久无障碍授权或隐藏后台监听逻辑。
无障碍服务和麦克风等需要我手动操作的地方，请指出具体入口。

最后分别报告：配置命令执行结果、系统服务真实绑定结果，以及哪些步骤
仍需我用物理电源键和真实页面验证。KEYCODE_ASSIST 模拟成功不算物理键验证。
配置完成后断开 adb，再验证小克是否仍能使用。
```

这个提示词给出了荣耀实验中停用 YOYO 的明确授权；如果你不愿停用它，删除相应授权句，改用快捷设置磁贴或无障碍按钮即可。停止厂商助手可能影响其语音和集成功能，不能把“可恢复”理解为没有影响。

## 4. 安装与基础配置

在工程目录构建：

```powershell
.\gradlew.bat :app:assembleRelease
Invoke-DeviceAdb install -r --no-streaming '.\app\build\outputs\apk\release\app-release.apk'
```

发布签名配置见 [README](../README.md#发布签名)。如果构建者没有原作者私钥，自建 Release 通常不能覆盖原作者版本。需要保留原版时，可先构建并安装包名不同的 Debug 测试版。

手机端依次完成：

1. 小克 → 设置 → 模型接口，填写接口地址、模型和 Key，保存并测试连接。
2. 小克 → 设置 → 唤醒方式，跳转系统设置，将小克选为默认数字助理。
3. 启用“小克 屏幕截图”无障碍服务，并在快捷设置面板添加“屏幕翻译”磁贴作为另一入口。
4. 如需语音，授予麦克风权限，检查设置 → 语音中的真实识别服务。停用 YOYO 前先确认仍有可用的其他识别服务；没有时先用文字输入。

应用界面名称为“小克”，但正式版包名仍是 `yyzs.lqkj.uk`。测试版包名是 `yyzs.lqkj.uk.ocrpreview`，服务类的命名空间仍是 `yyzs.lqkj.uk`。不要把早期实验截图中的旧包名直接套到当前版本。

## 5. 荣耀 YOYO：先备份，再处理回写

测试中只设置默认助手或只写一个 Secure Settings 值，曾被 YOYO 改回。高频把设置改回来会产生两个服务互相抢写的循环，这不是本项目采用的处理方式。

仅当检查确认设备为对应荣耀系统、确有 `com.hihonor.magicvoice` 回写、并已获得用户授权时，才进行此节。下面示例限定当前用户为 `0`；非机主用户或其他 ROM 应停止套用并重新分析。

先建立本机快照。它包含设备标识，**只保留在被 Git 忽略的 `work/` 中**：

```powershell
$AndroidUserId = (Invoke-DeviceAdb shell am get-current-user).Trim()
if ($AndroidUserId -ne '0') { throw '本节只覆盖已测试的用户 0 场景' }
$AppPackage = 'yyzs.lqkj.uk'
$BackupDir = Join-Path (Get-Location) 'work/device-backups'
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
$SnapshotPath = Join-Path $BackupDir ('assistant-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')

function Read-AndroidSetting([string]$Namespace, [string]$Name) {
    $value = ((Invoke-DeviceAdb shell settings --user $AndroidUserId get $Namespace $Name) -join "`n").Trim()
    if ($LASTEXITCODE -ne 0) { throw "读取设置失败：$Namespace/$Name" }
    if ($value -eq 'null') { return $null }
    return $value
}
$YoyoDump = (Invoke-DeviceAdb shell dumpsys package com.hihonor.magicvoice) -join "`n"
$EnabledMatch = [regex]::Match($YoyoDump, '(?m)^\s*User 0:.*\benabled=(\d+)')
if (-not $EnabledMatch.Success) { throw '无法确定 YOYO 原启用状态，停止修改' }
$Before = [ordered]@{
    device = $DeviceSerial
    user = $AndroidUserId
    appPackage = $AppPackage
    role = @(Invoke-DeviceAdb shell cmd role get-role-holders --user $AndroidUserId android.app.role.ASSISTANT)
    yoyoEnabledState = [int]$EnabledMatch.Groups[1].Value
    assistant = Read-AndroidSetting secure assistant
    voiceInteraction = Read-AndroidSetting secure voice_interaction_service
    voiceRecognition = Read-AndroidSetting secure voice_recognition_service
    invokeHivoice = Read-AndroidSetting secure invoke_hivoice_keypress_type
    powerLongPress = Read-AndroidSetting global power_button_long_press
    accessibilityServices = Read-AndroidSetting secure enabled_accessibility_services
    accessibilityEnabled = Read-AndroidSetting secure accessibility_enabled
}
$Before | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $SnapshotPath -Encoding utf8
Write-Output "快照已保存：$SnapshotPath"
```

在继续之前检查快照字段、命令退出码和设备匹配；Agent 应确认 APK 内确实存在下面的服务类。`global` 电源键设置是全设备设置，不只是某个应用的偏好。

```powershell
# 以下变更仅用于已确认并授权的荣耀实验场景。
Invoke-DeviceAdb shell pm disable-user --user $AndroidUserId com.hihonor.magicvoice
Invoke-DeviceAdb shell cmd role add-role-holder --user $AndroidUserId android.app.role.ASSISTANT $AppPackage
Invoke-DeviceAdb shell settings --user $AndroidUserId put secure assistant "$AppPackage/yyzs.lqkj.uk.assist.AssistService"
Invoke-DeviceAdb shell settings --user $AndroidUserId put secure voice_interaction_service "$AppPackage/yyzs.lqkj.uk.assist.AssistService"
Invoke-DeviceAdb shell settings --user $AndroidUserId put secure voice_recognition_service "$AppPackage/yyzs.lqkj.uk.assist.ProxyRecognitionService"
Invoke-DeviceAdb shell settings --user $AndroidUserId put secure invoke_hivoice_keypress_type none
Invoke-DeviceAdb shell settings put global power_button_long_press 5
```

每条命令都要检查成功后再继续；如 ROM 返回拒绝权限或不支持，不要通过盲目追加权限、停用更多系统包或卸载系统组件来强行完成。

其中 `invoke_hivoice_keypress_type=none` 是该荣耀实验中避开原厂电源键路径的配置，`power_button_long_press=5` 用于尝试系统助手入口。**不应将这两个值作为所有手机通用、永久有效的接口保证。**

再做只读验证：

```powershell
Invoke-DeviceAdb shell cmd role get-role-holders --user $AndroidUserId android.app.role.ASSISTANT
Invoke-DeviceAdb shell settings --user $AndroidUserId get secure assistant
Invoke-DeviceAdb shell settings --user $AndroidUserId get secure voice_interaction_service
Invoke-DeviceAdb shell dumpsys voiceinteraction
Invoke-DeviceAdb shell pm list packages -d --user $AndroidUserId com.hihonor.magicvoice
```

需要同时确认默认角色、设置值和实际服务绑定。然后由用户在公开测试页面上用**真实电源键**短暂长按，确认弹出小克；等待一段时间、切换应用后再次验证。不要把模拟 `KEYCODE_ASSIST` 的结果替代物理键结果，也不要把一次成功当成重启或系统升级后永久有效。

无障碍请通过系统界面启用。不要覆盖整条 `enabled_accessibility_services` 来只留下小克，那会影响其他无障碍服务；也不要声称 ADB 能在所有系统上“永久授权”。

## 6. 恢复原设置

恢复时使用本次修改前的真实快照，不能凭记忆把所有值统一写成 `power`、`null` 或默认值。先核对快照里的设备与用户，再恢复。

下面继续使用同一 PowerShell 会话中的 `$Adb`、`$DeviceSerial`、`$AndroidUserId`、`$SnapshotPath`：

```powershell
$Restore = Get-Content -LiteralPath $SnapshotPath -Raw | ConvertFrom-Json
if ($Restore.device -ne $DeviceSerial -or [string]$Restore.user -ne [string]$AndroidUserId) {
    throw '快照与当前设备或用户不匹配，停止恢复'
}

# 恢复原厂包原来的启用状态，而不是一律启用。
switch ([int]$Restore.yoyoEnabledState) {
    0 { Invoke-DeviceAdb shell pm default-state --user $AndroidUserId com.hihonor.magicvoice }
    1 { Invoke-DeviceAdb shell pm enable --user $AndroidUserId com.hihonor.magicvoice }
    2 { Invoke-DeviceAdb shell pm disable --user $AndroidUserId com.hihonor.magicvoice }
    3 { Invoke-DeviceAdb shell pm disable-user --user $AndroidUserId com.hihonor.magicvoice }
    4 { Invoke-DeviceAdb shell pm disable-until-used --user $AndroidUserId com.hihonor.magicvoice }
    default { throw '未知的包启用状态，请人工检查快照' }
}
Invoke-DeviceAdb shell cmd role remove-role-holder --user $AndroidUserId android.app.role.ASSISTANT $Restore.appPackage
foreach ($holder in @($Restore.role)) {
    if (-not [string]::IsNullOrWhiteSpace($holder)) {
        Invoke-DeviceAdb shell cmd role add-role-holder --user $AndroidUserId android.app.role.ASSISTANT $holder.Trim()
    }
}

function Restore-AndroidSetting([string]$Namespace, [string]$Name, $Value) {
    if ($null -eq $Value) {
        Invoke-DeviceAdb shell settings --user $AndroidUserId delete $Namespace $Name
    } elseif ([string]$Value -eq '') {
        # adb 的远端 shell 需要显式保留空字符串；不能把空字符串当作未设置。
        Invoke-DeviceAdb shell "settings --user $AndroidUserId put $Namespace $Name ''"
    } else {
        Invoke-DeviceAdb shell settings --user $AndroidUserId put $Namespace $Name ([string]$Value)
    }
    if ($LASTEXITCODE -ne 0) { throw "恢复失败：$Namespace/$Name" }
}
Restore-AndroidSetting secure assistant $Restore.assistant
Restore-AndroidSetting secure voice_interaction_service $Restore.voiceInteraction
Restore-AndroidSetting secure voice_recognition_service $Restore.voiceRecognition
Restore-AndroidSetting secure invoke_hivoice_keypress_type $Restore.invokeHivoice
Restore-AndroidSetting global power_button_long_press $Restore.powerLongPress
```

如果配置阶段仅通过系统页面增加了小克的无障碍权限，恢复时在页面关闭该服务即可。不要把快照中的整个服务列表盲目覆盖回去，以免抹掉用户在此期间启用的其他服务。

恢复后同样核对角色、服务绑定和真实电源键行为；某些 ROM 可能需要重新选择默认助手或重启才能重新绑定，应按实际现象处理。

## 7. 配置完成后

- 在普通英文测试页验证快速翻译、深度翻译、两种输出模式、文本输入和语音输入。
- 断开 ADB 后再测，确认日常使用不依赖电脑。完成调试后可关闭无线调试；不再信任该电脑时，在手机端忘记配对设备。
- 重启、息屏一段时间、应用升级和系统升级后的表现分别记录，不将其混成一次“通过”。
- 遇到问题使用 [反馈模板](FEEDBACK.md)，联系 [support@lqstudio.uk](mailto:support@lqstudio.uk)。仅发送相关且脱敏的结果。
