package com.termuxbuilder

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var filePath: String
    private var modified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        filePath = intent.getStringExtra("file_path") ?: return finish()
        val fileName = intent.getStringExtra("file_name") ?: ""
        title = fileName

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editor = findViewById(R.id.code_editor)
        editor.typeface = Typeface.MONOSPACE
        editor.setTextSize(13f)
        editor.setHorizontallyScrolling(true)

        try {
            editor.setText(File(filePath).readText())
            val bgColor = ContextCompat.getColor(this, R.color.editor_bg)
            editor.setBackgroundColor(bgColor)
            val textColor = ContextCompat.getColor(this, R.color.editor_text)
            editor.setTextColor(textColor)
        } catch (e: Exception) {
            Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
            finish()
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { modified = true }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun save() {
        try {
            File(filePath).writeText(editor.text.toString())
            modified = false
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "保存").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            save()
            return true
        }
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (modified) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("有未保存的更改")
                .setPositiveButton("保存并退出") { _, _ ->
                    save()
                    super.onBackPressed()
                }
                .setNeutralButton("放弃") { _, _ -> super.onBackPressed() }
                .setNegativeButton("取消", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
