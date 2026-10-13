package app.naviplayer.desktop

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.HHOOK
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT
import com.sun.jna.platform.win32.WinUser.MSG
import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles the hardware media keys on Windows.
 *
 * MPRIS provides this integration on Linux. Windows does not expose MPRIS, so
 * a low-level keyboard hook is used for the four media virtual keys. Events are
 * passed on to Windows after NaviPlayer handles them, allowing the normal
 * system media overlay and other applications to keep receiving the keys.
 */
class WindowsMediaKeyService(private val player: DesktopPlayer) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val started = CountDownLatch(1)
    private var hook: HHOOK? = null
    private var nativeThreadId = 0

    // Keep a strong reference for as long as the native hook is installed.
    private val callback = WinUser.LowLevelKeyboardProc { code, message, event ->
        val messageCode = message.toInt()
        if (code >= 0 && (messageCode == WinUser.WM_KEYDOWN || messageCode == WinUser.WM_SYSKEYDOWN)) {
            actionFor(event.vkCode)?.let { action ->
                EventQueue.invokeLater { if (!closed.get()) action() }
            }
        }
        User32.INSTANCE.CallNextHookEx(hook, code, message, pointerOf(event))
    }

    private val messageThread: Thread? = if (isWindows()) {
        Thread(::runMessageLoop, "naviplayer-windows-media-keys").apply {
            isDaemon = true
            start()
        }
    } else {
        started.countDown()
        null
    }

    init {
        if (messageThread != null) {
            runCatching { started.await(2, TimeUnit.SECONDS) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val threadId = nativeThreadId
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WPARAM(0), LPARAM(0))
        }
        runCatching { messageThread?.join(2_000) }
    }

    private fun runMessageLoop() {
        nativeThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
        try {
            hook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, callback, null, 0)
            if (hook == null) {
                System.err.println("NaviPlayer Windows media keys unavailable: SetWindowsHookEx failed")
                return
            }
            started.countDown()
            val message = MSG()
            while (!closed.get() && User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(message)
                User32.INSTANCE.DispatchMessage(message)
            }
        } catch (error: Throwable) {
            System.err.println("NaviPlayer Windows media keys unavailable: ${error.message}")
        } finally {
            started.countDown()
            hook?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
            hook = null
            nativeThreadId = 0
        }
    }

    private fun actionFor(keyCode: Int): (() -> Unit)? = when (keyCode) {
        VK_MEDIA_PLAY_PAUSE -> player::toggle
        VK_MEDIA_NEXT_TRACK -> player::next
        VK_MEDIA_PREV_TRACK -> player::previous
        VK_MEDIA_STOP -> player::stop
        else -> null
    }

    private fun pointerOf(event: KBDLLHOOKSTRUCT): LPARAM =
        LPARAM(Pointer.nativeValue(event.pointer))

    private companion object {
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_MEDIA_STOP = 0xB2
        const val VK_MEDIA_PLAY_PAUSE = 0xB3

        fun isWindows(): Boolean =
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    }
}
