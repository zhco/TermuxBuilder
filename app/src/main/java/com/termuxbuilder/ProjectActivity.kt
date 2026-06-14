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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

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
        Toast.makeText(this, "正在打包项目...", Toast.LENGTH_SHORT).show()

        btnBuild.isEnabled = false
        btnBuild.text = "打包中..."

        Thread {
            try {
                // Create tar.gz of project
                val tarFile = File(cacheDir, "proj.tar.gz")
                val projectDir = File(projectPath)
                tarDir(projectDir, tarFile)

                val tarBytes = tarFile.readBytes()
                val b64 = Base64.encodeToString(tarBytes, Base64.NO_WRAP)

                val targetDir = "/data/data/com.termux/files/home/projects/$projectName"
                val script = """#!/data/data/com.termux/files/usr/bin/bash
DIR="$targetDir"
mkdir -p "\$DIR" && cd "\$DIR" || exit 1
echo '>>> 解压项目...'
base64 -d << 'B64EOF' | gunzip | tar -xf -
$b64
B64EOF
echo '>>> 开始编译...'
echo ''
chmod +x gradlew 2>/dev/null
./gradlew assembleDebug
echo ''
echo '>>> 编译完成。APK:'
find app/build/outputs/apk -name '*.apk' 2>/dev/null
echo ''
"""

                runOnUiThread {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("build", script))
                    btnBuild.isEnabled = true
                    btnBuild.text = "编译"
                    Toast.makeText(this, "已复制，长按粘贴到 Termux 执行", Toast.LENGTH_LONG).show()

                    try {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            setClassName("com.termux", "com.termux.app.TermuxActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
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

    private fun tarDir(srcDir: File, destFile: File) {
        val baos = ByteArrayOutputStream()
        val gzos = GZIPOutputStream(baos)
        tarDirRecursive(gzos, srcDir, "")
        gzos.close()
        destFile.writeBytes(baos.toByteArray())
    }

    private fun tarDirRecursive(out: java.io.OutputStream, dir: File, prefix: String) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val relPath = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                tarDirRecursive(out, file, relPath)
            } else {
                val header = buildTarHeader(relPath, file.length())
                out.write(header)
                file.inputStream().use { input ->
                    val buf = ByteArray(4096)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                }
                // Padding to 512
                val pad = (512 - (file.length() % 512)) % 512
                if (pad > 0) out.write(ByteArray(pad.toInt()))
            }
        }
    }

    private fun buildTarHeader(name: String, size: Long): ByteArray {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))
        // Mode: 0644
        "0000644\u0000".toByteArray().copyInto(header, 100)
        // UID/GID: 0
        "0000000\u0000".toByteArray().copyInto(header, 108)
        "0000000\u0000".toByteArray().copyInto(header, 116)
        // Size (octal)
        val sizeOctal = String.format("%011o\u0000", size).toByteArray()
        sizeOctal.copyInto(header, 124)
        // Mtime
        val mtime = String.format("%011o\u0000", System.currentTimeMillis() / 1000).toByteArray()
        mtime.copyInto(header, 136)
        // Type flag: '0' for regular file
        header[156] = '0'.code.toByte()
        // Calculate checksum
        for (i in 148..155) header[i] = ' '.code.toByte()
        var sum = 0L
        for (b in header) sum += b.toLong() and 0xFF
        val cksum = String.format("%06o\u0000 ", sum).toByteArray()
        cksum.copyInto(header, 148)
        return header
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
