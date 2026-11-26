package cn.aurange.schedule

import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object ExternalLinks {
    private const val FEISHU = "com.ss.android.lark"
    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    fun document(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host.isNullOrEmpty() || uri.userInfo != null) return false
        val host = uri.host.orEmpty().lowercase()
        if (host == "feishu.cn" || host.endsWith(".feishu.cn")) {
            if (start(context, Intent(Intent.ACTION_VIEW, uri).setPackage(FEISHU))) return true
            val appLink = Uri.parse("https://applink.feishu.cn/client/web_url/open").buildUpon().appendQueryParameter("url", url).build()
            if (start(context, Intent(Intent.ACTION_VIEW, appLink).setPackage(FEISHU))) return true
        }
        return start(context, Intent(Intent.ACTION_VIEW, uri))
    }
    fun appSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun notifications(context: Context): Boolean = start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) || appSettings(context)
    fun exactAlarms(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 31)
        start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) || appSettings(context)
        else appSettings(context)
    fun battery(context: Context): Boolean = start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || appSettings(context)
    fun batteryUnrestricted(context: Context): Boolean = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    fun autostart(context: Context): Boolean = start(context, Intent().setComponent(ComponentName("com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"))) || appSettings(context)
}
