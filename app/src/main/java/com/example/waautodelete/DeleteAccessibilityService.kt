package com.example.waautodelete

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap

class DeleteAccessibilityService : AccessibilityService() {

    data class Pending(val chatTitle: String, val timestamp: Long, var canceled: Boolean = false)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val handler = Handler(Looper.getMainLooper())
    private val waitMs = 2 * 60 * 60 * 1000L
    private val checkIntervalMs = 5 * 60 * 1000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.postDelayed(checker, checkIntervalMs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val root = rootInActiveWindow ?: return
        val chatTitle = getChatTitle(root) ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val src = event.source ?: return
                if (src.viewIdResourceName?.endsWith("send") == true) {
                    pending[chatTitle] = Pending(chatTitle, System.currentTimeMillis())
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                pending[chatTitle]?.let { it.canceled = true }
            }
        }
    }

    private fun getChatTitle(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/conversation_contact_name"
        )
        return nodes.firstOrNull()?.text?.toString()
    }

    private val checker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val toRemove = mutableListOf<String>()
            for ((key, p) in pending) {
                if (p.canceled) {
                    toRemove.add(key)
                } else if (now - p.timestamp >= waitMs) {
                    attemptDeleteLastMessage(p.chatTitle)
                    toRemove.add(key)
                }
            }
            toRemove.forEach { pending.remove(it) }
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    private fun attemptDeleteLastMessage(chatTitle: String) {
        // TODO: secuencia real de gestos (long-press, Eliminar, Eliminar para
        // todos, confirmar). Requiere ajustar ids reales de tu WhatsApp.
    }

    override fun onInterrupt() {}
}
