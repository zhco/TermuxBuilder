package com.termuxbuilder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var projectList: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var btnOpenFolder: Button
    private lateinit var btnOpenZip: Button
    private val projects = mutableListOf<File>()

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { importFolder(it) }
    }

    private val zipPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importZip(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectList = findViewById(R.id.project_list)
        emptyView = findViewById(R.id.empty_view)
        fab = findViewById(R.id.fab_create)
        btnOpenFolder = findViewById(R.id.btn_open_folder)
        btnOpenZip = findViewById(R.id.btn_open_zip)

        projectList.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            startActivity(Intent(this, CreateProjectActivity::class.java))
        }

        btnOpenFolder.setOnClickListener { openFolder() }
        btnOpenZip.setOnClickListener { openZip() }
    }

    private fun openFolder() {
        try {
            folderPicker.launch(null)
        } catch (e: Exception) {
            showPathInputDialog()
        }
    }

    private fun openZip() {
        try {
            zipPicker.launch("application/zip")
        } catch (e: Exception) {
            zipPicker.launch("*/*")
        }
    }

    private fun showPathInputDialog() {
        val input = android.widget.EditText(this)
        input.hint = "/sdcard/Download/MyProject"
        input.setTextColor(0xFFFFFFFF.toInt())
        input.setHintTextColor(0xFF444444.toInt())
        input.setBackgroundColor(0xFF1A1A1A.toInt())
        input.setPadding(24, 24, 24, 24)

        AlertDialog.Builder(this)
            .setTitle("输入项目文件夹路径")
            .setView(input)
            .setPositiveButton("打开") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    val srcDir = File(path)
                    if (srcDir.exists() && srcDir.isDirectory) {
                        importLocalFolder(srcDir)
                    } else {
                        Toast.makeText(this, "路径不存在或不是文件夹", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importLocalFolder(srcDir: File) {
        val destDir = File(filesDir, "projects/${srcDir.name}")
        if (destDir.exists()) destDir.deleteRecursively()
        srcDir.copyRecursively(destDir)
        loadProjects()
        openProject(destDir)
        Toast.makeText(this, "已导入: ${srcDir.name}", Toast.LENGTH_SHORT).show()
    }

    private fun importFolder(treeUri: Uri) {
        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val docFile = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val folderName = docFile.name ?: "imported_${System.currentTimeMillis()}"
        val destDir = File(filesDir, "projects/$folderName")
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()

        copyDocumentTree(docFile, destDir)
        loadProjects()
        openProject(destDir)
        Toast.makeText(this, "已导入: $folderName", Toast.LENGTH_SHORT).show()
    }

    private fun copyDocumentTree(srcDoc: DocumentFile, destDir: File) {
        srcDoc.listFiles().forEach { child ->
            val childName = child.name ?: return@forEach
            if (child.isDirectory) {
                val subDir = File(destDir, childName)
                subDir.mkdirs()
                copyDocumentTree(child, subDir)
            } else {
                val destFile = File(destDir, childName)
                try {
                    contentResolver.openInputStream(child.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun importZip(uri: Uri) {
        val fileName = getFileName(uri) ?: "imported_${System.currentTimeMillis()}"
        val projectName = fileName.replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")
        val destDir = File(filesDir, "projects/$projectName")

        if (destDir.exists()) {
            AlertDialog.Builder(this)
                .setTitle("项目已存在")
                .setMessage("$projectName 已存在，是否覆盖？")
                .setPositiveButton("覆盖") { _, _ ->
                    destDir.deleteRecursively()
                    destDir.mkdirs()
                    extractZipAndOpen(uri, destDir)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            destDir.mkdirs()
            extractZipAndOpen(uri, destDir)
        }
    }

    private fun extractZipAndOpen(uri: Uri, destDir: File) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Handle single root folder inside zip
            val children = destDir.listFiles()
            if (children != null && children.size == 1 && children[0].isDirectory) {
                val innerDir = children[0]
                val tmpDir = File(destDir.parentFile, "${destDir.name}_tmp")
                innerDir.renameTo(tmpDir)
                destDir.deleteRecursively()
                tmpDir.renameTo(destDir)
            }

            loadProjects()
            openProject(destDir)
            Toast.makeText(this, "导入成功: ${destDir.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            destDir.deleteRecursively()
        }
    }

    private fun openProject(projectDir: File) {
        val intent = Intent(this, ProjectActivity::class.java)
        intent.putExtra("project_path", projectDir.absolutePath)
        intent.putExtra("project_name", projectDir.name)
        startActivity(intent)
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    private fun loadProjects() {
        val dir = File(filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        projects.clear()
        dir.listFiles()?.filter { it.isDirectory }?.let { projects.addAll(it) }
        projects.sortByDescending { it.lastModified() }

        if (projects.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            projectList.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            projectList.visibility = View.VISIBLE
            projectList.adapter = ProjectAdapter(projects)
        }
    }

    inner class ProjectAdapter(private val items: List<File>) :
        RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.project_name)
            val pkgText: TextView = view.findViewById(R.id.project_package)
            val dateText: TextView = view.findViewById(R.id.project_date)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val project = items[position]
            val gradleFile = File(project, "app/build.gradle.kts")
            val pkg = if (gradleFile.exists()) {
                val content = gradleFile.readText()
                val match = Regex("""applicationId\s*=\s*"([^"]+)"""").find(content)
                match?.groupValues?.get(1) ?: "unknown"
            } else "unknown"

            holder.nameText.text = project.name
            holder.pkgText.text = pkg
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            holder.dateText.text = date.format(java.util.Date(project.lastModified()))

            holder.itemView.setOnClickListener {
                openProject(project)
            }

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("删除项目")
                    .setMessage("确定删除 ${project.name}？")
                    .setPositiveButton("删除") { _, _ ->
                        project.deleteRecursively()
                        loadProjects()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
