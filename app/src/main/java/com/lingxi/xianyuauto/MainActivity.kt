package com.lingxi.xianyuauto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        
        val title = TextView(this).apply {
            text = "🔮 灵犀助手"
            textSize = 24f
        }
        
        val status = TextView(this).apply {
            text = if (isAccessibilityEnabled()) "✅ 无障碍已开启" else "❌ 请开启无障碍服务"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        
        val btnAccessibility = Button(this).apply {
            text = "开启无障碍服务"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        
        val info = TextView(this).apply {
            text = """
                |HTTP控制端口: 8848
                |控制地址: http://localhost:8848
                |
                |API接口:
                |GET  /status     - 服务状态
                |GET  /tree       - 获取UI树
                |GET  /click?text=xxx - 点击文本
                |GET  /click?x=100&y=200 - 点击坐标
                |POST /input      - 输入文字
                |POST /scroll     - 滑动
                |GET  /back       - 返回
                |POST /find       - 查找元素
                |POST /gesture    - 手势操作
            """.trimMargin()
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        
        layout.addView(title)
        layout.addView(status)
        layout.addView(btnAccessibility)
        layout.addView(info)
        
        setContentView(layout)
    }
    
    private fun isAccessibilityEnabled(): Boolean {
        val serviceName = "${packageName}/${AutoService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }
}
