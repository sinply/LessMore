package com.appcontrol.domain.usecase

import com.appcontrol.data.db.UsageRecord
import com.appcontrol.domain.repository.UsageRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

// region Fake

class FakeStatsUsageRepository : UsageRepository {
    var records = mutableListOf<UsageRecord>()
    var lastCleanBeforeDate: String? = null
    var lastWeeklyStartDate: String? = null
    var lastWeeklyEndDate: String? = null

    override suspend fun getUsageRecord(packageName: String, date: String): UsageRecord? =
        records.find { it.packageName == packageName && it.date == date }

    override fun getDailyStats(date: String): Flow<List<UsageRecord>> =
        flowOf(records.filter { it.date == date })

    override fun getWeeklyStats(startDate: String, endDate: String): Flow<List<UsageRecord>> {
        lastWeeklyStartDate = startDate
        lastWeeklyEndDate = endDate
        return flowOf(records.filter { it.date >= startDate && it.date <= endDate })
    }

    override suspend fun updateUsageDuration(packageName: String, date: String, additionalSeconds: Long) {}
    override suspend fun incrementOpenCount(packageName: String, date: String) {}
    override suspend fun ensureRecordExists(packageName: String, date: String) {}

    override suspend fun cleanOldData(beforeDate: String) {
        lastCleanBeforeDate = beforeDate
        records.removeAll { it.date < beforeDate }
    }
}

// endregion

class GetDailyStatsUseCaseTest : DescribeSpec({

    describe("GetDailyStatsUseCase") {

        it("returns usage records for the given date") {
            val repo = FakeStatsUsageRepository().apply {
                records.addAll(listOf(
                    UsageRecord(packageName = "com.app.a", date = "2024-01-15", usageDurationSeconds = 3600, openCount = 5),
                    UsageRecord(packageName = "com.app.b", date = "2024-01-15", usageDurationSeconds = 1800, openCount = 3),
                    UsageRecord(packageName = "com.app.a", date = "2024-01-14", usageDurationSeconds = 900, openCount = 2)
                ))
            }
            val useCase = GetDailyStatsUseCase(repo)

            val result = useCase("2024-01-15").first()

            result.size shouldBe 2
            result[0].packageName shouldBe "com.app.a"
            result[0].usageDurationSeconds shouldBe 3600
            result[1].packageName shouldBe "com.app.b"
        }

        it("returns empty list when no records exist for the date") {
            val repo = FakeStatsUsageRepository()
            val useCase = GetDailyStatsUseCase(repo)

            val result = useCase("2024-01-15").first()

            result.size shouldBe 0
        }
    }
})

class GetWeeklyStatsUseCaseTest : DescribeSpec({

    describe("GetWeeklyStatsUseCase") {

        it("delegates to repository with correct 7-day date range") {
            val repo = FakeStatsUsageRepository()
            val useCase = GetWeeklyStatsUseCase(repo)

            useCase().first()

            val today = java.time.LocalDate.now()
            val expectedStart = today.minusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val expectedEnd = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            repo.lastWeeklyStartDate shouldBe expectedStart
            repo.lastWeeklyEndDate shouldBe expectedEnd
        }

        it("returns records within the weekly range") {
            val today = java.time.LocalDate.now()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val repo = FakeStatsUsageRepository().apply {
                records.addAll(listOf(
                    UsageRecord(packageName = "com.app.a", date = today.format(fmt), usageDurationSeconds = 3600, openCount = 5),
                    UsageRecord(packageName = "com.app.a", date = today.minusDays(3).format(fmt), usageDurationSeconds = 1800, openCount = 2),
                    UsageRecord(packageName = "com.app.a", date = today.minusDays(10).format(fmt), usageDurationSeconds = 900, openCount = 1)
                ))
            }
            val useCase = GetWeeklyStatsUseCase(repo)

            val result = useCase().first()

            result.size shouldBe 2
        }
    }
})

class CleanOldDataUseCaseTest : DescribeSpec({

    describe("CleanOldDataUseCase") {

        it("delegates to repository with date 30 days ago") {
            val repo = FakeStatsUsageRepository()
            val useCase = CleanOldDataUseCase(repo)

            useCase()

            val expected = java.time.LocalDate.now().minusDays(30)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            repo.lastCleanBeforeDate shouldBe expected
        }

        it("removes records older than 30 days") {
            val today = java.time.LocalDate.now()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val repo = FakeStatsUsageRepository().apply {
                records.addAll(listOf(
                    UsageRecord(packageName = "com.app.a", date = today.format(fmt), usageDurationSeconds = 100, openCount = 1),
                    UsageRecord(packageName = "com.app.a", date = today.minusDays(31).format(fmt), usageDurationSeconds = 200, openCount = 2)
                ))
            }
            val useCase = CleanOldDataUseCase(repo)

            useCase()

            repo.records.size shouldBe 1
            repo.records[0].date shouldBe today.format(fmt)
        }
    }
})
