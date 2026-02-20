package com.xs.expensetracker.utils

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager {

    private lateinit var remoteConfig: FirebaseRemoteConfig

    fun init() {
        remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour (prod safe)
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.setDefaultsAsync(
            mapOf(
                KEY_MAX_SOURCES to 20L,
                KEY_ENABLE_SHARING to true,
                KEY_PREMIUM_ENABLED to false,
                KEY_ADS_ENABLED to true
            )
        )
    }

    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }

    fun getBoolean(key: String): Boolean =
        remoteConfig.getBoolean(key)

    fun getString(key: String): String =
        remoteConfig.getString(key)

    fun getLong(key: String): Long =
        remoteConfig.getLong(key)

    fun getDouble(key: String): Double =
        remoteConfig.getDouble(key)

    // Keys
    companion object{
        const val KEY_MAX_SOURCES = "max_sources"
        const val KEY_ENABLE_SHARING = "enable_sharing"
        const val KEY_PREMIUM_ENABLED = "premium_enabled"
        const val KEY_ADS_ENABLED = "ads_enabled"
    }

}