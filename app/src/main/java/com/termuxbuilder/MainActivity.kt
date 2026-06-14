package com.termuxbuilder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var projectList: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private val projects = mutableListOf<File>()

    companion object {
        private const val PERMISSION_CODE = 100

        fun getProjectsDir(): File {
            return File(Environment.getExternalStorageDirectory(), "TermuxBuilder/projects")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectList = findViewById(R.id.project_list)
        emptyView = findViewById(R.id.empty_view)
        fab = findViewById(R.id.fab_create)

        projectList.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            if (checkStoragePermission()) {
                startActivity(Intent(this, CreateProjectActivity::class.java))
            }
        }

        if (checkStoragePermission()) {
            loadProjects()
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (write != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_CODE
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadProjects()
            } else {
                Snackbar.make(fab, "需要存储权限才能创建项目", Snackbar.LENGTH_INDEFINITE)
                    .setAction("授予") { checkStoragePermission() }.show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkStoragePermission()) {
            loadProjects()
        }
    }

    private fun loadProjects() {
        val dir = getProjectsDir()
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
                val intent = Intent(this@MainActivity, ProjectActivity::class.java)
                intent.putExtra("project_path", project.absolutePath)
                intent.putExtra("project_name", project.name)
                startActivity(intent)
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
