package com.routemate.app.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskParserTest {
    private val analyzer: TaskAnalyzer = RuleBasedTaskAnalyzer()
    private val today = LocalDate.of(2026, 11, 4) // Wednesday

    @Test fun requestedExampleHasIndependentDatesAndCategories() {
        val tasks = analyzer.analyze("오늘 퇴근할 때 우유 사고, 이번 주 금요일까지 세탁소에서 옷 찾아야 해.", today)
        assertEquals(2, tasks.size)
        assertTrue(tasks[0].title.contains("우유"))
        assertEquals(Category.SHOPPING, tasks[0].category)
        assertEquals(today, tasks[0].dueDate)
        assertEquals(Category.LAUNDRY, tasks[1].category)
        assertEquals(LocalDate.of(2026, 11, 6), tasks[1].dueDate)
        assertEquals("세탁소", tasks[1].place)
        assertFalse(tasks[1].title.contains("금요일"))
    }

    @Test fun dateIsSharedWithoutPunctuation() {
        val tasks = analyzer.analyze("오늘 우유 사고 세탁소 들르기", today)
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.dueDate == today })
        assertEquals("우유 사기", tasks[0].title)
    }

    @Test fun secondExplicitDateOverridesAndIsCarriedForward() {
        val tasks = analyzer.analyze("오늘 우유 사고 내일 세탁소 들르고 택배 보내기", today)
        assertEquals(3, tasks.size)
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(1)), tasks.map { it.dueDate })
    }

    @Test fun noDateRemainsNull() {
        val task = analyzer.analyze("우유 사기", today).single()
        assertNull(task.dueDate)
        assertEquals("", task.place)
        assertEquals(Priority.NORMAL, task.priority)
        assertEquals("", task.memo)
    }

    @Test fun laterDateDoesNotApplyBackwards() {
        val tasks = analyzer.analyze("우유 사기, 내일 택배 보내기", today)
        assertNull(tasks[0].dueDate)
        assertEquals(today.plusDays(1), tasks[1].dueDate)
    }

    @Test fun tomorrowAndDayAfterUseOriginalToday() {
        val tasks = analyzer.analyze("내일 우유 사기; 모레 약속", today)
        assertEquals(listOf(today.plusDays(1), today.plusDays(2)), tasks.map { it.dueDate })
    }

    @Test fun thisWeekFridayUsesCalendarWeek() {
        assertEquals(LocalDate.of(2026, 11, 6), analyzer.analyze("이번 주 금요일까지 세탁소 들르기", today).single().dueDate)
    }

    @Test fun thisWeekFridayMayAlreadyBePastOnSunday() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 6), analyzer.analyze("이번주 금요일 옷 찾기", sunday).single().dueDate)
    }

    @Test fun nextWeekFridayOnSundayIsFollowingCalendarWeek() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 13), analyzer.analyze("다음 주 금요일 세탁소 들르기", sunday).single().dueDate)
    }

    @Test fun nextWeekMondayCrossesYearBoundary() {
        val thursday = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 4), analyzer.analyze("다음주 월요일 약속", thursday).single().dueDate)
    }

    @Test fun bareFridayMeansUpcomingIncludingToday() {
        val friday = LocalDate.of(2026, 11, 6)
        assertEquals(friday, analyzer.analyze("금요일 장보기", friday).single().dueDate)
        assertEquals(friday.plusWeeks(1), analyzer.analyze("금요일 장보기", friday.plusDays(1)).single().dueDate)
    }

    @Test fun validIsoDateAndSuffixAreRemovedFromTitle() {
        val task = analyzer.analyze("2027-01-02까지 택배 보내기", today).single()
        assertEquals(LocalDate.of(2027, 1, 2), task.dueDate)
        assertEquals("택배 보내기", task.title)
    }

    @Test fun invalidCalendarDateDoesNotThrow() {
        assertNull(analyzer.analyze("2026-02-30 우유 사기", today).single().dueDate)
        assertNull(analyzer.analyze("2026-13-01 장보기", today).single().dueDate)
        assertNull(analyzer.analyze("2026-00-10 장보기", today).single().dueDate)
    }

    @Test fun validLeapDayIsAccepted() {
        assertEquals(LocalDate.of(2028, 2, 29), analyzer.analyze("2028-02-29 병원 예약", today).single().dueDate)
    }

    @Test fun invalidExplicitDateClearsSharedDate() {
        val tasks = analyzer.analyze("오늘 우유 사기, 2026-02-30 택배 보내기, 세탁소 들르기", today)
        assertEquals(3, tasks.size)
        assertEquals(today, tasks[0].dueDate)
        assertNull(tasks[1].dueDate)
        assertNull(tasks[2].dueDate)
    }

    @Test fun blankAndPunctuationOnlyInputReturnNoTasks() {
        assertTrue(analyzer.analyze("", today).isEmpty())
        assertTrue(analyzer.analyze(" \t\n ", today).isEmpty())
        assertTrue(analyzer.analyze(", ; .!?", today).isEmpty())
    }

    @Test fun standaloneDateOnlySetsContext() {
        val tasks = analyzer.analyze("오늘, 우유 사기", today)
        assertEquals(1, tasks.size)
        assertEquals(today, tasks.single().dueDate)
        assertTrue(analyzer.analyze("내일", today).isEmpty())
    }

    @Test fun clausesSplitAtNewlinesAndConjunctions() {
        val tasks = analyzer.analyze("오늘 우유 사기\n세탁소 들르기 그리고 택배 보내기", today)
        assertEquals(3, tasks.size)
        assertTrue(tasks.all { it.dueDate == today })
        assertEquals(listOf(Category.SHOPPING, Category.LAUNDRY, Category.PARCEL), tasks.map { it.category })
    }

    @Test fun urgencyAndLowPriorityAreClauseLocal() {
        val tasks = analyzer.analyze("긴급 택배 보내기, 천천히 장보기, 우유 사기", today)
        assertEquals(listOf(Priority.HIGH, Priority.LOW, Priority.NORMAL), tasks.map { it.priority })
    }

    @Test fun highPriorityWinsWhenBothKeywordsOccur() {
        assertEquals(Priority.HIGH, analyzer.analyze("긴급 천천히 우유 사기", today).single().priority)
    }

    @Test fun explicitPlaceAndAppointmentAreDetected() {
        val task = analyzer.analyze("내일 강남역에서 친구 만나기", today).single()
        assertEquals("강남역", task.place)
        assertEquals(Category.APPOINTMENT, task.category)
        assertEquals(today.plusDays(1), task.dueDate)
    }

    @Test fun namedVenueIsUsedWithoutLocationParticle() {
        assertEquals("세탁소", analyzer.analyze("세탁소 들르기", today).single().place)
        assertEquals("중앙우체국", analyzer.analyze("중앙우체국에서 소포 보내기", today).single().place)
    }

    @Test fun unknownTaskHasOtherCategoryAndNoInventedPlace() {
        val task = analyzer.analyze("책 읽기", today).single()
        assertEquals(Category.OTHER, task.category)
        assertEquals("", task.place)
        assertNull(task.dueDate)
    }

    @Test fun todayDoesNotImplyHighPriority() {
        assertEquals(Priority.NORMAL, analyzer.analyze("오늘 우유 사기", today).single().priority)
    }

    @Test fun dateContextDoesNotLeakAcrossAnalyzeCalls() {
        analyzer.analyze("오늘 우유 사기", today)
        assertNull(analyzer.analyze("택배 보내기", today).single().dueDate)
    }

    @Test fun firstDateInUnsplitClauseWins() {
        assertEquals(today, analyzer.analyze("오늘 내일 중 우유 사기", today).single().dueDate)
    }

    @Test fun wishExpressionIsNotSplitAtSago() {
        val tasks = analyzer.analyze("우유 사고 싶다", today)
        assertEquals(1, tasks.size)
        assertEquals("우유 사고 싶다", tasks.single().title)
    }

    @Test fun dateSuffixDoesNotConsumeBeginningOfNextWord() {
        val task = analyzer.analyze("오늘 에코백 구매하기", today).single()
        assertEquals("에코백 구매하기", task.title)
        assertEquals(today, task.dueDate)
    }

    @Test fun everydayUrgencyKeywordIsSupported() {
        assertEquals(Priority.HIGH, analyzer.analyze("빨리 택배 보내기", today).single().priority)
    }

    @Test fun contractDefaultsAndKoreanLabelsRemainStable() {
        val task = ParsedTask("책 읽기", Category.OTHER, null, "")
        assertEquals(Priority.NORMAL, task.priority)
        assertEquals("", task.memo)
        assertEquals(listOf("장보기", "세탁소", "택배", "약속", "기타"), Category.values().map { it.label })
        assertEquals(listOf("낮음", "보통", "높음"), Priority.values().map { it.label })
    }
}
