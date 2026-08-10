package com.deepfish.pet.gateway

import android.content.Context
import android.util.Log
import com.deepfish.pet.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 内置 openclaw Gateway 部署管理器。
 * 负责：首次解压 rootfs asset → 启动 proot 进程运行 openclaw gateway → 状态管理。
 */
object GatewayDeployManager {

    private const val TAG = "GatewayDeploy"
    private const val ASSET_ROOTFS = "rootfs.dat"
    private const val ROOTFS_DIR_NAME = "rootfs"
    private const val HOME_DIR_NAME = "home"
    private const val CONFIG_DIR_NAME = "config"
    private const val TMP_DIR_NAME = "tmp"

    enum class DeployState {
        NotDeployed,   // 未部署（无 rootfs）
        Extracting,    // 解压中
        Ready,         // rootfs 就绪，gateway 可启动
        Starting,      // gateway 启动中
        Running,       // gateway 运行中
        Stopping,      // 停止中
        Stopped,       // 已停止
        Error          // 出错
    }

    data class DeployStatus(
        val state: DeployState = DeployState.NotDeployed,
        val progress: Float = 0f,
        val message: String = "",
        val pid: Long = 0L,
        val error: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _status = MutableStateFlow(DeployStatus())
    val status: StateFlow<DeployStatus> = _status.asStateFlow()

    private var gatewayProcess: java.lang.Process? = null
    private var rootfsDir: File? = null

    val isRunning: Boolean
        get() = gatewayProcess?.let { proc ->
            try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        } ?: false

    // ================================================================
    // 路径
    // ================================================================

    private fun rootfsDir(context: Context): File =
        File(context.filesDir, ROOTFS_DIR_NAME)

    private fun configDir(context: Context): File =
        File(context.filesDir, CONFIG_DIR_NAME)

    private fun tmpDir(context: Context): File =
        File(context.filesDir, TMP_DIR_NAME)

    private fun homeDir(context: Context): File =
        File(context.filesDir, HOME_DIR_NAME)

    private fun nativeLibDir(context: Context): String =
        context.applicationInfo.nativeLibraryDir

    private fun rootfsReady(context: Context): Boolean {
        val rf = rootfsDir(context)
        val node = File(rf, "usr/local/bin/node")
        val openclaw = File(rf, "usr/local/lib/node_modules/openclaw/openclaw.mjs")
        val proot = File(nativeLibDir(context), "libproot.so")
        return node.exists() && openclaw.exists() && proot.exists()
    }

    // ================================================================
    // 部署状态
    // ================================================================

    /** 判断当前是否已部署且可运行。 */
    fun isDeployed(context: Context): Boolean = rootfsReady(context)

    fun isProotReady(context: Context): Boolean =
        File(nativeLibDir(context), "libproot.so").exists()

    // ================================================================
    // 解压 rootfs
    // ================================================================

    /**
     * 将 asset 中的 rootfs.tar.gz 解压到 filesDir/rootfs。
     * 两阶段：先解压文件/目录，再建软链，处理 tar 条目顺序问题。
     */
    suspend fun deploy(context: Context) {
        if (isDeployed(context)) {
            _status.value = DeployStatus(DeployState.Ready, 1f, "已部署")
            return
        }
        withContext(Dispatchers.IO) {
            _status.value = DeployStatus(DeployState.Extracting, 0f, "正在解压运行时…")
            val rf = rootfsDir(context)
            if (rf.exists()) deleteRecursively(rf)
            rf.mkdirs()
            context.filesDir.mkdirs()

            val deferredSymlinks = mutableListOf<Pair<String, File>>()
            var entryCount = 0
            var fileCount = 0
            var extractionError: Exception? = null

            try {
                context.assets.open(ASSET_ROOTFS).use { ais ->
                    BufferedInputStream(ais, 256 * 1024).use { bis ->
                        GzipCompressorInputStream(bis).use { gis ->
                            TarArchiveInputStream(gis).use { tis ->
                                var entry: TarArchiveEntry? = tis.nextTarEntry
                                while (entry != null) {
                                    entryCount++
                                    val name = entry.name
                                        .removePrefix("./")
                                        .removePrefix("/")
                                    if (name.isEmpty() || name.startsWith("dev/") || name == "dev") {
                                        entry = tis.nextTarEntry
                                        continue
                                    }
                                    val outFile = File(rf, name)
                                    when {
                                        entry.isDirectory -> outFile.mkdirs()
                                        entry.isSymbolicLink ->
                                            deferredSymlinks.add(Pair(entry.linkName, outFile))
                                        entry.isLink -> {
                                            val target = entry.linkName.removePrefix("./").removePrefix("/")
                                            val targetFile = File(rf, target)
                                            outFile.parentFile?.mkdirs()
                                            try {
                                                if (targetFile.exists()) {
                                                    targetFile.copyTo(outFile, overwrite = true)
                                                    if (targetFile.canExecute()) outFile.setExecutable(true, false)
                                                    fileCount++
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        else -> {
                                            outFile.parentFile?.mkdirs()
                                            FileOutputStream(outFile).use { fos ->
                                                val buf = ByteArray(65536)
                                                var len: Int
                                                while (tis.read(buf).also { len = it } != -1) {
                                                    fos.write(buf, 0, len)
                                                }
                                            }
                                            outFile.setReadable(true, false)
                                            outFile.setWritable(true, false)
                                            val mode = entry.mode
                                            if (mode == 0 || mode and 0b001_001_001 != 0) {
                                                outFile.setExecutable(true, false)
                                            }
                                            fileCount++
                                        }
                                    }
                                    if (entryCount % 5000 == 0) {
                                        val p = (entryCount.toFloat() / 30000f).coerceAtMost(0.9f)
                                        _status.value = DeployStatus(DeployState.Extracting, p, "正在解压运行时… ($fileCount 个文件)")
                                    }
                                    entry = tis.nextTarEntry
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                extractionError = e
            }

            if (entryCount == 0) {
                throw RuntimeException("rootfs asset 为空或损坏: ${extractionError?.message}")
            }

            for ((target, file) in deferredSymlinks) {
                try {
                    if (file.exists()) file.delete()
                    file.parentFile?.mkdirs()
                    // Android 的 File API 无法建符号链接，用 ln -s 命令
                    val cmd = arrayOf("ln", "-sf", target, file.absolutePath)
                    Runtime.getRuntime().exec(cmd).waitFor()
                } catch (_: Exception) {
                    // 无符号链接权限时跳过
                }
            }

            // 补齐关键目录（proot 下 mkdir 可能 ENOSYS）
            for (dir in listOf(
                "etc/ssl/certs", "root/.openclaw/data", "root/.openclaw/memory",
                "root/.openclaw/config", "root/.openclaw/logs", "root/.config/openclaw",
                "root/.local/share", "root/.cache", "root/.cache/openclaw",
                "tmp/npm-cache", "var/tmp", "run/lock", "dev/shm"
            )) File(rf, dir).mkdirs()

            // 确保可执行位
            fixExec(rf)

            if (!File(rf, "usr/local/bin/node").exists()) {
                throw RuntimeException("rootfs 解压后缺少 node 二进制")
            }
            if (extractionError != null && fileCount < 100) {
                throw RuntimeException("rootfs 解压失败: ${extractionError.message}")
            }

            _status.value = DeployStatus(DeployState.Ready, 1f, "部署完成")
        }
    }

    private fun fixExec(root: File) {
        for (dirName in listOf("usr/bin", "usr/sbin", "usr/local/bin", "usr/local/sbin", "bin", "sbin")) {
            val dir = File(root, dirName)
            if (dir.exists()) fixExecRecursive(dir)
        }
        for (dirName in listOf("usr/lib", "lib")) {
            val dir = File(root, dirName)
            if (dir.exists()) fixSharedLibsRecursive(dir)
        }
    }

    private fun fixExecRecursive(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) fixExecRecursive(f)
            else if (f.isFile) {
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }
    }

    private fun fixSharedLibsRecursive(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) fixSharedLibsRecursive(f)
            else if (f.isFile && (f.name.endsWith(".so") || f.name.contains(".so."))) {
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }
    }

    // ================================================================
    // proot 启动
    // ================================================================

    /** 构造 proot 命令。参照 proot-distro command_login 风格。 */
    private fun buildGatewayCommand(context: Context, command: String): List<String> {
        val nativeLib = nativeLibDir(context)
        val rf = rootfsDir(context)
        val cfg = configDir(context)
        val tmp = tmpDir(context)
        val home = homeDir(context)
        cfg.mkdirs()
        tmp.mkdirs()
        home.mkdirs()

        // resolv.conf
        val resolvHost = File(cfg, "resolv.conf")
        if (!resolvHost.exists() || resolvHost.length() == 0L) {
            resolvHost.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        }

        // fake /proc 文件
        val procFakes = File(cfg, "proc_fakes")
        procFakes.mkdirs()
        File(procFakes, "loadavg").writeText("0.00 0.01 0.05 1/100 1000\n")
        File(procFakes, "uptime").writeText("1000.0 1000.0\n")
        File(procFakes, "version").writeText("Linux version 6.17.0-PRoot-Distro (proot) #1 SMP\n")
        File(procFakes, "vmstat").writeText("nr_free_pages 100000\n")
        File(procFakes, "cap_last_cap").writeText("37\n")
        File(procFakes, "max_user_watches").writeText("8192\n")
        val sysFakes = File(cfg, "sys_fakes")
        sysFakes.mkdirs()
        File(sysFakes, "empty").mkdirs()

        val flags = mutableListOf(
            "$nativeLib/libproot.so",
            "--link2symlink",
            "-L",
            "--kill-on-exit",
            "--change-id=0:0",
            "--sysvipc",
            "--rootfs=$rf",
            "--cwd=/root",
            "--bind=/dev",
            "--bind=/dev/urandom:/dev/random",
            "--bind=/proc",
            "--bind=/proc/self/fd:/dev/fd",
            "--bind=/proc/self/fd/0:/dev/stdin",
            "--bind=/proc/self/fd/1:/dev/stdout",
            "--bind=/proc/self/fd/2:/dev/stderr",
            "--bind=/sys",
            "--bind=$procFakes/loadavg:/proc/loadavg",
            "--bind=$procFakes/uptime:/proc/uptime",
            "--bind=$procFakes/version:/proc/version",
            "--bind=$procFakes/vmstat:/proc/vmstat",
            "--bind=$procFakes/cap_last_cap:/proc/sys/kernel/cap_last_cap",
            "--bind=$procFakes/max_user_watches:/proc/sys/fs/inotify/max_user_watches",
            "--bind=$rf/tmp:/dev/shm",
            "--bind=$sysFakes/empty:/sys/fs/selinux",
            "--bind=$resolvHost:/etc/resolv.conf",
            "--bind=$home:/root/home",
        )

        // 内核版本伪装（完整 uname 结构）
        val machine = "aarch64"
        val kernelRelease = "\\Linux\\localhost\\6.17.0-PRoot-Distro" +
            "\\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\\$machine\\localdomain\\-1\\"
        flags.add(1, "--kernel-release=$kernelRelease")

        flags.addAll(listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "USER=root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
            "CHOKIDAR_USEPOLLING=true",
            "UV_USE_IO_URING=0",
            "/bin/bash", "-c",
            command,
        ))
        return flags
    }

    /** proot 宿主进程的环境变量。 */
    private fun prootEnv(context: Context): Map<String, String> {
        val nativeLib = nativeLibDir(context)
        val cfg = configDir(context)
        val tmp = tmpDir(context)
        return mapOf(
            "PROOT_TMP_DIR" to tmp.absolutePath,
            "PROOT_LOADER" to "$nativeLib/libprootloader.so",
            "PROOT_LOADER_32" to "$nativeLib/libprootloader32.so",
            "LD_LIBRARY_PATH" to "$cfg/libtalloc:$nativeLib",
        )
    }

    /** 确保 libtalloc.so → libtalloc.so.2（匹配 proot 的 SONAME）。 */
    private fun ensureTalloc(context: Context) {
        val nativeLib = nativeLibDir(context)
        val destDir = File(configDir(context), "libtalloc")
        destDir.mkdirs()
        val so2 = File(destDir, "libtalloc.so.2")
        if (!so2.exists()) {
            val src = File(nativeLib, "libtalloc.so")
            if (src.exists()) {
                src.copyTo(so2, overwrite = true)
                so2.setReadable(true, false)
                so2.setExecutable(true, false)
            }
        }
    }

    /**
     * 启动内置 Gateway 进程。rootfs 就绪且进程未运行时调用。
     */
    fun start(context: Context): Boolean {
        if (isRunning) return true
        if (!isDeployed(context)) {
            _status.value = DeployStatus(DeployState.Error, message = "尚未部署", error = "rootfs 未就绪")
            return false
        }
        ensureTalloc(context)
        val command = "node /usr/local/lib/node_modules/openclaw/openclaw.mjs " +
            "gateway --allow-unconfigured --port ${Prefs.gatewayPort(context)} --bind loopback"
        val cmd = buildGatewayCommand(context, command)
        val env = prootEnv(context)
        _status.value = DeployStatus(DeployState.Starting, message = "正在启动内置 Gateway…")

        return try {
            val pb = ProcessBuilder(cmd)
            pb.environment().clear()
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            val process = pb.start()
            gatewayProcess = process
            _status.value = DeployStatus(DeployState.Running, message = "内置 Gateway 运行中")
            // 读取进程日志（防止缓冲阻塞）
            scope.launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { Log.d(TAG, "proot: $it") }
                }
            }
            true
        } catch (e: Exception) {
            _status.value = DeployStatus(DeployState.Error, error = e.message, message = "启动失败")
            Log.e(TAG, "gateway start failed", e)
            false
        }
    }

    /** 停止内置 Gateway 进程。 */
    fun stop(context: Context) {
        val proc = gatewayProcess ?: run {
            _status.value = DeployStatus(DeployState.Stopped, message = "已停止")
            return
        }
        _status.value = DeployStatus(DeployState.Stopping, message = "正在停止…")
        scope.launch(Dispatchers.IO) {
            try {
                proc.destroy()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {}
            gatewayProcess = null
            _status.value = DeployStatus(DeployState.Stopped, message = "已停止")
        }
    }

    /** 等待 rootfs 就绪（部署/解压完成后返回）。 */
    suspend fun awaitDeployed(context: Context) {
        if (isDeployed(context)) return
        deploy(context)
    }

    fun getRootfsPath(context: Context): String? =
        if (isDeployed(context)) rootfsDir(context).absolutePath else null

    private fun deleteRecursively(file: File) {
        if (!file.absolutePath.contains("/files/rootfs")) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
