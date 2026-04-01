package com.appcontrol.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppControlDatabase {
        return Room.databaseBuilder(
            context,
            AppControlDatabase::class.java,
            "app_control_db"
        ).build()
    }

    @Provides
    fun provideTargetAppDao(database: AppControlDatabase): TargetAppDao {
        return database.targetAppDao()
    }

    @Provides
    fun provideAllowedPeriodDao(database: AppControlDatabase): AllowedPeriodDao {
        return database.allowedPeriodDao()
    }

    @Provides
    fun provideUsageRecordDao(database: AppControlDatabase): UsageRecordDao {
        return database.usageRecordDao()
    }

    @Provides
    fun provideAppSettingsDao(database: AppControlDatabase): AppSettingsDao {
        return database.appSettingsDao()
    }
}
