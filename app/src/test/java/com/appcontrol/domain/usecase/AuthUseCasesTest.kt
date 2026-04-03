package com.appcontrol.domain.usecase

import com.appcontrol.data.db.AppSettings
import com.appcontrol.domain.repository.AuthRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// region Fake

class FakeAuthRepository : AuthRepository {
    var settings = AppSettings()
    var passwordSet: String? = null

    override fun getSettings(): Flow<AppSettings?> = flowOf(settings)
    override suspend fun getSettingsSync(): AppSettings? = settings

    override suspend fun setPassword(passwordHash: String) {
        passwordSet = passwordHash
        settings = settings.copy(passwordHash = passwordHash)
    }

    override suspend fun verifyPassword(inputHash: String): Boolean =
        settings.passwordHash == inputHash

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        settings = settings.copy(biometricEnabled = enabled)
    }

    override suspend fun setForcedLockEnabled(enabled: Boolean) {
        settings = settings.copy(forcedLockEnabled = enabled)
    }

    override suspend fun updateLockoutState(lockoutUntil: Long?, failedAttempts: Int) {
        settings = settings.copy(lockoutUntil = lockoutUntil, failedAttempts = failedAttempts)
    }

    override suspend fun initializeSettings() {
        // no-op for tests
    }
}

// endregion

class SetPasswordUseCaseTest : DescribeSpec({

    describe("SetPasswordUseCase") {

        it("sets password with SHA-256 hash when length >= 4") {
            val repo = FakeAuthRepository()
            val useCase = SetPasswordUseCase(repo)

            useCase("1234")

            repo.passwordSet shouldBe PasswordHashUtil.sha256("1234")
        }

        it("throws IllegalArgumentException when password is shorter than 4 characters") {
            val repo = FakeAuthRepository()
            val useCase = SetPasswordUseCase(repo)

            shouldThrow<IllegalArgumentException> {
                useCase("123")
            }
        }
    }
})

class VerifyPasswordUseCaseTest : DescribeSpec({

    describe("VerifyPasswordUseCase") {

        it("returns Success when password is correct") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(passwordHash = PasswordHashUtil.sha256("1234"))
            }
            val useCase = VerifyPasswordUseCase(repo)

            val result = useCase("1234")
            result.shouldBeInstanceOf<VerifyPasswordUseCase.VerifyResult.Success>()
        }

        it("resets failed attempts on successful verification") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(
                    passwordHash = PasswordHashUtil.sha256("1234"),
                    failedAttempts = 3
                )
            }
            val useCase = VerifyPasswordUseCase(repo)

            useCase("1234")
            repo.settings.failedAttempts shouldBe 0
            repo.settings.lockoutUntil shouldBe null
        }

        it("returns WrongPassword with remaining attempts on wrong password") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(passwordHash = PasswordHashUtil.sha256("1234"))
            }
            val useCase = VerifyPasswordUseCase(repo)

            val result = useCase("wrong")
            result.shouldBeInstanceOf<VerifyPasswordUseCase.VerifyResult.WrongPassword>()
            (result as VerifyPasswordUseCase.VerifyResult.WrongPassword).remainingAttempts shouldBe 4
        }

        it("returns LockedOut after 5 consecutive wrong attempts") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(
                    passwordHash = PasswordHashUtil.sha256("1234"),
                    failedAttempts = 4
                )
            }
            val useCase = VerifyPasswordUseCase(repo)

            val result = useCase("wrong")
            result.shouldBeInstanceOf<VerifyPasswordUseCase.VerifyResult.LockedOut>()
        }

        it("returns LockedOut when currently locked out") {
            val futureTime = System.currentTimeMillis() + 60_000
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(
                    passwordHash = PasswordHashUtil.sha256("1234"),
                    lockoutUntil = futureTime
                )
            }
            val useCase = VerifyPasswordUseCase(repo)

            val result = useCase("1234")
            result.shouldBeInstanceOf<VerifyPasswordUseCase.VerifyResult.LockedOut>()
            (result as VerifyPasswordUseCase.VerifyResult.LockedOut).lockoutUntilMillis shouldBe futureTime
        }

        it("allows verification after lockout expires") {
            val pastTime = System.currentTimeMillis() - 1000
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(
                    passwordHash = PasswordHashUtil.sha256("1234"),
                    lockoutUntil = pastTime,
                    failedAttempts = 5
                )
            }
            val useCase = VerifyPasswordUseCase(repo)

            val result = useCase("1234")
            result.shouldBeInstanceOf<VerifyPasswordUseCase.VerifyResult.Success>()
        }
    }
})

class ChangePasswordUseCaseTest : DescribeSpec({

    describe("ChangePasswordUseCase") {

        it("returns true and updates password when old password is correct") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(passwordHash = PasswordHashUtil.sha256("old1"))
            }
            val useCase = ChangePasswordUseCase(repo)

            val result = useCase("old1", "new1")
            result shouldBe true
            repo.settings.passwordHash shouldBe PasswordHashUtil.sha256("new1")
        }

        it("returns false when old password is incorrect") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(passwordHash = PasswordHashUtil.sha256("old1"))
            }
            val useCase = ChangePasswordUseCase(repo)

            val result = useCase("wrong", "new1")
            result shouldBe false
        }

        it("throws when new password is shorter than 4 characters") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(passwordHash = PasswordHashUtil.sha256("old1"))
            }
            val useCase = ChangePasswordUseCase(repo)

            shouldThrow<IllegalArgumentException> {
                useCase("old1", "abc")
            }
        }
    }
})

class ToggleBiometricUseCaseTest : DescribeSpec({

    describe("ToggleBiometricUseCase") {

        it("enables biometric") {
            val repo = FakeAuthRepository()
            val useCase = ToggleBiometricUseCase(repo)

            useCase(true)
            repo.settings.biometricEnabled shouldBe true
        }

        it("disables biometric") {
            val repo = FakeAuthRepository().apply {
                settings = settings.copy(biometricEnabled = true)
            }
            val useCase = ToggleBiometricUseCase(repo)

            useCase(false)
            repo.settings.biometricEnabled shouldBe false
        }
    }
})

class PasswordHashUtilTest : DescribeSpec({

    describe("PasswordHashUtil") {

        it("produces consistent SHA-256 hash") {
            val hash1 = PasswordHashUtil.sha256("test")
            val hash2 = PasswordHashUtil.sha256("test")
            hash1 shouldBe hash2
        }

        it("produces different hashes for different inputs") {
            val hash1 = PasswordHashUtil.sha256("abc")
            val hash2 = PasswordHashUtil.sha256("def")
            (hash1 != hash2) shouldBe true
        }

        it("produces 64-character hex string") {
            val hash = PasswordHashUtil.sha256("anything")
            hash.length shouldBe 64
        }
    }
})
