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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectActivity : AppCompatActivity() {

    private lateinit var projectPath: String
    private lateinit var projectName: String
    private lateinit var fileList: RecyclerView
    private lateinit var btnBuild: Button
    private var files = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project)

        projectPath = intent.getStringExtra("project_path") ?: return finish()
        projectName = intent.getStringExtra("project_name") ?: ""

        title = "项目: $projectName"

        fileList = findViewById(R.id.file_list)
        btnBuild = findViewById(R.id.btn_build)

        fileList.layoutManager = LinearLayoutManager(this)
        loadFiles()

        btnBuild.setOnClickListener { triggerBuild() }
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    private fun loadFiles() {
        val dir = File(projectPath)
        files.clear()
        collectFiles(dir)
        files.sortBy { if (it.isDirectory) 0 else 1 }
        fileList.adapter = FileAdapter(files)
    }

    private fun collectFiles(dir: File) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.name == "build" || file.name == ".gradle" || file.name.startsWith(".")) return@forEach
            files.add(file)
            if (file.isDirectory) collectFiles(file)
        }
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

    private fun triggerBuild() {
        Toast.makeText(this, "正在打包项目...", Toast.LENGTH_SHORT).show()

        btnBuild.isEnabled = false
        btnBuild.text = "打包中..."

        Thread {
            try {
                val zipFile = File(cacheDir, "proj.zip")
                zipDir(File(projectPath), zipFile)

                // Try multiple locations for the zip
                var zipPath: String? = null
                val cacheDest = File(cacheDir, "${projectName}.zip")
                try {
                    zipFile.copyTo(cacheDest, overwrite = true)
                    zipPath = cacheDest.absolutePath
                } catch (e1: Exception) {
                    try {
                        val dlDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), "TermuxBuilder")
                        dlDir.mkdirs()
                        val dlFile = File(dlDir, "${projectName}.zip")
                        zipFile.copyTo(dlFile, overwrite = true)
                        zipPath = dlFile.absolutePath
                    } catch (e2: Exception) {
                        runOnUiThread {
                            btnBuild.isEnabled = true
                            btnBuild.text = "编译"
                            Toast.makeText(this@ProjectActivity, "写入失败: ${e1.message}", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                }

                val targetDir = "/data/data/com.termux/files/home/projects/${projectName}"
                val D = "${'$'}"
                val script = "#!/data/data/com.termux/files/usr/bin/bash\n" +
                    "DIR=\"$targetDir\"\n" +
                    "mkdir -p \"${D}DIR\" && cd \"${D}DIR\" || exit 1\n" +
                    "echo '>>> 解压项目...'\n" +
                    "unzip -o \"$zipPath\"\n" +
                    "echo '>>> 开始编译...'\n" +
                    "chmod +x gradlew 2>/dev/null\n" +
                    "./gradlew assembleDebug\n" +
                    "echo ''\n" +
                    "echo '>>> 编译完成。APK:'\n" +
                    "find app/build/outputs/apk -name '*.apk' 2>/dev/null\n" +
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
                    builder.setMessage("项目已导出：\n$zipPath\n\n命令已复制，在 Termux 粘贴执行即可")
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

    inner class FileAdapter(private val items: List<File>) :
        RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.file_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = items[position]
            val relPath = file.absolutePath.removePrefix(projectPath).removePrefix("/")
            val icon = if (file.isDirectory) "\uD83D\uDCC1 " else "\uD83D\uDCC4 "
            holder.nameText.text = icon + relPath

            holder.itemView.setOnClickListener {
                if (file.isDirectory) return@setOnClickListener
                val intent = Intent(this@ProjectActivity, EditorActivity::class.java)
                intent.putExtra("file_path", file.absolutePath)
                intent.putExtra("file_name", file.name)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
