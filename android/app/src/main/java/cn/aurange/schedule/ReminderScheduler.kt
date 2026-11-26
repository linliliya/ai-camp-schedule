package cn.aurange.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.Instant

class ReminderScheduler(private val context: Context) {
    private val repo get() = ScheduleRepository.get(context)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    fun channels() {
        notifications.createNotificationChannels(listOf(
            NotificationChannel(TASK_CHANNEL, "任务时间提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "有明确时间的任务及手动设置的任务提醒"; enableVibration(true)
            },
            NotificationChannel(SUMMARY_CHANNEL, "每日待办汇总", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天汇总待办和持续跟进事项"
            }
        ))
    }
    fun canNotify(channel: String = TASK_CHANNEL): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && notifications.areNotificationsEnabled() && notifications.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun pending(at: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, 1,
        Intent(context, ReminderReceiver::class.java).putExtra("scheduledAt", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun rebuild() = synchronized(lock) { rebuildLocked() }
    private fun rebuildLocked() {
        channels()
        alarms.cancel(pending())
        if (repo.store.session() == null || repo.store.demo() || !repo.store.settings().enabled) {
            repo.store.batch(null)
            notifications.cancelAll()
            return
        }
        val next = ReminderPlan(repo.engine).next(repo.store.snapshot(), repo.store.settings(), Instant.now(),
            repo.store.custom(), repo.store.delivered().keys, repo.store.batch())
        repo.store.batch(next)
        if (next != null) {
            val operation = pending(next.at)
            try {
                if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, operation)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, operation)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, operation)
            }
        }
    }
    fun cancel() = synchronized(lock) {
        alarms.cancel(pending()); repo.store.batch(null); notifications.cancelAll()
    }

    fun deliverAndRebuild(scheduledAt: Long) = synchronized(lock) {
        try {
            val batch = repo.store.batch() ?: return@synchronized
            val now = System.currentTimeMillis()
            if (batch.at != scheduledAt || now < batch.at - 1000 || now - batch.at > 3_600_000) return@synchronized
            if (repo.store.session() == null || !repo.store.settings().enabled || repo.store.demo()) return@synchronized
            val snapshot = repo.store.snapshot()
            val all = repo.engine.allTasks(snapshot).associateBy { it.id }
            val already = repo.store.delivered().keys
            for (event in batch.events.filterNot { it.id in already }) {
                if (event.summary) {
                    val date = Instant.ofEpochMilli(event.at).atZone(BUSINESS_ZONE).toLocalDate()
                    val tasks = ReminderPlan(repo.engine).summaryTasks(snapshot, date)
                    if (tasks.isNotEmpty()) post(200, "今日有 ${tasks.size} 项待办", tasks.take(8).joinToString("\n") {
                        "第${it.camp.term}期 · ${it.template.title}"
                    } + if (tasks.size > 8) "\n打开应用查看全部" else "", "", SUMMARY_CHANNEL)
                } else {
                    val task = all[event.taskId] ?: continue
                    if (!repo.engine.complete(snapshot, task)) {
                        val time = task.template.time.takeIf { it.isNotEmpty() }?.let { "$it · " }.orEmpty()
                        post(event.taskId.hashCode() and 0x7fffffff, "第${task.camp.term}期 · ${task.template.title}",
                            time + task.template.description.ifEmpty { "点击查看任务与相关资料" }, task.id, TASK_CHANNEL)
                    }
                }
            }
            repo.store.markDelivered(batch.events, now)
        } finally { rebuildLocked() }
    }
    private fun post(id: Int, title: String, body: String, taskId: String, channel: String): Boolean {
        channels()
        if (!canNotify(channel)) return false
        val route = Intent(context, MainActivity::class.java).putExtra("taskId", taskId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val action = PendingIntent.getActivity(context, id, route, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setColor(0x2563eb).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(action).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try { notifications.notify(id, notification); true } catch (_: SecurityException) { false }
    }
    fun test(): Boolean = post(199, "营期日程 · 通知测试", "通知已开启。日程提醒会显示在这里，点击可以返回今日待办。", "", TASK_CHANNEL)
    companion object {
        private val lock = Any()
        const val TASK_CHANNEL = "schedule-tasks-v1"
        const val SUMMARY_CHANNEL = "schedule-summary-v1"
    }
}
