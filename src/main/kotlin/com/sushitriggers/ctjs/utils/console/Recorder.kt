package com.sushitriggers.ctjs.utils.console

import com.sushitriggers.ctjs.minecraft.libs.ChatLib
import com.sushitriggers.ctjs.utils.Config
import com.sushitriggers.ctjs.utils.Refl
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object Recorder {
    private var wasDown = false

    fun keyName(code: Int): String = when (code) {
        in 290..301 -> "F${code - 289}"
        in 48..57, in 65..90 -> code.toChar().toString()
        else -> "key $code"
    }

    fun parseKey(text: String): Int? {
        val t = text.trim().uppercase()
        if (t.matches(Regex("F([1-9]|1[0-2])"))) return 289 + t.substring(1).toInt()
        if (t.length == 1 && (t[0] in 'A'..'Z' || t[0] in '0'..'9')) return t[0].code
        return null
    }

    fun toggle() {
        if (CTLog.recording) {
            val n = CTLog.recordedCount()
            val file = CTLog.stopRecording()
            ChatLib.chat(if (file != null) "&c[Record] stopped, $n lines saved: &f${file.path}" else "&c[Record] stop failed")
        } else {
            CTLog.startRecording()
            ChatLib.chat("&a[Record] started. Press ${keyName(Config.recordKey)} again to stop and save")
        }
    }

    fun tick() {
        try {
            val mc = Minecraft.getInstance()
            if (com.sushitriggers.ctjs.minecraft.libs.GameUtil.screen() != null) {
                wasDown = false
                return
            }
            val window = Refl.call(mc, "getWindow") ?: return
            val handle = (Refl.call(window, "handle", "getWindow") as? Long) ?: return
            val down = GLFW.glfwGetKey(handle, Config.recordKey) == GLFW.GLFW_PRESS
            if (down && !wasDown) toggle()
            wasDown = down
        } catch (e: Throwable) {
        }
    }
}
