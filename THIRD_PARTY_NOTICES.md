# 第三方组件和内容

根目录 LICENSE 只适用于本项目有权许可的原创内容，不改变以下组件及其他第三方内容的既有许可。

## 仓库内分发的 Gradle Wrapper

`gradlew`、`gradlew.bat` 及 `gradle/wrapper/gradle-wrapper.jar` 来自 Gradle 项目，按 Apache License 2.0 提供。脚本内原有版权和许可声明予以保留。

- 项目：https://github.com/gradle/gradle
- 许可证副本：[Apache-2.0.txt](licenses/Apache-2.0.txt)
- Gradle 发行版及其完整第三方说明：https://github.com/gradle/gradle/blob/master/LICENSE

## 构建时获取的依赖

依赖坐标和版本见 `gradle/libs.versions.toml` 及 `app/build.gradle.kts`。源码仓库不重新授权这些依赖。若再分发包含依赖的 APK，应按实际构建产物附带所需的许可和声明。

- AndroidX、Jetpack Compose：https://android.googlesource.com/platform/frameworks/support/
- Kotlin：https://github.com/JetBrains/kotlin
- kotlinx.coroutines：https://github.com/Kotlin/kotlinx.coroutines
- OkHttp：https://github.com/square/okhttp
- Google ML Kit：https://developers.google.com/ml-kit/terms
- Android SDK：https://developer.android.com/studio/terms

Google ML Kit 及 Android SDK 的使用受各自条款约束；本项目公开源码不表示这些 SDK 和模型均按本项目许可证开放。

## 回归样本

`app/src/androidTest/assets` 中的截图用于识别、段落归并及绘制回归测试。截图里的第三方界面、账号、头像、标志及文字仍归各自权利人，本项目不对这些第三方内容另行授予商用或品牌使用权。它们不打包进主应用的生产资源。

截图中的帖子内容仅作为测试输入，不代表本项目对其事实的确认。
