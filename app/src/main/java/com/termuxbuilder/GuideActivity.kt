package com.termuxbuilder

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class GuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        val sectionId = intent.getIntExtra("section_id", 1)
        val title = intent.getStringExtra("section_title") ?: "指南"
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tv = findViewById<TextView>(R.id.guide_content)
        tv.text = buildContent(sectionId)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildContent(id: Int): SpannableStringBuilder {
        return when (id) {
            1 -> guideEnvSetup()
            2 -> guideCommands()
            3 -> guideProject()
            4 -> guideSdkManager()
            5 -> guideTroubleshoot()
            6 -> guidePerformance()
            else -> SpannableStringBuilder("内容待补充")
        }
    }

    // ==================== 1. 环境搭建 ====================
    private fun guideEnvSetup(): SpannableStringBuilder {
        val green = ContextCompat.getColor(this, R.color.terminal_green)
        val gray = ContextCompat.getColor(this, R.color.text_secondary)
        val white = ContextCompat.getColor(this, R.color.text_primary)

        return build {
            section("前提条件", white) {
                line("- 安装 Termux (F-Droid 版本，Google Play 版本已停更)")
                line("- 授予存储权限：termux-setup-storage")
                line("- 确保手机剩余存储 ≥ 8GB")
                blank()
            }

            section("第一步：换源加速下载", white) {
                cmd("termux-change-repo")
                line("选择「Mirrors by BFSU」或「Tsinghua University」镜像")
                blank()
            }

            section("第二步：更新包列表", white) {
                cmd("pkg update && pkg upgrade -y")
                blank()
            }

            section("第三步：安装 OpenJDK 17", white) {
                cmd("pkg install openjdk-17 -y")
                cmd("java -version", "验证 Java 版本，应显示 17.x")
                blank()
            }

            section("第四步：安装 Android SDK 命令行工具", white) {
                cmd("pkg install android-sdk -y")
                line("或手动下载（推荐，版本更新）：")
                cmd("cd ~ && mkdir -p android-sdk/cmdline-tools")
                cmd("curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip")
                cmd("unzip commandlinetools-linux-*.zip -d android-sdk/cmdline-tools")
                cmd("mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest")
                blank()
            }

            section("第五步：配置环境变量", white) {
                cmd("echo 'export ANDROID_HOME=\$HOME/android-sdk' >> ~/.bashrc")
                cmd("echo 'export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc")
                cmd("echo 'export PATH=\$PATH:\$ANDROID_HOME/platform-tools' >> ~/.bashrc")
                cmd("source ~/.bashrc")
                blank()
            }

            section("第六步：同意许可协议并安装 SDK", white) {
                cmd("yes | sdkmanager --licenses")
                cmd("sdkmanager \"platform-tools\" \"platforms;android-34\" \"build-tools;34.0.0\"")
                line("视网络情况，此步骤需 5-15 分钟")
                blank()
            }

            section("第七步：安装 Gradle", white) {
                cmd("pkg install gradle -y")
                cmd("gradle --version", "验证安装，应显示 8.x")
                blank()
            }

            section("验证编译环境", white) {
                cmd("echo \$ANDROID_HOME", "应输出 /data/data/com.termux/files/home/android-sdk")
                cmd("ls \$ANDROID_HOME/platforms", "应看到 android-34")
                cmd("gradle --version && java -version && sdkmanager --list")
                line("三者在同一终端无报错即环境就绪。")
            }
        }
    }

    // ==================== 2. 常用命令 ====================
    private fun guideCommands(): SpannableStringBuilder {
        val white = ContextCompat.getColor(this, R.color.text_primary)

        return build {
            section("编译命令", white) {
                line("编译 Debug APK：")
                cmd("gradle assembleDebug")
                line("编译 Release APK：")
                cmd("gradle assembleRelease")
                line("清理构建产物：")
                cmd("gradle clean")
                line("编译并跳过测试：")
                cmd("gradle assembleDebug -x test")
                line("查看所有 Gradle 任务：")
                cmd("gradle tasks")
            }

            section("APK 路径", white) {
                line("Debug APK 在：app/build/outputs/apk/debug/app-debug.apk")
                line("Release APK（未签名）在：app/build/outputs/apk/release/app-release-unsigned.apk")
            }

            section("签名 APK（Release）", white) {
                line("先生成签名密钥：")
                cmd("keytool -genkey -v -keystore release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000")
                line("在 app/build.gradle.kts 中添加签名配置。")
            }

            section("安装与调试", white) {
                line("用 ADB 安装到手机：")
                cmd("adb install app/build/outputs/apk/debug/app-debug.apk")
                line("查看设备：")
                cmd("adb devices")
                line("查看日志：")
                cmd("adb logcat | grep -i termuxbuilder")
            }

            section("快速组合命令", white) {
                line("一键清理+编译+安装：")
                cmd("gradle clean && gradle assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk")
            }
        }
    }

    // ==================== 3. 项目模板 ====================
    private fun guideProject(): SpannableStringBuilder {
        val white = ContextCompat.getColor(this, R.color.text_primary)

        return build {
            section("创建项目目录结构", white) {
                cmd("mkdir -p MyApp/app/src/main/java/com/myapp")
                cmd("mkdir -p MyApp/app/src/main/res/{layout,values,drawable,mipmap-hdpi}")
                cmd("mkdir -p MyApp/gradle/wrapper")
            }

            section("settings.gradle.kts", white) {
                line("""pluginManagement { repositories { google(); mavenCentral() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "MyApp"
include(":app")""".trimIndent())
            }

            section("build.gradle.kts（根目录）", white) {
                line("""plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}""".trimIndent())
            }

            section("app/build.gradle.kts", white) {
                line("""plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.myapp"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.myapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}""".trimIndent())
            }

            section("初始化 Gradle Wrapper", white) {
                cmd("cd MyApp && gradle wrapper --gradle-version 8.5")
            }

            section("编译", white) {
                cmd("cd MyApp && gradle assembleDebug")
                line("成功后 APK 位于 app/build/outputs/apk/debug/")
            }
        }
    }

    // ==================== 4. SDK 管理 ====================
    private fun guideSdkManager(): SpannableStringBuilder {
        val white = ContextCompat.getColor(this, R.color.text_primary)

        return build {
            section("列出已安装和可用的 SDK", white) {
                cmd("sdkmanager --list")
            }

            section("安装指定平台", white) {
                cmd("sdkmanager \"platforms;android-34\"", "安装 Android 14 SDK")
                cmd("sdkmanager \"platforms;android-33\"", "安装 Android 13 SDK")
            }

            section("安装 Build-Tools", white) {
                cmd("sdkmanager \"build-tools;34.0.0\"")
            }

            section("安装平台工具（adb 等）", white) {
                cmd("sdkmanager \"platform-tools\"")
            }

            section("安装 NDK（如需 C++ 开发）", white) {
                cmd("sdkmanager \"ndk;26.1.10909125\"")
                line("NDK 体积约 2GB，按需安装。")
            }

            section("更新所有已安装组件", white) {
                cmd("sdkmanager --update")
            }

            section("同意所有许可协议", white) {
                cmd("yes | sdkmanager --licenses")
            }

            section("查看已安装组件", white) {
                cmd("sdkmanager --list --verbose 2>/dev/null | grep -E 'Installed|Available'")
            }
        }
    }

    // ==================== 5. 常见错误 ====================
    private fun guideTroubleshoot(): SpannableStringBuilder {
        val white = ContextCompat.getColor(this, R.color.text_primary)
        val accent = ContextCompat.getColor(this, R.color.accent)

        return build {
            // Error 1
            sectionBold("SDK 找不到 / ANDROID_HOME 未设置", accent, white) {
                line("报错：SDK location not found / ANDROID_HOME is not set")
                line("原因：未配置环境变量或变量未在当前终端生效")
                line("解决：")
                cmd("echo \$ANDROID_HOME", "先确认变量是否存在")
                cmd("source ~/.bashrc", "如果为空，重新加载配置")
                cmd("export ANDROID_HOME=\$HOME/android-sdk", "或临时手动设置")
                line("检查 local.properties 是否包含 sdk.dir=\$ANDROID_HOME")
                blank()
            }

            // Error 2
            sectionBold("License not accepted", accent, white) {
                line("原因：未同意 Android SDK 许可协议")
                line("解决：")
                cmd("yes | sdkmanager --licenses")
                blank()
            }

            // Error 3
            sectionBold("Gradle 下载依赖失败 / 超时", accent, white) {
                line("原因：网络问题或镜像源不可用")
                line("解决：")
                line("1. 检查网络：curl -I https://dl.google.com")
                line("2. 在 ~/.gradle/init.gradle 中配置阿里云镜像：")
                cmd("""mkdir -p ~/.gradle && cat > ~/.gradle/init.gradle << 'EOF'
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google()
        mavenCentral()
    }
}
EOF""")
                blank()
            }

            // Error 4
            sectionBold("OutOfMemoryError: Java heap space", accent, white) {
                line("原因：Gradle 守护进程内存不足")
                line("解决：在 gradle.properties 中增加堆内存：")
                cmd("echo 'org.gradle.jvmargs=-Xmx2048m' >> gradle.properties")
                blank()
            }

            // Error 5
            sectionBold("adb: command not found", accent, white) {
                line("原因：未安装 platform-tools")
                line("解决：")
                cmd("sdkmanager \"platform-tools\"")
                cmd("export PATH=\$PATH:\$ANDROID_HOME/platform-tools")
                blank()
            }

            // Error 6
            sectionBold("CompileSdkVersion 过高 / 平台未安装", accent, white) {
                line("原因：build.gradle.kts 中 compileSdk 版本未下载")
                line("解决：")
                cmd("sdkmanager \"platforms;android-34\"", "安装对应版本")
                line("或修改 build.gradle.kts 中 compileSdk 为已安装版本")
            }
        }
    }

    // ==================== 6. 性能优化 ====================
    private fun guidePerformance(): SpannableStringBuilder {
        val white = ContextCompat.getColor(this, R.color.text_primary)

        return build {
            section("gradle.properties 推荐配置", white) {
                cmd("""org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.configuration-cache=true
android.enableJetifier=false
android.useAndroidX=true
kotlin.incremental=true""")
            }

            section("配置说明", white) {
                line("jvmargs -Xmx2048m — 分配 2GB 堆内存给 Gradle")
                line("parallel=true — 并行编译独立模块")
                line("caching=true — 启用构建缓存，增量编译提速 40-60%")
                line("daemon=true — 保持 Gradle 守护进程常驻，避免冷启动")
                line("configuration-cache=true — 缓存配置阶段结果")
                line("Jetifier=false — 如果不用旧 Support 库，关闭可提速")
            }

            section("按需下载 SDK", white) {
                line("只需安装当前项目用到的平台版本，避免冗余：")
                cmd("sdkmanager --list 2>/dev/null | grep platforms")
                line("按需安装，不要一次性装所有版本。")
            }

            section("离线编译模式（可选）", white) {
                line("首次编译成功后，所有依赖已缓存到本地。后续编译可加 --offline 跳过网络检查：")
                cmd("gradle assembleDebug --offline")
                line("注意：新增依赖后需先在线同步一次。")
            }

            section("手机端编译预期", white) {
                line("小型项目：30-90 秒")
                line("中型项目：2-5 分钟")
                line("大型项目：5-15 分钟+（建议用 GitHub Actions 云端编译）")
            }
        }
    }

    // ==================== DSL helpers ====================

    private fun SpannableStringBuilder.line(text: CharSequence) {
        append(text)
        append("\n")
    }

    private fun SpannableStringBuilder.blank() {
        append("\n")
    }

    private fun SpannableStringBuilder.cmd(command: String, comment: String? = null) {
        val green = ContextCompat.getColor(this@GuideActivity, R.color.terminal_green)
        val gray = ContextCompat.getColor(this@GuideActivity, R.color.text_secondary)
        val start = length
        append("$ ")
        append(command)
        setSpan(ForegroundColorSpan(green), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (comment != null) {
            append("    # ")
            val cStart = length
            append(comment)
            setSpan(ForegroundColorSpan(gray), cStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        append("\n")
    }

    private fun section(title: String, titleColor: Int, body: SpannableStringBuilder.() -> Unit) {
        val start = length
        append("▎ $title\n")
        setSpan(ForegroundColorSpan(titleColor), start, length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        body()
        append("\n")
    }

    private fun sectionBold(title: String, titleColor: Int, textColor: Int, body: SpannableStringBuilder.() -> Unit) {
        val start = length
        append("▌ $title\n")
        setSpan(ForegroundColorSpan(titleColor), start, length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        body()
    }

    private fun build(block: SpannableStringBuilder.() -> Unit): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        sb.block()
        return sb
    }
}
