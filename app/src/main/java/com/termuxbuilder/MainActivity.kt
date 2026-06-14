package com.termuxbuilder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sections = listOf(
            Section(1, "环境搭建", "pkg install openjdk-17 android-sdk gradle", "Termux 安装 Android 编译环境全流程"),
            Section(2, "常用命令", "gradle assembleDebug", "编译、签名、安装、调试命令速查"),
            Section(3, "项目模板", "从零创建 Android 项目", "在 Termux 中初始化 Gradle 项目结构"),
            Section(4, "SDK 管理", "sdkmanager 使用指南", "安装/更新 SDK Platform、Build-Tools"),
            Section(5, "常见错误", "排查 Gradle 编译报错", "SDK 找不到、依赖下载失败、签名错误"),
            Section(6, "性能优化", "gradle.properties 调优", "减少编译时间、降低内存占用")
        )

        findViewById<RecyclerView>(R.id.section_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = SectionAdapter(sections) { section ->
                startActivity(Intent(this@MainActivity, GuideActivity::class.java).apply {
                    putExtra("section_id", section.id)
                    putExtra("section_title", section.title)
                })
            }
        }
    }
}

data class Section(val id: Int, val title: String, val subtitle: String, val desc: String)

class SectionAdapter(
    private val items: List<Section>,
    private val onClick: (Section) -> Unit
) : RecyclerView.Adapter<SectionAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card)
        val title: TextView = view.findViewById(R.id.card_title)
        val subtitle: TextView = view.findViewById(R.id.card_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_section, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.title.text = "${item.id}. ${item.title}"
        holder.subtitle.text = item.subtitle
        holder.card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
