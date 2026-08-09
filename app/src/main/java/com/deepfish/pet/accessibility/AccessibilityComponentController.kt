package com.deepfish.pet.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal class AccessibilityComponentController(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val component = ComponentName(appContext, OpenClawAccessibilityService::class.java)

  fun setEnabled(enabled: Boolean) {
    val state =
      if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      }
    appContext.packageManager.setComponentEnabledSetting(
      component,
      state,
      PackageManager.DONT_KILL_APP,
    )
  }
}
