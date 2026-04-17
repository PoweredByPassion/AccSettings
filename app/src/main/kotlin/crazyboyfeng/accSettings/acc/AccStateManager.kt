package crazyboyfeng.accSettings.acc

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ACC 状态管理器
 * 维护全局状态缓存，后台定期轮询更新状态
 */
object AccStateManager {
    private const val TAG = "AccStateManager"
    private const val POLLING_INTERVAL_MS = 5000L // 5秒轮询间隔

    private val _accStatus = MutableStateFlow<AccStatus?>(null)
    val accStatus: StateFlow<AccStatus?> = _accStatus.asStateFlow()

    private var monitoringJob: Job? = null
    private var isMonitoring = false
    private var appContext: Context? = null

    /**
     * 启动状态监控
     * @param context Application context
     */
    fun startMonitoring(context: Context) {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already started")
            return
        }

        appContext = context.applicationContext
        isMonitoring = true

        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            Log.i(TAG, "Starting ACC status monitoring")

            // 立即获取一次状态
            refreshNow()

            // 开始定期轮询
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                refreshNow()
            }
        }

        Log.i(TAG, "ACC status monitoring started")
    }

    /**
     * 停止状态监控
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        monitoringJob?.cancel()
        monitoringJob = null
        isMonitoring = false

        Log.i(TAG, "ACC status monitoring stopped")
    }

    /**
     * 强制立即刷新状态
     */
    suspend fun refreshNow() {
        try {
            val status = fetchAccStatus()
            _accStatus.value = status
            Log.d(TAG, "ACC status updated: installState=${status.installState}, daemonRunning=${status.daemonRunning}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ACC status", e)
            // 保持上一次的状态，或者设置为 null 表示未知状态
            // _accStatus.value = null
        }
    }

    /**
     * 获取当前缓存的状态（同步方法，用于快速读取）
     */
    fun getCurrentStatus(): AccStatus? = _accStatus.value

    /**
     * 检查 daemon 是否正在运行（从缓存读取，避免阻塞）
     */
    fun isDaemonRunning(): Boolean = _accStatus.value?.daemonRunning ?: false

    /**
     * 从系统获取最新的 ACC 状态
     */
    private suspend fun fetchAccStatus(): AccStatus = withContext(Dispatchers.IO) {
        // 检查 Root 权限
        val hasRoot = try {
            Shell.rootAccess()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check root access", e)
            false
        }

        if (!hasRoot) {
            // 返回一个默认状态表示无 root 权限
            return@withContext AccStatus(
                installState = AccInstallState.NOT_INSTALLED,
                installedVersionName = null,
                daemonRunning = false,
                canManageDaemon = false,
                showInstallAction = true,
                showUninstallAction = false
            )
        }

        // 获取版本信息
        val (installedVersionCode, installedVersionName) = try {
            Command.getVersion()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get ACC version", e)
            Pair(0, null)
        }

        // 获取 daemon 运行状态
        val daemonRunning = try {
            Command.isDaemonRunning()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check daemon status", e)
            false
        }

        // 获取 bundled 版本代码
        val bundledVersionCode = appContext?.resources?.getInteger(
            appContext!!.resources.getIdentifier("acc_version_code", "integer", appContext!!.packageName)
        ) ?: 0

        // 解析状态
        AccStatusResolver.resolve(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            bundledVersionCode = bundledVersionCode,
            daemonRunning = daemonRunning
        )
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stopMonitoring()
        _accStatus.value = null
        appContext = null
    }
}
