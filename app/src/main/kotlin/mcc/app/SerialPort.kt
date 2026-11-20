package mcc.app

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.Closeable
import java.io.IOException

/** Raw serial port opened via JNI (termios raw, fixed baud). */
class SerialPort private constructor(private val pfd: ParcelFileDescriptor) : Closeable {
    /**
     * Returns bytes read, 0 on timeout (~200 ms, VTIME). Uses Os.read because FileInputStream treats a
     * 0-byte read as end-of-file.
     */
    fun read(buf: ByteArray): Int = Os.read(pfd.fileDescriptor, buf, 0, buf.size)

    fun write(frame: ByteArray) {
        var off = 0
        while (off < frame.size) off += Os.write(pfd.fileDescriptor, frame, off, frame.size - off)
    }

    override fun close() {
        try { pfd.close() } catch (_: IOException) {}
    }

    companion object {
        init { System.loadLibrary("mccserial") }

        @JvmStatic private external fun nativeOpen(path: String, baud: Int): Int

        fun open(path: String, baud: Int): SerialPort {
            val fd = nativeOpen(path, baud)
            if (fd < 0) throw IOException("open $path failed: ${Os.strerror(-fd)} ($fd)")
            return SerialPort(ParcelFileDescriptor.adoptFd(fd))
        }
    }
}

/**
 * Finds other processes that use (or will grab) the port. The stock machine service is non-dumpable, so its
 * /proc/<pid>/fd is root-only and invisible to us: it is detected by process name through ActivityManager.
 * Other same-uid processes are checked by their open file descriptors.
 */
object PortGuard {
    data class Holder(val pid: Int, val name: String)

    /** The stock machine service opens /dev/ttyMT0 as soon as it starts. */
    private val KNOWN_PORT_USERS = setOf("de.silpion.mc2:mc.machine_service")

    fun otherHolders(context: android.content.Context, devicePath: String): List<Holder> {
        val me = android.os.Process.myPid()
        val out = ArrayList<Holder>()
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.runningAppProcesses?.forEach { p ->
            if (p.pid != me && p.processName in KNOWN_PORT_USERS) out.add(Holder(p.pid, p.processName))
        }
        val myUid = Os.getuid()
        val procs = java.io.File("/proc").listFiles() ?: return out
        for (p in procs) {
            val pid = p.name.toIntOrNull() ?: continue
            if (pid == me || out.any { it.pid == pid }) continue
            // Other uids are unreadable anyway and every attempt logs an SELinux denial.
            if (runCatching { Os.stat(p.path).st_uid }.getOrNull() != myUid) continue
            val fds = java.io.File(p, "fd").listFiles() ?: continue
            if (fds.any { runCatching { Os.readlink(it.path) }.getOrNull() == devicePath }) {
                val name = runCatching { java.io.File(p, "cmdline").readText().trimEnd('\u0000').substringBefore('\u0000') }
                    .getOrDefault("?")
                out.add(Holder(pid, name))
            }
        }
        return out
    }
}
