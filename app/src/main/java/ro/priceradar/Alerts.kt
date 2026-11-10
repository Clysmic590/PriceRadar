package ro.priceradar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

object Alerts {
    fun show(context: Context, id: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("prices", "Price alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = Intent(context, MainActivity::class.java).putExtra("product_id", id)
            .setData(android.net.Uri.parse("priceradar://product/$id"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val intent = PendingIntent.getActivity(context, id.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(id.hashCode(), NotificationCompat.Builder(context, "prices").setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title).setContentText(text).setContentIntent(intent).setAutoCancel(true).build())
    }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PriceWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("price-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
class PriceWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (Repository.get(applicationContext).refresh(applicationContext)) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
}
