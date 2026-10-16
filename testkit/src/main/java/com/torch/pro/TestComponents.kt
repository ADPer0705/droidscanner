package com.torch.pro

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.widget.TextView
import android.app.Service

/**
 * A deliberately harmless app whose only purpose is to hold a hostile-looking permission
 * profile so the auditor can be demonstrated on a real device. None of these components
 * do anything, and no permission is ever used at runtime.
 */
class TorchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            text = "Torch Pro\n\nBenign test app.\nIt declares risky permissions (SMS, contacts, camera, " +
                "microphone, location, overlay, accessibility, device admin) that are never used.\n\n" +
                "Use it to verify RiskScanner's scoring, special access panel and AI purpose check."
            textSize = 16f
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            gravity = Gravity.TOP
        }
        setContentView(text)
    }
}

class HelperService : Service() {
    override fun onBind(intent: Intent?) = null
}

class NotifListenerService : NotificationListenerService()

class AdminReceiver : DeviceAdminReceiver()

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = Unit
}
