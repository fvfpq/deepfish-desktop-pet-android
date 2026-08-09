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
import com.deepfish.pet.ApiKeyStore
import com.deepfish.pet.PetService
import com.deepfish.pet.PetSettings
import com.deepfish.pet.Prefs
import com.deepfish.pet.R

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

    private lateinit var scaleBar: SeekBar
    private lateinit var scaleOutput: TextView
    private lateinit var intensityBar: SeekBar
    private lateinit var intensityOutput: TextView

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
        scaleBar = findViewById(R.id.scale_bar)
        scaleOutput = findViewById(R.id.scale_output)
        intensityBar = findViewById(R.id.intensity_bar)
        intensityOutput = findViewById(R.id.intensity_output)

        findViewById<Button>(R.id.btn_reset).setOnClickListener { selectProvider("pollinations", true) }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

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
        scaleBar.progress = ((s.scale - 0.6f) * 20).toInt().coerceIn(0, 11)
        intensityBar.progress = ((s.behaviorIntensity - 0.5f) * 10).toInt().coerceIn(0, 13)
        scaleOutput.text = "${(s.scale * 100).toInt()}%"
        intensityOutput.text = "${(s.behaviorIntensity * 100).toInt()}%"
        keyStatus.text = if (ApiKeyStore.hasKey(this)) "已加密保存，留空即可保留" else "尚未保存 Key"
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
            behaviorIntensity = ((intensityBar.progress + 50) / 100f).coerceIn(0.5f, 1.8f)
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
        }
        keyStatus.text = "设置已保存"
    }
}
