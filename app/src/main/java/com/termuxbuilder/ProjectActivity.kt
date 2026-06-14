package com.termuxbuilder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private lateinit var btnExport: Button
    private var files = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project)

        projectPath = intent.getStringExtra("project_path") ?: return finish()
        projectName = intent.getStringExtra("project_name") ?: ""

        title = "项目: $projectName"

        fileList = findViewById(R.id.file_list)
        btnBuild = findViewById(R.id.btn_build)
        btnExport = findViewById(R.id.btn_export)

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
        collectFiles(dir, "")
        files.sortBy { if (it.isDirectory) 0 else 1 }
        fileList.adapter = FileAdapter(files)
    }

    private fun collectFiles(dir: File, prefix: String) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.name == "build" || file.name == ".gradle" || file.name.startsWith(".")) return@forEach
            files.add(file)
            if (file.isDirectory) {
                collectFiles(file, prefix + "  ")
            }
        }
    }

    private fun triggerBuild() {
        val buildScript = File(projectPath, "build.sh")
        val script = """#!/data/data/com.termux/files/usr/bin/bash
cd "${projectPath}"
echo ">> 开始编译: $projectName"
echo ""
./gradlew assembleDebug 2>&1
echo ""
echo ">> 编译完成。APK 路径:"
find app/build/outputs/apk -name "*.apk" 2>/dev/null
"""
        buildScript.writeText(script)
        buildScript.setExecutable(true)

        val cmd = "cd \"${projectPath}\" && chmod +x build.sh && ./build.sh"

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("build", cmd))

        Toast.makeText(this, "编译命令已复制到剪贴板", Toast.LENGTH_LONG).show()

        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.termux", "com.termux.app.TermuxActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "未检测到 Termux，请先安装 Termux (F-Droid版)", Toast.LENGTH_LONG).show()
        }
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
