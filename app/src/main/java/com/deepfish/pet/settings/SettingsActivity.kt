package com.deepfish.pet.settings

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepfish.pet.ApiKeyStore
import com.deepfish.pet.PetService
import com.deepfish.pet.PetSettings
import com.deepfish.pet.Prefs
import com.deepfish.pet.R
import com.deepfish.pet.accessibility.PhoneOperator
import com.deepfish.pet.gateway.GatewayController
import com.deepfish.pet.gateway.GatewayDeployManager
import com.deepfish.pet.gateway.GatewayNode
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var keyStatus: TextView
    private lateinit var providerGroup: RadioGroup

    private lateinit var chkAlwaysOnTop: CheckBox
    private lateinit var chkSound: CheckBox
    private lateinit var chkFreeWalk: CheckBox
    private lateinit var chkInteractiveZones: CheckBox
    private lateinit var chkSpicyLines: CheckBox
    private lateinit var chkTouchThrough: CheckBox

    private lateinit var scaleBar: SeekBar
    private lateinit var scaleOutput: TextView
    private lateinit var intensityBar: SeekBar
    private lateinit var intensityOutput: TextView

    private lateinit var chkGatewayEnabled: CheckBox
    private lateinit var gatewayHost: EditText
    private lateinit var gatewayPort: EditText
    private lateinit var gatewayToken: EditText
    private lateinit var gatewayStatus: TextView
    private lateinit var gatewayDeployStatus: TextView

    private val presets = mapOf(
        "pollinations" to (Prefs.DEFAULT_ENDPOINT to Prefs.DEFAULT_MODEL),
        "deepseek" to ("https://api.deepseek.com/chat/completions" to "deepseek-chat"),
        "custom" to ("" to "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        providerGroup = findViewById(R.id.provider_group)
        endpoint = findViewById(R.id.endpoint)
        model = findViewById(R.id.model)
        apiKey = findViewById(R.id.api_key)
        keyStatus = findViewById(R.id.key_status)
        chkAlwaysOnTop = findViewById(R.id.chk_always_on_top)
        chkSound = findViewById(R.id.chk_sound)
        chkFreeWalk = findViewById(R.id.chk_free_walk)
        chkInteractiveZones = findViewById(R.id.chk_interactive_zones)
        chkSpicyLines = findViewById(R.id.chk_spicy_lines)
        chkTouchThrough = findViewById(R.id.chk_touch_through)
        scaleBar = findViewById(R.id.scale_bar)
        scaleOutput = findViewById(R.id.scale_output)
        intensityBar = findViewById(R.id.intensity_bar)
        intensityOutput = findViewById(R.id.intensity_output)
        chkGatewayEnabled = findViewById(R.id.chk_gateway_enabled)
        gatewayHost = findViewById(R.id.gateway_host)
        gatewayPort = findViewById(R.id.gateway_port)
        gatewayToken = findViewById(R.id.gateway_token)
        gatewayStatus = findViewById(R.id.gateway_status)
        gatewayDeployStatus = findViewById(R.id.gateway_deploy_status)

        findViewById<Button>(R.id.btn_gateway_ping).setOnClickListener { pingGateway() }
        findViewById<Button>(R.id.btn_gateway_connect).setOnClickListener { saveGateway() }
        findViewById<Button>(R.id.btn_gateway_disconnect).setOnClickListener {
            GatewayController.stop()
            updateGatewayStatus()
        }
        findViewById<Button>(R.id.btn_gateway_start).setOnClickListener {
            lifecycleScope.launch {
                runCatching {
                    if (!GatewayDeployManager.isDeployed(this@SettingsActivity)) {
                        GatewayDeployManager.deploy(this@SettingsActivity)
                    }
                    GatewayDeployManager.start(this@SettingsActivity)
                }.onFailure { e ->
                    gatewayDeployStatus.text = "内置 Gateway 启动失败: ${e.message}"
                }
                updateDeployStatus()
            }
        }
        findViewById<Button>(R.id.btn_gateway_stop).setOnClickListener {
            GatewayDeployManager.stop(this)
            updateDeployStatus()
        }

        lifecycleScope.launch {
            GatewayDeployManager.status.collect {
                updateDeployStatus()
            }
        }

        lifecycleScope.launch {
            GatewayController.state.collect {
                updateGatewayStatus()
            }
        }

        findViewById<Button>(R.id.btn_reset).setOnClickListener { selectProvider("pollinations", true) }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            PhoneOperator(this).openAccessibilitySettings()
        }

        providerGroup.setOnCheckedChangeListener { _, checkedId ->
            val id = when (checkedId) {
                R.id.radio_deepseek -> "deepseek"
                R.id.radio_custom -> "custom"
                else -> "pollinations"
            }
            selectProvider(id, false)
            updateKeyVisibility(id)
        }

        scaleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                scaleOutput.text = "${(progress + 60)}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        intensityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                intensityOutput.text = "${(progress + 50)}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        loadSettings()
    }

    private fun loadSettings() {
        val s = Prefs.settings(this)
        selectProvider(s.provider, false)
        updateKeyVisibility(s.provider)
        endpoint.setText(s.endpoint)
        model.setText(s.model)
        chkAlwaysOnTop.isChecked = s.alwaysOnTop
        chkSound.isChecked = s.sound
        chkFreeWalk.isChecked = s.freeWalk
        chkInteractiveZones.isChecked = s.interactiveZones
        chkSpicyLines.isChecked = s.spicyLines
        chkTouchThrough.isChecked = s.touchThrough
        scaleBar.progress = ((s.scale - 0.6f) * 20).toInt().coerceIn(0, 11)
        intensityBar.progress = ((s.behaviorIntensity - 0.5f) * 10).toInt().coerceIn(0, 13)
        scaleOutput.text = "${(s.scale * 100).toInt()}%"
        intensityOutput.text = "${(s.behaviorIntensity * 100).toInt()}%"
        keyStatus.text = if (ApiKeyStore.hasKey(this)) "已加密保存，留空即可保留" else "尚未保存 Key"
        loadGateway()
        updateGatewayStatus()
    }

    private fun loadGateway() {
        chkGatewayEnabled.isChecked = Prefs.gatewayEnabled(this)
        gatewayHost.setText(Prefs.gatewayHost(this))
        gatewayPort.setText(Prefs.gatewayPort(this).toString())
        gatewayToken.setText(Prefs.gatewayToken(this) ?: "")
    }

    private fun pingGateway() {
        val host = gatewayHost.text.toString().trim().ifEmpty { "127.0.0.1" }
        val port = gatewayPort.text.toString().trim().toIntOrNull() ?: 18789
        gatewayStatus.text = "检测中..."
        lifecycleScope.launch {
            val reachable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    java.net.Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(host, port), 5000)
                    }
                }.isSuccess
            }
            gatewayStatus.text = if (reachable) {
                "端口 $port 可达，Gateway 服务正在运行"
            } else {
                "无法连接 $host:$port（请确认 Termux 中已启动 Gateway）"
            }
        }
    }

    private fun saveGateway() {
        val host = gatewayHost.text.toString().trim().ifEmpty { "127.0.0.1" }
        val port = gatewayPort.text.toString().trim().toIntOrNull() ?: 18789
        val token = gatewayToken.text.toString().trim().takeIf { it.isNotEmpty() }
        Prefs.saveGatewayConfig(this, host, port, token)
        Prefs.setGatewayEnabled(this, chkGatewayEnabled.isChecked)
        GatewayController.ensureStarted(this)
        GatewayController.start(this, host, port, token)
        updateGatewayStatus()
    }

    private fun updateGatewayStatus() {
        when (val s = GatewayController.state.value) {
            is GatewayNode.ConnectionState.Connected ->
                gatewayStatus.text = "已连接（server ${s.serverVersion}）"
            GatewayNode.ConnectionState.Connecting ->
                gatewayStatus.text = "连接中..."
            GatewayNode.ConnectionState.Disconnected ->
                gatewayStatus.text = "未连接"
            is GatewayNode.ConnectionState.Error ->
                gatewayStatus.text = "连接失败：${s.message}"
        }
    }

    private fun updateDeployStatus() {
        val st = GatewayDeployManager.status.value
        gatewayDeployStatus.text = when (st.state) {
            GatewayDeployManager.DeployState.NotDeployed -> "内置 Gateway：未部署"
            GatewayDeployManager.DeployState.Extracting -> "内置 Gateway：解压中… ${(st.progress * 100).toInt()}%"
            GatewayDeployManager.DeployState.Ready -> "内置 Gateway：就绪，未启动"
            GatewayDeployManager.DeployState.Starting -> "内置 Gateway：启动中…"
            GatewayDeployManager.DeployState.Running -> "内置 Gateway：运行中（pid ${st.pid}）"
            GatewayDeployManager.DeployState.Stopping -> "内置 Gateway：停止中…"
            GatewayDeployManager.DeployState.Stopped -> "内置 Gateway：已停止"
            GatewayDeployManager.DeployState.Error -> "内置 Gateway：异常 ${st.error ?: ""}"
        }
    }

    private fun selectProvider(provider: String, fillPreset: Boolean) {
        val id = when (provider) {
            "deepseek" -> R.id.radio_deepseek
            "custom" -> R.id.radio_custom
            else -> R.id.radio_pollinations
        }
        findViewById<RadioButton>(id).isChecked = true
        if (fillPreset && presets[provider] != null) {
            endpoint.setText(presets[provider]!!.first)
            model.setText(presets[provider]!!.second)
        }
    }

    private fun updateKeyVisibility(provider: String) {
        val needsKey = provider != "pollinations"
        apiKey.visibility = if (needsKey) android.view.View.VISIBLE else android.view.View.GONE
        keyStatus.visibility = if (needsKey) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun selectedProvider(): String = when (providerGroup.checkedRadioButtonId) {
        R.id.radio_deepseek -> "deepseek"
        R.id.radio_custom -> "custom"
        else -> "pollinations"
    }

    private fun save() {
        val provider = selectedProvider()
        val settings = PetSettings(
            provider = provider,
            endpoint = endpoint.text.toString().trim(),
            model = model.text.toString().trim(),
            alwaysOnTop = chkAlwaysOnTop.isChecked,
            sound = chkSound.isChecked,
            freeWalk = chkFreeWalk.isChecked,
            interactiveZones = chkInteractiveZones.isChecked,
            spicyLines = chkSpicyLines.isChecked,
            scale = ((scaleBar.progress + 60) / 100f).coerceIn(0.6f, 1.15f),
            behaviorIntensity = ((intensityBar.progress + 50) / 100f).coerceIn(0.5f, 1.8f),
            touchThrough = chkTouchThrough.isChecked
        )
        Prefs.saveSettings(this, settings)
        val key = apiKey.text.toString().trim()
        if (key.isNotEmpty()) {
            ApiKeyStore.save(this, key)
        }
        PetService.instance?.let { service ->
            val view = service.getPetView()
            view.freeWalk = settings.freeWalk
            view.interactiveZones = settings.interactiveZones
            view.spicyLines = settings.spicyLines
            view.behaviorIntensity = settings.behaviorIntensity
            service.applyTouchThrough(settings.touchThrough)
        }
        keyStatus.text = "设置已保存"
    }
}
