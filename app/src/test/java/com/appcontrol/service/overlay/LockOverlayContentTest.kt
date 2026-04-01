package com.appcontrol.service.overlay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class LockOverlayContentTest : FunSpec({

    test("formatDuration returns minutes only when less than 1 hour") {
        formatDuration(0) shouldBe "0分钟"
        formatDuration(59) shouldBe "0分钟"
        formatDuration(60) shouldBe "1分钟"
        formatDuration(300) shouldBe "5分钟"
        formatDuration(3599) shouldBe "59分钟"
    }

    test("formatDuration returns hours and minutes when 1 hour or more") {
        formatDuration(3600) shouldBe "1小时0分钟"
        formatDuration(3660) shouldBe "1小时1分钟"
        formatDuration(7200) shouldBe "2小时0分钟"
        formatDuration(7380) shouldBe "2小时3分钟"
        formatDuration(86399) shouldBe "23小时59分钟"
    }

    test("formatDuration property: hours component equals totalSeconds / 3600") {
        checkAll(Arb.long(0L..86400L)) { seconds ->
            val result = formatDuration(seconds)
            val expectedHours = seconds / 3600
            val expectedMinutes = (seconds % 3600) / 60
            if (expectedHours > 0) {
                result shouldBe "${expectedHours}小时${expectedMinutes}分钟"
            } else {
                result shouldBe "${expectedMinutes}分钟"
            }
        }
    }
})
