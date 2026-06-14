package com.termuxbuilder

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class CreateProjectActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var pkgInput: EditText
    private lateinit var minSdkSpinner: Spinner
    private lateinit var templateSpinner: Spinner
    private lateinit var createBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_project)

        nameInput = findViewById(R.id.input_project_name)
        pkgInput = findViewById(R.id.input_package_name)
        minSdkSpinner = findViewById(R.id.spinner_min_sdk)
        templateSpinner = findViewById(R.id.spinner_template)
        createBtn = findViewById(R.id.btn_create)

        ArrayAdapter.createFromResource(this, R.array.min_sdk_values, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            .let { minSdkSpinner.adapter = it }

        ArrayAdapter.createFromResource(this, R.array.template_names, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            .let { templateSpinner.adapter = it }

        createBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val pkg = pkgInput.text.toString().trim()

            if (name.isEmpty()) { Toast.makeText(this, "请输入项目名称", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (pkg.isEmpty()) { Toast.makeText(this, "请输入包名", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"))) {
                Toast.makeText(this, "包名格式不正确", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }

            val minSdk = minSdkSpinner.selectedItem.toString().toInt()
            val templateIdx = templateSpinner.selectedItemPosition
            val projectDir = File(filesDir, "projects/$name")

            if (projectDir.exists()) {
                Toast.makeText(this, "项目 $name 已存在", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }

            ProjectTemplate.create(this, projectDir, name, pkg, minSdk, templateIdx)
            Toast.makeText(this, "项目创建成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
