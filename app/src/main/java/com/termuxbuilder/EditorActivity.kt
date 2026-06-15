package com.termuxbuilder

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Typeface
import android.text.Spannable
import android.graphics.Color
import androidx.core.content.ContextCompat
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var filePath: String
    private var modified = false

    // Search bar views
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var replaceBar: LinearLayout
    private lateinit var replaceInput: EditText
    private var searchIndex = -1

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
            editor.selectAll()
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

        // Search bar
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        replaceBar = findViewById(R.id.replace_bar)
        replaceInput = findViewById(R.id.replace_input)

        // Toolbar buttons
        findViewById<TextView>(R.id.btn_select_all).setOnClickListener { editor.selectAll() }
        findViewById<TextView>(R.id.btn_cut).setOnClickListener { cut() }
        findViewById<TextView>(R.id.btn_copy).setOnClickListener { copy() }
        findViewById<TextView>(R.id.btn_paste).setOnClickListener { paste() }
        findViewById<TextView>(R.id.btn_search).setOnClickListener { toggleSearch() }
        findViewById<TextView>(R.id.btn_replace).setOnClickListener { toggleReplace() }
        findViewById<TextView>(R.id.btn_undo).setOnClickListener {
            editor.text?.let { if (it is android.text.Editable) editor.onTextContextMenuItem(android.R.id.undo) }
        }
        findViewById<TextView>(R.id.btn_redo).setOnClickListener {
            editor.text?.let { if (it is android.text.Editable) editor.onTextContextMenuItem(android.R.id.redo) }
        }

        // Search bar buttons
        findViewById<TextView>(R.id.btn_search_prev).setOnClickListener { searchPrev() }
        findViewById<TextView>(R.id.btn_search_next).setOnClickListener { searchNext() }
        findViewById<TextView>(R.id.btn_search_close).setOnClickListener { closeSearch() }

        // Search input: search on enter
        searchInput.setOnEditorActionListener { _, _, _ ->
            searchNext()
            true
        }

        // Replace bar buttons
        findViewById<TextView>(R.id.btn_replace_one).setOnClickListener { replaceOne() }
        findViewById<TextView>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        findViewById<TextView>(R.id.btn_replace_close).setOnClickListener { closeReplace() }
    }

    // ---- clipboard helpers ----
    private fun cut() {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        if (start != end) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", editor.text.subSequence(start, end)))
            editor.text.replace(start, end, "")
        }
    }

    private fun copy() {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        if (start != end) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", editor.text.subSequence(start, end)))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未选中文本", Toast.LENGTH_SHORT).show()
        }
    }

    private fun paste() {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text ?: return
            val start = editor.selectionStart
            val end = editor.selectionEnd
            editor.text.replace(Math.min(start, end), Math.max(start, end), text)
        }
    }

    // ---- search ----
    private fun toggleSearch() {
        if (searchBar.visibility == View.VISIBLE) {
            closeSearch()
            return
        }
        replaceBar.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchIndex = -1
    }

    private fun closeSearch() {
        searchBar.visibility = View.GONE
        clearSearchHighlights()
    }

    private fun searchNext() {
        val query = searchInput.text.toString()
        if (query.isEmpty()) return

        clearSearchHighlights()
        val text = editor.text.toString()
        val startIdx = if (searchIndex >= 0) searchIndex + 1 else 0
        val idx = text.indexOf(query, startIdx, true)
        if (idx >= 0) {
            searchIndex = idx
            highlightMatch(idx, query.length)
        } else {
            // Wrap around
            val idx2 = text.indexOf(query, 0, true)
            if (idx2 >= 0) {
                searchIndex = idx2
                highlightMatch(idx2, query.length)
            } else {
                Toast.makeText(this, "未找到", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchPrev() {
        val query = searchInput.text.toString()
        if (query.isEmpty()) return

        clearSearchHighlights()
        val text = editor.text.toString()
        val startIdx = if (searchIndex > 0) searchIndex - 1 else text.length - 1
        val idx = text.lastIndexOf(query, startIdx, true)
        if (idx >= 0) {
            searchIndex = idx
            highlightMatch(idx, query.length)
        } else {
            val idx2 = text.lastIndexOf(query, text.length - 1, true)
            if (idx2 >= 0) {
                searchIndex = idx2
                highlightMatch(idx2, query.length)
            } else {
                Toast.makeText(this, "未找到", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun highlightMatch(idx: Int, len: Int) {
        editor.setSelection(idx, idx + len)
        val span = editor.text as Spannable
        val existing = span.getSpans(0, span.length, android.text.style.BackgroundColorSpan::class.java)
        existing.forEach { span.removeSpan(it) }
        if (idx + len <= span.length) {
            span.setSpan(
                android.text.style.BackgroundColorSpan(Color.parseColor("#FF6D00")),
                idx, idx + len,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun clearSearchHighlights() {
        val span = editor.text as? Spannable ?: return
        val spans = span.getSpans(0, span.length, android.text.style.BackgroundColorSpan::class.java)
        spans.forEach { span.removeSpan(it) }
    }

    // ---- replace ----
    private fun toggleReplace() {
        if (replaceBar.visibility == View.VISIBLE) {
            closeReplace()
            return
        }
        searchBar.visibility = View.GONE
        replaceBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchIndex = -1
    }

    private fun closeReplace() {
        replaceBar.visibility = View.GONE
        clearSearchHighlights()
    }

    private fun replaceOne() {
        val find = searchInput.text.toString()
        val replace = replaceInput.text.toString()
        if (find.isEmpty()) return

        val selStart = editor.selectionStart
        val selEnd = editor.selectionEnd
        if (selStart < selEnd) {
            val selected = editor.text.subSequence(selStart, selEnd).toString()
            if (selected.equals(find, true)) {
                editor.text.replace(selStart, selEnd, replace)
                searchNext()
                return
            }
        }
        searchNext()
    }

    private fun replaceAll() {
        val find = searchInput.text.toString()
        val replace = replaceInput.text.toString()
        if (find.isEmpty()) return

        val text = editor.text.toString()
        val newText = text.replace(find, replace, true)
        var count = 0
        var idx = 0
        while (true) {
            val i = text.indexOf(find, idx, true)
            if (i < 0) break
            count++
            idx = i + find.length
        }

        editor.setText(newText)
        Toast.makeText(this, "已替换 $count 处", Toast.LENGTH_SHORT).show()
    }

    // ---- save ----
    private fun save() {
        try {
            File(filePath).writeText(editor.text.toString())
            modified = false
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${"$"}{e.message}", Toast.LENGTH_SHORT).show()
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
