package cn.aurange.schedule

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    private val model: ScheduleViewModel by viewModels()
    private var route by mutableStateOf<String?>(null)
    private var permissionVersion by mutableIntStateOf(0)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionVersion++
        model.message(if (granted) "通知已开启，可发送测试通知。" else "通知尚未开启，可在设置中再次开启。")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(0xFFF5F6F8.toInt(), 0xFF344054.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFF5F6F8.toInt(), 0xFF344054.toInt()))
        if (intent.hasExtra("taskId")) route = intent.getStringExtra("taskId").orEmpty()
        ReminderScheduler(this).channels()
        setContent { ScheduleTheme { ScheduleApp(model, route, { route = null }, permissionVersion, ::askNotifications) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { delay(30_000); model.refresh() }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("taskId")) route = intent.getStringExtra("taskId").orEmpty()
    }
    override fun onResume() {
        super.onResume()
        permissionVersion++
        model.refresh()
        lifecycleScope.launch(Dispatchers.IO) { ReminderScheduler(this@MainActivity).rebuild() }
    }
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val permissionPrefs = getPreferences(MODE_PRIVATE)
            if (permissionPrefs.getBoolean("notificationsAsked", false) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                ExternalLinks.notifications(this)
                return
            }
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                model.message("请允许通知，用于每天的待办和任务时间提醒。")
            }
            permissionPrefs.edit().putBoolean("notificationsAsked", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else ExternalLinks.notifications(this)
    }
}
