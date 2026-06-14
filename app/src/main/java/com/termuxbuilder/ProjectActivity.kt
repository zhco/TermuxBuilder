package com.termuxbuilder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    private fun triggerBuild() {
        Toast.makeText(this, "正在准备编译...", Toast.LENGTH_SHORT).show()

        val buildDir = "/data/local/tmp/tb_$projectName"
        val buildCmd = "cd \"$buildDir\" && chmod +x gradlew 2>/dev/null && ./gradlew assembleDebug && echo '' && echo '>> APK:' && find app/build/outputs/apk -name '*.apk'"

        // Try direct copy first
        var ok = false
        try {
            val dest = File(buildDir)
            dest.deleteRecursively()
            File(projectPath).copyRecursively(dest, overwrite = true)
            ok = true
        } catch (_: Exception) {}

        if (ok) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("build", buildCmd))
            Toast.makeText(this, "命令已复制，在 Termux 粘贴执行", Toast.LENGTH_LONG).show()
        } else {
            // Fallback: create tar and have Termux extract it
            val tarFile = File("/data/local/tmp", "tb_${projectName}.tar")
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("tar", "-cf", tarFile.absolutePath, "-C", File(projectPath).parent!!, File(projectPath).name))
                proc.waitFor()
                val tarcmd = "mkdir -p \"$buildDir\" && tar -xf \"${tarFile.absolutePath}\" -C \"$buildDir\" && $buildCmd"
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("build", tarcmd))
                Toast.makeText(this, "命令已复制，在 Termux 粘贴执行", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("build", "echo '错误: 无法准备项目文件'"))
                Toast.makeText(this, "准备失败: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Launch Termux
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.termux", "com.termux.app.TermuxActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
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
