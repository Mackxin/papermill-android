// 根构建脚本：只声明插件版本，不在这里 apply。
// 版本组合来自同机已验证可编的项目（AGP 8.5.0 + Kotlin 2.0.21），不要随意升。
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
