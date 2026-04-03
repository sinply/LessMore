package com.appcontrol.service.overlay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class LockOverlayContentTest : FunSpec({

    test("formatDurationPlain returns minutes only when less than 1 hour") {
        formatDurationPlain(0) shouldBe "0m"
        formatDurationPlain(59) shouldBe "0m"
        formatDurationPlain(60) shouldBe "1m"
        formatDurationPlain(300) shouldBe "5m"
        formatDurationPlain(3599) shouldBe "59m"
    }

    test("formatDurationPlain returns hours and minutes when 1 hour or more") {
        formatDurationPlain(3600) shouldBe "1h 0m"
        formatDurationPlain(3660) shouldBe "1h 1m"
        formatDurationPlain(7200) shouldBe "2h 0m"
        formatDurationPlain(7380) shouldBe "2h 3m"
        formatDurationPlain(86399) shouldBe "23h 59m"
    }

    test("formatDurationPlain property: hours component equals totalSeconds / 3600") {
        checkAll(Arb.long(0L..86400L)) { seconds ->
            val result = formatDurationPlain(seconds)
            val expectedHours = seconds / 3600
            val expectedMinutes = (seconds % 3600) / 60
            if (expectedHours > 0) {
                result shouldBe "${expectedHours}h ${expectedMinutes}m"
            } else {
                result shouldBe "${expectedMinutes}m"
            }
        }
    }
})
