package com.flightchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flightchat.model.AppSettings

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = :id")
    suspend fun getSettings(id: String = AppSettings.DEFAULT_ID): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: AppSettings)
}
