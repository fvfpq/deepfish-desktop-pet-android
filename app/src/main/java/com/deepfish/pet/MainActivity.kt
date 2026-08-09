package com.deepfish.pet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.deepfish.pet.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshButtons()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startPet()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_start).setOnClickListener { startPet() }
        findViewById<Button>(R.id.btn_grant).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshButtons()
    }

    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_hint, Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val intent = Intent(this, PetService::class.java).setAction(PetService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        refreshButtons()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startPet()
        }
    }

    private fun refreshButtons() {
        val running = PetService.isRunning()
        val overlayOk = Settings.canDrawOverlays(this)
        val startBtn = findViewById<Button>(R.id.btn_start)
        val grantBtn = findViewById<Button>(R.id.btn_grant)
        val status = findViewById<TextView>(R.id.tv_status)

        if (running) {
            startBtn.text = getString(R.string.stop_pet)
            startBtn.setOnClickListener {
                stopService(Intent(this, PetService::class.java).setAction(PetService.ACTION_STOP))
                refreshButtons()
            }
            grantBtn.visibility = android.view.View.GONE
            status.text = "桌宠正在运行，去桌面看看吧"
        } else {
            startBtn.text = getString(R.string.start_pet)
            startBtn.setOnClickListener { startPet() }
            if (overlayOk) {
                grantBtn.visibility = android.view.View.GONE
                status.text = "悬浮窗权限已授予，点击启动桌宠"
            } else {
                grantBtn.visibility = android.view.View.VISIBLE
                status.text = getString(R.string.overlay_permission_hint)
            }
        }
    }
}
