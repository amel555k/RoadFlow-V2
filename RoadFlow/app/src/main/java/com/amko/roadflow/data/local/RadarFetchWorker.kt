package com.amko.roadflow.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class RadarFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val favoriteCity = prefs.getString("favorite_city", "") ?: ""
        val notificationsEnabled = prefs.getBoolean("notification_enabled", true)

        if (favoriteCity.isBlank() || !notificationsEnabled) {
            return Result.success()
        }

        val firebaseService = FirebaseService()
        val parser = RadarParser(applicationContext, firebaseService)

        if (parser.isCachedForToday()) {
            val cached = parser.getActiveRadarsAsync()
            RadarNotificationHelper.update(
                context = applicationContext,
                currentRadars = cached,
                isNoInternetNoCache = false
            )
            return Result.success()
        }

        RadarNotificationHelper.showLoading(applicationContext, favoriteCity)

        return try {
            val radars = withTimeout(30_000L) {
                parser.parseAllLocationsAsFlow(null).first().radars
            }
            RadarNotificationHelper.update(
                context = applicationContext,
                currentRadars = radars,
                isNoInternetNoCache = false
            )
            Result.success()
        } catch (e: NoInternetWithCacheException) {
            val cached = if (parser.isCachedForToday()) e.cachedRadars else emptyList()
            val noInternetNoCache = cached.isEmpty()
            RadarNotificationHelper.update(
                context = applicationContext,
                currentRadars = cached,
                isNoInternetNoCache = noInternetNoCache
            )
            if (noInternetNoCache) Result.retry() else Result.success()
        } catch (e: TimeoutCancellationException) {
            val cached = if (parser.isCachedForToday()) parser.getActiveRadarsAsync() else emptyList()
            val noInternetNoCache = cached.isEmpty()
            RadarNotificationHelper.update(
                context = applicationContext,
                currentRadars = cached,
                isNoInternetNoCache = noInternetNoCache
            )
            Result.retry()
        } catch (e: Exception) {
            val cached = if (parser.isCachedForToday()) parser.getActiveRadarsAsync() else emptyList()
            val noInternetNoCache = cached.isEmpty()
            RadarNotificationHelper.update(
                context = applicationContext,
                currentRadars = cached,
                isNoInternetNoCache = noInternetNoCache
            )
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "radar_fetch_periodic"
        const val UNIQUE_ONE_TIME_WORK_NAME = "radar_fetch_one_time"
    }
}