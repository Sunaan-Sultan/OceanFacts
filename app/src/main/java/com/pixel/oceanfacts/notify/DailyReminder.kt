package com.pixel.oceanfacts.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pixel.oceanfacts.MainActivity
import com.pixel.oceanfacts.R
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.DailyFact
import com.pixel.oceanfacts.core.DeepLink
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.Streak
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The once-a-day nudge back to the app, and the only thing in Ocean Facts that runs while it is
 * closed. Off until the user turns it on from the You tab.
 *
 * Scheduled as a self-rescheduling one-shot rather than a 24-hour periodic request: periodic
 * work drifts away from the wall clock and cannot follow a time-zone change or a change to the
 * chosen hour, and this needs to land at the time the user picked.
 *
 * One send a day, but not always the same one: when a live streak is about to lapse, the fact
 * gives way to the quiz, because that is the evening the user has something to lose. Two sends
 * would earn a little more and cost the channel — on Android 13+ a muted reminder cannot be
 * asked for again.
 */
object DailyReminder {

    // The channel id is load-bearing: renaming it would orphan the notification settings of
    // every user who already turned the reminder on. Both nudges are the same daily nudge.
    private const val CHANNEL_ID = "daily_fact"
    private const val WORK_NAME = "daily_fact_reminder"
    private const val NOTIFICATION_ID = 1

    /** Which of the two things tonight's single notification is about. */
    internal enum class Nudge { FACT, QUIZ }

    /** Below this there is nothing at stake worth naming, so a new user still gets the fact. */
    internal const val MIN_STREAK_FOR_QUIZ_NUDGE = 2

    /** Re-arms or clears the reminder to match [OceanPrefs]. Safe to call on every launch. */
    fun sync(context: Context) {
        if (OceanPrefs.notifyEnabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyFactWorker>()
            .setInitialDelay(millisUntilNextRun(OceanPrefs.notifyHour), TimeUnit.MILLISECONDS)
            .build()
        // REPLACE rather than KEEP so changing the hour takes effect on the next run instead of
        // being ignored until the pending one fires.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Milliseconds from now until the next [hour] o'clock in the device's own time zone. */
    internal fun millisUntilNextRun(hour: Int, now: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }

    /**
     * Which nudge tonight calls for. Pure, so the decision can be tested without a device.
     *
     * The quiz only wins when all three hold: today has nothing recorded yet, the run is long
     * enough to be worth naming, and it is genuinely live rather than a stale number left over
     * from a week ago. A nudge that claims something untrue is worse than no nudge at all.
     */
    internal fun nudgeFor(
        streak: Int,
        quizDoneToday: Boolean,
        lastActiveDay: Long,
        today: Long,
    ): Nudge = when {
        quizDoneToday -> Nudge.FACT
        streak < MIN_STREAK_FOR_QUIZ_NUDGE -> Nudge.FACT
        lastActiveDay == Streak.NEVER || lastActiveDay < today - 1 -> Nudge.FACT
        lastActiveDay == today -> Nudge.FACT
        else -> Nudge.QUIZ
    }

    private data class Content(
        val title: String,
        val text: String,
        val big: String,
        val uri: String,
    )

    internal fun notifyToday(context: Context) {
        // The worker runs in its own process state; nothing guarantees the catalog is loaded.
        OceanData.init(context)
        OceanPrefs.init(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val today = OceanPrefs.dayIndex
        val streak = OceanPrefs.streak
        val nudge = nudgeFor(
            streak = streak,
            quizDoneToday = OceanPrefs.isDailyQuizDone(today),
            lastActiveDay = OceanPrefs.lastActiveDay,
            today = today,
        )
        val content = when (nudge) {
            Nudge.QUIZ -> Content(
                title = "Your $streak-day streak ends tonight",
                text = "Today's ten questions are waiting.",
                big = "You have kept this going for $streak days. One round keeps it alive.",
                uri = DeepLink.toDailyQuiz(),
            )
            // Falls back to the fact, so a catalog that failed to load takes the whole send with
            // it rather than silently killing the streak nudge too.
            Nudge.FACT -> DailyFact.factForToday()?.let {
                Content(it.title, it.sub, it.blurb, DeepLink.toFact(it.id))
            } ?: return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = content.uri.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            // A request code per destination, so a pending intent left over from the other
            // variant cannot be handed back in place of this one.
            nudge.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.big))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // POST_NOTIFICATIONS can be revoked between the permission prompt and here.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        Analytics.notificationPosted(nudge.name.lowercase())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "One fact from the deep, or today's quiz, once a day." }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/** Posts today's fact, then books itself in again for tomorrow. */
class DailyFactWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        OceanPrefs.init(applicationContext)
        // The user may have switched the reminder off after this run was booked.
        if (!OceanPrefs.notifyEnabled) return Result.success()
        DailyReminder.notifyToday(applicationContext)
        DailyReminder.schedule(applicationContext)
        return Result.success()
    }
}
