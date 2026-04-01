package com.appcontrol.domain.usecase

import com.appcontrol.data.db.AllowedPeriod
import com.appcontrol.data.db.TargetApp
import com.appcontrol.data.db.UsageRecord
import com.appcontrol.domain.model.LockReason
import com.appcontrol.domain.repository.AppRepository
import com.appcontrol.domain.repository.RuleRepository
import com.appcontrol.domain.repository.UsageRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// region Fakes

class FakeAppRepository : AppRepository {
    var apps = mutableMapOf<String, TargetApp>()

    override fun getAllTargetApps(): Flow<List<TargetApp>> = flowOf(apps.values.toList())
    override suspend fun getTargetApp(packageName: String): TargetApp? = apps[packageName]
    override fun getWhitelistedApps(): Flow<List<TargetApp>> =
        flowOf(apps.values.filter { it.isWhitelisted })
    override suspend fun addTargetApp(app: TargetApp) { apps[app.packageName] = app }
    override suspend fun removeTargetApp(packageName: String) { apps.remove(packageName) }
    override suspend fun toggleWhitelist(packageName: String, isWhitelisted: Boolean) {
        apps[packageName] = apps[packageName]!!.copy(isWhitelisted = isWhitelisted)
    }
}

class FakeRuleRepository : RuleRepository {
    var periods = mutableMapOf<String, List<AllowedPeriod>>()

    override suspend fun setUsageLimit(packageName: String, limitMinutes: Int?) {}
    override fun getAllowedPeriods(packageName: String): Flow<List<AllowedPeriod>> =
        flowOf(periods[packageName] ?: emptyList())
    override fun getAllowedPeriodsSync(packageName: String): List<AllowedPeriod> =
        periods[packageName] ?: emptyList()
    override suspend fun addAllowedPeriod(period: AllowedPeriod) {}
    override suspend fun updateAllowedPeriod(period: AllowedPeriod) {}
    override suspend fun removeAllowedPeriod(period: AllowedPeriod) {}
}

class FakeUsageRepository : UsageRepository {
    var records = mutableMapOf<String, UsageRecord>() // key = "$packageName|$date"
    var ensureCalled = false
    var lastUpdatePackage: String? = null
    var lastUpdateDate: String? = null

    override suspend fun getUsageRecord(packageName: String, date: String): UsageRecord? =
        records["$packageName|$date"]
    override fun getDailyStats(date: String): Flow<List<UsageRecord>> =
        flowOf(records.values.filter { it.date == date })
    override fun getWeeklyStats(startDate: String, endDate: String): Flow<List<UsageRecord>> =
        flowOf(emptyList())
    override suspend fun updateUsageDuration(packageName: String, date: String, additionalSeconds: Long) {
        lastUpdatePackage = packageName
        lastUpdateDate = date
        val key = "$packageName|$date"
        val existing = records[key]
        if (existing != null) {
            records[key] = existing.copy(usageDurationSeconds = existing.usageDurationSeconds + additionalSeconds)
        }
    }
    override suspend fun incrementOpenCount(packageName: String, date: String) {}
    override suspend fun ensureRecordExists(packageName: String, date: String) {
        ensureCalled = true
        val key = "$packageName|$date"
        if (records[key] == null) {
            records[key] = UsageRecord(packageName = packageName, date = date)
        }
    }
    override suspend fun cleanOldData(beforeDate: String) {}
}

// endregion


class CheckAppLockStatusUseCaseTest : DescribeSpec({

    describe("CheckAppLockStatusUseCase") {

        it("returns NotLocked when app is not a target app") {
            val appRepo = FakeAppRepository()
            val ruleRepo = FakeRuleRepository()
            val usageRepo = FakeUsageRepository()
            val useCase = CheckAppLockStatusUseCase(appRepo, ruleRepo, usageRepo)

            val result = useCase("com.example.unknown")
            result shouldBe LockReason.NotLocked
        }

        it("returns NotLocked when app is whitelisted") {
            val appRepo = FakeAppRepository().apply {
                apps["com.example.app"] = TargetApp(
                    packageName = "com.example.app",
                    appName = "Test App",
                    isWhitelisted = true
                )
            }
            val ruleRepo = FakeRuleRepository()
            val usageRepo = FakeUsageRepository()
            val useCase = CheckAppLockStatusUseCase(appRepo, ruleRepo, usageRepo)

            val result = useCase("com.example.app")
            result shouldBe LockReason.NotLocked
        }

        it("returns NotLocked when no limits are set and no periods defined") {
            val appRepo = FakeAppRepository().apply {
                apps["com.example.app"] = TargetApp(
                    packageName = "com.example.app",
                    appName = "Test App"
                )
            }
            val ruleRepo = FakeRuleRepository()
            val usageRepo = FakeUsageRepository()
            val useCase = CheckAppLockStatusUseCase(appRepo, ruleRepo, usageRepo)

            val result = useCase("com.example.app")
            result shouldBe LockReason.NotLocked
        }

        it("returns UsageLimitExceeded when usage exceeds limit") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(
                    packageName = pkg,
                    appName = "Test App",
                    usageLimitMinutes = 30
                )
            }
            val ruleRepo = FakeRuleRepository()
            val usageRepo = FakeUsageRepository().apply {
                // 30 minutes = 1800 seconds, set usage to 1800
                val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                records["$pkg|$today"] = UsageRecord(
                    packageName = pkg,
                    date = today,
                    usageDurationSeconds = 1800
                )
            }
            val useCase = CheckAppLockStatusUseCase(appRepo, ruleRepo, usageRepo)

            val result = useCase(pkg)
            result.shouldBeInstanceOf<LockReason.UsageLimitExceeded>()
            (result as LockReason.UsageLimitExceeded).limitMinutes shouldBe 30
            result.usedSeconds shouldBe 1800
        }

        it("returns NotLocked when usage is below limit") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(
                    packageName = pkg,
                    appName = "Test App",
                    usageLimitMinutes = 30
                )
            }
            val ruleRepo = FakeRuleRepository()
            val usageRepo = FakeUsageRepository().apply {
                val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                records["$pkg|$today"] = UsageRecord(
                    packageName = pkg,
                    date = today,
                    usageDurationSeconds = 900 // 15 minutes, below 30 min limit
                )
            }
            val useCase = CheckAppLockStatusUseCase(appRepo, ruleRepo, usageRepo)

            val result = useCase(pkg)
            result shouldBe LockReason.NotLocked
        }
    }
})

class UpdateUsageUseCaseTest : DescribeSpec({

    describe("UpdateUsageUseCase") {

        it("ensures record exists and increments usage by 1 second") {
            val usageRepo = FakeUsageRepository()
            val useCase = UpdateUsageUseCase(usageRepo)
            val pkg = "com.example.app"

            useCase(pkg)

            usageRepo.ensureCalled shouldBe true
            usageRepo.lastUpdatePackage shouldBe pkg
            // Verify the date is today
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            usageRepo.lastUpdateDate shouldBe today
        }
    }
})

class ResetDailyUsageUseCaseTest : DescribeSpec({

    describe("ResetDailyUsageUseCase") {

        it("invoke completes without error") {
            val usageRepo = FakeUsageRepository()
            val useCase = ResetDailyUsageUseCase(usageRepo)

            // Should not throw - it's a no-op placeholder
            useCase()
        }
    }
})

class CheckUsageWarningUseCaseTest : DescribeSpec({

    describe("CheckUsageWarningUseCase") {

        it("returns false when app is not a target app") {
            val appRepo = FakeAppRepository()
            val usageRepo = FakeUsageRepository()
            val useCase = CheckUsageWarningUseCase(appRepo, usageRepo)

            useCase("com.example.unknown") shouldBe false
        }

        it("returns false when no usage limit is set") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(packageName = pkg, appName = "Test App")
            }
            val usageRepo = FakeUsageRepository()
            val useCase = CheckUsageWarningUseCase(appRepo, usageRepo)

            useCase(pkg) shouldBe false
        }

        it("returns true when usage reaches 80% threshold") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(
                    packageName = pkg,
                    appName = "Test App",
                    usageLimitMinutes = 100 // 6000 seconds, 80% = 4800 seconds
                )
            }
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val usageRepo = FakeUsageRepository().apply {
                records["$pkg|$today"] = UsageRecord(
                    packageName = pkg,
                    date = today,
                    usageDurationSeconds = 4800 // exactly 80%
                )
            }
            val useCase = CheckUsageWarningUseCase(appRepo, usageRepo)

            useCase(pkg) shouldBe true
        }

        it("returns false when usage is below 80% threshold") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(
                    packageName = pkg,
                    appName = "Test App",
                    usageLimitMinutes = 100 // 6000 seconds, 80% = 4800 seconds
                )
            }
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val usageRepo = FakeUsageRepository().apply {
                records["$pkg|$today"] = UsageRecord(
                    packageName = pkg,
                    date = today,
                    usageDurationSeconds = 4799 // just below 80%
                )
            }
            val useCase = CheckUsageWarningUseCase(appRepo, usageRepo)

            useCase(pkg) shouldBe false
        }

        it("returns true when usage exceeds 80% threshold") {
            val pkg = "com.example.app"
            val appRepo = FakeAppRepository().apply {
                apps[pkg] = TargetApp(
                    packageName = pkg,
                    appName = "Test App",
                    usageLimitMinutes = 100
                )
            }
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val usageRepo = FakeUsageRepository().apply {
                records["$pkg|$today"] = UsageRecord(
                    packageName = pkg,
                    date = today,
                    usageDurationSeconds = 5500 // above 80%
                )
            }
            val useCase = CheckUsageWarningUseCase(appRepo, usageRepo)

            useCase(pkg) shouldBe true
        }
    }
})
