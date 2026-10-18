package com.example.ui.util

import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method

object ImeCujHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ijmInstance: Any? = null
    private var endMethod: Method? = null
    private var cancelMethod: Method? = null
    private var showCujCode: Int = 80
    private var hideCujCode: Int = 81
    private var initialized = false

    @Synchronized
    private fun init() {
        if (initialized) return
        initialized = true
        try {
            // Use double-reflection to bypass Android hidden API restrictions safely
            val forNameMethod = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val ijmClass = forNameMethod.invoke(null, "com.android.internal.jank.InteractionJankMonitor") as? Class<*> ?: return

            val classArrayClass = java.lang.Class.forName("[Ljava.lang.Class;")
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                classArrayClass
            )

            val noParams = java.lang.reflect.Array.newInstance(Class::class.java, 0)
            val getInstanceMethod = getDeclaredMethod.invoke(
                ijmClass,
                "getInstance",
                noParams
            ) as? Method
            getInstanceMethod?.isAccessible = true
            ijmInstance = getInstanceMethod?.invoke(null)

            val intParam = java.lang.reflect.Array.newInstance(Class::class.java, 1)
            java.lang.reflect.Array.set(intParam, 0, java.lang.Integer.TYPE)

            endMethod = getDeclaredMethod.invoke(ijmClass, "end", intParam) as? Method
            endMethod?.isAccessible = true

            cancelMethod = getDeclaredMethod.invoke(ijmClass, "cancel", intParam) as? Method
            cancelMethod?.isAccessible = true

            try {
                val showField = ijmClass.getDeclaredField("CUJ_IME_INSETS_SHOW_ANIMATION")
                showField.isAccessible = true
                showCujCode = showField.getInt(null)
            } catch (_: Throwable) {}

            try {
                val hideField = ijmClass.getDeclaredField("CUJ_IME_INSETS_HIDE_ANIMATION")
                hideField.isAccessible = true
                hideCujCode = hideField.getInt(null)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {
            // Safe fallback on platforms where class/methods are unavailable
        }
    }

    fun endImeCuj() {
        init()
        val instance = ijmInstance ?: return
        try {
            endMethod?.invoke(instance, showCujCode)
            endMethod?.invoke(instance, hideCujCode)
        } catch (_: Throwable) {
            try {
                cancelMethod?.invoke(instance, showCujCode)
                cancelMethod?.invoke(instance, hideCujCode)
            } catch (_: Throwable) {}
        }
    }

    fun scheduleFinish() {
        endImeCuj()
        mainHandler.postDelayed({ endImeCuj() }, 150L)
        mainHandler.postDelayed({ endImeCuj() }, 350L)
        mainHandler.postDelayed({ endImeCuj() }, 700L)
    }
}
