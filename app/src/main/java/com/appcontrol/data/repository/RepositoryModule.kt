package com.appcontrol.data.repository

import com.appcontrol.domain.repository.AppRepository
import com.appcontrol.domain.repository.AuthRepository
import com.appcontrol.domain.repository.RuleRepository
import com.appcontrol.domain.repository.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
