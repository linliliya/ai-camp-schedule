package cn.aurange.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = ScheduleRepository.get(applicationContext)
        if (repo.store.session() == null || repo.store.demo()) return Result.success()
        return try {
            repo.refresh()
            ReminderScheduler(applicationContext).rebuild()
            Result.success()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            ReminderScheduler(applicationContext).rebuild()
            if (error is AccessDenied || error is LoginExpired) Result.success() else Result.retry()
        }
    }
    companion object {
        private const val NAME = "schedule-cloud-sync"
        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun disable(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
    }
}
