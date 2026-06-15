package com.termuxbuilder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MenuItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectActivity : AppCompatActivity() {

    companion object {
        private const val TYPE_PARENT = 0
        private const val TYPE_DIR = 1
        private const val TYPE_FILE = 2
        private const val TYPE_QUICK = 3

        // 项目首页常用文件（相对路径）
        // 常用文件（相对项目根目录）。MainActivity.kt 的包路径动态查找。
        private val QUICK_FILES = listOf(
            "app/src/main/AndroidManifest.xml",
            "app/build.gradle.kts",
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values/colors.xml",
            "app/src/main/res/values/themes.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
        )

    }

    private fun resolveQuickFiles(): List<String> {
        val result = mutableListOf<String>()
        // 动态查找 MainActivity.kt
        val javaDir = File(projectPath, "app/src/main/java")
        if (javaDir.exists()) {
            javaDir.walkTopDown().filter { it.isFile && it.name == "MainActivity.kt" }.forEach {
                result.add(it.relativeTo(File(projectPath)).path)
            }
        }
        // 静态路径
        result.addAll(QUICK_FILES.filter { File(projectPath, it).exists() })
        return result
    }

    private lateinit var projectPath: String
    private lateinit var projectName: String
    private lateinit var fileList: RecyclerView
    private lateinit var btnBuild: Button
    private lateinit var btnNewFile: TextView
    private lateinit var pathLabel: TextView
    private var currentPath = ""
    private var files = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        projectPath = intent.getStringExtra("project_path") ?: return finish()
        projectName = intent.getStringExtra("project_name") ?: ""
        currentPath = projectPath

        title = projectName

        pathLabel = findViewById(R.id.path_label)
        btnNewFile = findViewById(R.id.btn_new_file)
        fileList = findViewById(R.id.file_list)
        btnBuild = findViewById(R.id.btn_build)

        fileList.layoutManager = LinearLayoutManager(this)

        btnNewFile.setOnClickListener { createNewFile() }
        btnBuild.setOnClickListener { triggerBuild() }

        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isAtRoot() = currentPath == projectPath

    private fun loadFiles() {
        val dir = File(currentPath)
        files.clear()
        dir.listFiles()?.filter {
            !(it.name == "build" || it.name == ".gradle" || it.name.startsWith("."))
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach {
            files.add(it)
        }
        fileList.adapter = FileAdapter(files)

        // Update path label
        val rel = if (isAtRoot()) "/" else currentPath.removePrefix(projectPath)
        pathLabel.text = rel

        // 首页显示常用文件按钮，子目录隐藏
        // 所有层级都显示新建文件按钮
    }

    private fun navigateTo(dir: File) {
        currentPath = dir.absolutePath
        loadFiles()
    }

    private fun navigateUp(): Boolean {
        if (isAtRoot()) return false
        currentPath = File(currentPath).parent ?: projectPath
        if (!currentPath.startsWith(projectPath)) currentPath = projectPath
        loadFiles()
        return true
    }

    private fun zipDir(srcDir: File, destFile: File) {
        ZipOutputStream(FileOutputStream(destFile)).use { zip ->
            srcDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(srcDir).path
                zip.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun ensureBuildFiles() {
        val root = File(projectPath)
        // 根目录 build.gradle.kts
        val rootGradle = File(root, "build.gradle.kts")
        if (!rootGradle.exists()) {
            rootGradle.writeText("""plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
""")
        }
        // settings.gradle.kts
        val settingsGradle = File(root, "settings.gradle.kts")
        if (!settingsGradle.exists()) {
            settingsGradle.writeText("""pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "$projectName"
include(":app")
""")
        }
        // gradlew
        val gradlewFile = File(root, "gradlew")
        if (!gradlewFile.exists()) {
            gradlewFile.writeText("""#!/data/data/com.termux/files/usr/bin/bash
cd "$$(dirname "$$0")" || exit 1
CLASSPATH=$$(pwd)/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$$CLASSPATH" ]; then
    echo ">>> download gradle-wrapper.jar..."
    mkdir -p gradle/wrapper
    curl -fsSL -o "$$CLASSPATH" "https://raw.githubusercontent.com/zhco/TermuxBuilder/main/app/src/main/res/raw/gradle_wrapper.jar" || {
        echo "ERROR: cannot download gradle-wrapper.jar"; exit 1
    }
fi
exec java -classpath "$$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$$@"
""")
            gradlewFile.setExecutable(true)
        }
        // gradle-wrapper.properties
        val propsDir = File(root, "gradle/wrapper")
        propsDir.mkdirs()
        val propsFile = File(propsDir, "gradle-wrapper.properties")
        if (!propsFile.exists()) {
            propsFile.writeText("""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")
        }
        // app/build.gradle.kts
        val appGradle = File(root, "app/build.gradle.kts")
        if (!appGradle.exists()) {
            appGradle.writeText("""plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.codeeditor.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.codeeditor.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
""")
        }
    }

    private fun triggerBuild() {
        Toast.makeText(this, "正在打包项目...", Toast.LENGTH_SHORT).show()

        btnBuild.isEnabled = false
        btnBuild.text = "打包中..."

        Thread {
            try {
                ensureBuildFiles()
                val zipFile = File(cacheDir, "proj.zip")
                zipDir(File(projectPath), zipFile)

                var zipPath: String? = null
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "${projectName}.zip")
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/TermuxBuilder")
                    }
                    val uri = contentResolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            zipFile.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                zipPath = cursor.getString(0)
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (zipPath == null) {
                    try {
                        val dlFile = File("/storage/emulated/0/Download/TermuxBuilder", "${projectName}.zip")
                        dlFile.parentFile?.mkdirs()
                        zipFile.copyTo(dlFile, overwrite = true)
                        zipPath = dlFile.absolutePath
                    } catch (_: Exception) {}
                }

                if (zipPath == null) {
                    runOnUiThread {
                        btnBuild.isEnabled = true
                        btnBuild.text = "编译"
                        Toast.makeText(this@ProjectActivity, "无法写入 Download 目录", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val targetDir = "/data/data/com.termux/files/home/projects/${projectName}"
                val D = "\$"
                val script = "#!/data/data/com.termux/files/usr/bin/bash\n" +
                    "DIR=\"$targetDir\"\n" +
                    "mkdir -p \"${D}DIR\" && cd \"${D}DIR\" || exit 1\n" +
                    "echo '>>> 解压项目...'\n" +
                    "unzip -o \"$zipPath\"\n" +
                    "echo '>>> 替换 Gradle 镜像源...'\n" +
                    "sed -i 's|services.gradle.org/distributions|mirrors.cloud.tencent.com/gradle|' gradle/wrapper/gradle-wrapper.properties\n" +
                    "echo '>>> 开始编译...'\n" +
                    "chmod +x gradlew 2>/dev/null\n" +
                    "./gradlew assembleDebug\n" +
                    "echo ''\n" +
                    "echo '>>> 编译完成，复制 APK 到 Download...'\n" +
                    "apk=\$(find app/build/outputs/apk -name '*.apk' 2>/dev/null | head -1)\n" +
                    "if [ -n \"${D}apk\" ]; then\n" +
                    "    cp \"${D}apk\" /sdcard/Download/${projectName}.apk && echo \"APK: /sdcard/Download/${projectName}.apk\"\n" +
                    "    termux-open \"${D}apk\" 2>/dev/null\n" +
                    "else\n" +
                    "    echo '未找到 APK'\n" +
                    "fi\n" +
                    "echo ''\n"

                runOnUiThread {
                    btnBuild.isEnabled = true
                    btnBuild.text = "编译"

                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("build", script))
                    } catch (_: Exception) {}

                    val builder = android.app.AlertDialog.Builder(this)
                    builder.setTitle("编译就绪")
                    builder.setMessage("项目已导出：\n$zipPath\n\n在 Termux 粘贴执行即可")
                    val input = android.widget.EditText(this)
                    input.setText(script)
                    input.setTextIsSelectable(true)
                    input.setHorizontallyScrolling(true)
                    input.setTextColor(0xFF00E676.toInt())
                    input.setBackgroundColor(0xFF111111.toInt())
                    input.textSize = 10f
                    input.minLines = 3
                    input.maxLines = 8
                    builder.setView(input)
                    builder.setPositiveButton("打开 Termux") { _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                setClassName("com.termux", "com.termux.app.TermuxActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(this@ProjectActivity, "未安装 Termux", Toast.LENGTH_SHORT).show()
                        }
                    }
                    builder.setNegativeButton("关闭", null)
                    builder.setCancelable(true)
                    builder.show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnBuild.isEnabled = true
                    btnBuild.text = "编译"
                    Toast.makeText(this, "打包失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun createNewFile() {
        val rel = if (isAtRoot()) "/" else currentPath.removePrefix(projectPath)
        val input = android.widget.EditText(this)
        input.hint = "输入文件名，如 dialog_activation.xml"
        input.setTextColor(0xFFFFFFFF.toInt())
        input.setHintTextColor(0xFF888888.toInt())

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("新建文件 @ $rel")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newFile = File(currentPath, name)
                if (newFile.exists()) {
                    Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    newFile.parentFile?.mkdirs()
                    newFile.createNewFile()
                    loadFiles()
                    Toast.makeText(this, "已创建: $name", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@ProjectActivity, EditorActivity::class.java)
                    intent.putExtra("file_path", newFile.absolutePath)
                    intent.putExtra("file_name", newFile.name)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openFile(file: File) {
        val intent = Intent(this@ProjectActivity, EditorActivity::class.java)
        intent.putExtra("file_path", file.absolutePath)
        intent.putExtra("file_name", file.name)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (!navigateUp()) {
            super.onBackPressed()
        }
    }

    inner class FileAdapter(private val items: List<File>) :
        RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        private val hasParent: Boolean get() = currentPath != projectPath
        private val hasQuick: Boolean get() = currentPath == projectPath
        private val quickItems: List<String> by lazy { if (hasQuick) resolveQuickFiles() else emptyList() }

        private fun parentOffset(): Int = if (hasParent) 1 else 0
        private fun quickCount(): Int = if (hasQuick) quickItems.size else 0

        override fun getItemViewType(position: Int): Int {
            if (hasParent && position == 0) return TYPE_PARENT
            if (hasQuick) {
                val qi = if (hasParent) position - 1 else position
                if (qi < quickItems.size) return TYPE_QUICK
            }
            val fileIdx = position - parentOffset() - quickCount()
            val file = items[fileIdx]
            return if (file.isDirectory) TYPE_DIR else TYPE_FILE
        }

        override fun getItemCount(): Int {
            return parentOffset() + quickCount() + items.size
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.file_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_PARENT) {
                holder.nameText.text = "\uD83D\uDCC2 .."
                holder.itemView.setOnClickListener { navigateUp() }
                return
            }

            if (getItemViewType(position) == TYPE_QUICK) {
                val qi = if (hasParent) position - 1 else position
                val quickPath = quickItems[qi]
                val file = File(projectPath, quickPath)
                holder.nameText.text = "\u2B50 $quickPath"
                holder.nameText.setTextColor(0xFFFFD700.toInt())
                holder.itemView.setOnClickListener { openFile(file) }
                return
            }

            val file = items[position - parentOffset() - quickCount()]
            val relPath = file.absolutePath.removePrefix(currentPath).removePrefix("/")
            val icon = if (file.isDirectory) "\uD83D\uDCC1 " else "\uD83D\uDCC4 "
            holder.nameText.text = icon + relPath

            holder.itemView.setOnClickListener {
                if (file.isDirectory) {
                    navigateTo(file)
                } else {
                    openFile(file)
                }
            }
        }
    }
}
