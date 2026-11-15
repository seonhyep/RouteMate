package com.routemate.app.core

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class Category(val label: String) {
    SHOPPING("장보기"), LAUNDRY("세탁소"), PARCEL("택배"), APPOINTMENT("약속"), OTHER("기타")
}

enum class Priority(val label: String) {
    LOW("낮음"), NORMAL("보통"), HIGH("높음")
}

data class ParsedTask(
    val title: String,
    val category: Category,
    val dueDate: LocalDate?,
    val place: String,
    val priority: Priority = Priority.NORMAL,
    val memo: String = ""
)

interface TaskAnalyzer {
    fun analyze(text: String, today: LocalDate = LocalDate.now()): List<ParsedTask>
}

/**
 * Deterministic, limited OFFLINE Korean rules, not AI or general language analysis.
 *
 * Dates: 오늘/내일/모레, YYYY-MM-DD, 이번 주/다음 주 + weekday, or a bare weekday.
 * A week starts Monday. "이번 주 금요일" means that calendar week's Friday,
 * even on Saturday/Sunday when it is already past. "다음 주 금요일" means the
 * following calendar week's Friday. Bare "금요일" means the next Friday,
 * including today. Relative dates always use today, never an earlier task's date.
 *
 * A date carries forward across clauses until an explicit date replaces it.
 * An invalid explicit ISO date clears that context to null rather than throwing
 * or silently retaining the preceding date. The first date in a single clause
 * wins; times, holidays and numeric Korean date expressions are unsupported.
 *
 * Clauses split on punctuation, 그리고/그다음, and selected connective verbs
 * such as 사고/찾고. Titles retain phrasing except dates and terminal connective
 * normalization. Category and priority use keywords, not semantic inference.
 * Places are one explicit token before 에/에서/으로, or a venue named in the
 * text. No shop is invented from a shopping item. Memo defaults to empty.
 */
class RuleBasedTaskAnalyzer : TaskAnalyzer {
    override fun analyze(text: String, today: LocalDate): List<ParsedTask> {
        if (text.isBlank()) return emptyList()
        var sharedDate: LocalDate? = null
        val tasks = mutableListOf<ParsedTask>()
        for (rawClause in splitClauses(text)) {
            val clause = rawClause.trim()
            if (clause.isEmpty()) continue
            val dateMatch = DATE.find(clause)
            if (dateMatch != null) sharedDate = resolveDate(dateMatch.groupValues[1], today)
            val title = cleanTitle(DATE.replace(clause, " "))
            // A standalone date changes context but is not itself a task.
            if (title.isBlank()) continue
            tasks += ParsedTask(
                title = title,
                category = categoryOf(title),
                dueDate = sharedDate,
                place = placeOf(title),
                priority = priorityOf(title)
            )
        }
        return tasks
    }

    private fun splitClauses(text: String): List<String> {
        val coordinated = CONJUNCTION.replace(text, "\n")
        val connected = CONNECTIVE.replace(coordinated) { it.value + "\n" }
        return SEPARATOR.split(connected)
    }

    private fun resolveDate(token: String, today: LocalDate): LocalDate? {
        val compact = token.replace(WHITESPACE, "")
        return try {
            when (compact) {
                "오늘" -> today
                "내일" -> today.plusDays(1)
                "모레" -> today.plusDays(2)
                else -> {
                    if (ISO.matches(compact)) {
                        LocalDate.parse(compact)
                    } else {
                        val dayCharacter = compact[compact.length - 3]
                        val day = DayOfWeek.of("월화수목금토일".indexOf(dayCharacter) + 1)
                        when {
                            compact.startsWith("이번주") -> today
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .plusDays((day.value - 1).toLong())
                            compact.startsWith("다음주") -> today
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .plusWeeks(1).plusDays((day.value - 1).toLong())
                            else -> today.with(TemporalAdjusters.nextOrSame(day))
                        }
                    }
                }
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun cleanTitle(text: String): String {
        var title = text.replace(WHITESPACE, " ").trim()
        title = title.replace(LEADING_CONJUNCTION, "").trim()
        val ending = CONNECTIVE_END.find(title)
        if (ending != null) {
            val replacement = CONNECTIVE_TITLES[ending.value]
            if (replacement != null) title = title.replaceRange(ending.range, replacement)
        }
        return title
    }

    private fun categoryOf(text: String): Category = when {
        containsAny(text, LAUNDRY_WORDS) -> Category.LAUNDRY
        containsAny(text, PARCEL_WORDS) -> Category.PARCEL
        containsAny(text, APPOINTMENT_WORDS) -> Category.APPOINTMENT
        containsAny(text, SHOPPING_WORDS) -> Category.SHOPPING
        else -> Category.OTHER
    }

    private fun priorityOf(text: String): Priority = when {
        containsAny(text, HIGH_WORDS) -> Priority.HIGH
        containsAny(text, LOW_WORDS) -> Priority.LOW
        else -> Priority.NORMAL
    }

    private fun placeOf(text: String): String {
        val explicit = PLACE.findAll(text).map { it.groupValues[1] }
            .firstOrNull { it !in NON_PLACES }
        return explicit ?: VENUE.find(text)?.value.orEmpty()
    }

    private fun containsAny(text: String, words: List<String>): Boolean =
        words.any { text.contains(it, ignoreCase = true) }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val ISO = Regex("\\d{4}-\\d{2}-\\d{2}")
        val DATE = Regex(
            "((?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)|" +
                "(?:이번\\s*주|다음\\s*주)\\s*[월화수목금토일]요일|" +
                "오늘|내일|모레|[월화수목금토일]요일)(?:\\s*(?:까지|에는|에|쯤|내로)(?=\\s|$))?"
        )
        val SEPARATOR = Regex("[,;\\r\\n.!?。！？]+")
        val CONJUNCTION = Regex("\\s+(?:그리고|그다음|그\\s+다음)\\s+")
        val LEADING_CONJUNCTION = Regex("^(?:그리고|그다음|그\\s+다음)\\s+")
        val CONNECTIVE_TITLES = linkedMapOf(
            "구매하고" to "구매하기", "수령하고" to "수령하기",
            "예약하고" to "예약하기", "납부하고" to "납부하기",
            "방문하고" to "방문하기", "신청하고" to "신청하기",
            "접수하고" to "접수하기", "픽업하고" to "픽업하기",
            "들르고" to "들르기", "보내고" to "보내기",
            "챙기고" to "챙기기", "만나고" to "만나기",
            "찾고" to "찾기", "사고" to "사기"
        )
        val CONNECTIVE = Regex(
            "(?:" + CONNECTIVE_TITLES.keys.joinToString("|") +
                ")(?=\\s+(?!(?:싶|나서|나면|나니))\\S)"
        )
        val CONNECTIVE_END = Regex("(?:" + CONNECTIVE_TITLES.keys.joinToString("|") + ")$")
        val PLACE = Regex("([가-힣A-Za-z0-9][가-힣A-Za-z0-9·._-]*?)(?:에서|으로|에)(?=\\s|$)")
        val NON_PLACES = setOf("오전", "오후", "저녁", "아침", "점심", "밤", "나중", "먼저")
        val VENUE = Regex("[가-힣A-Za-z0-9]*(?:세탁소|우체국|편의점|마트|백화점|병원|치과|약국|카페|공항|도서관|역)")
        val LAUNDRY_WORDS = listOf("세탁소", "세탁", "드라이클리닝", "드라이 클리닝")
        val PARCEL_WORDS = listOf("택배", "우체국", "소포", "등기", "배송", "반품")
        val APPOINTMENT_WORDS = listOf("약속", "병원", "치과", "진료", "예약", "미팅", "회의", "만나")
        val SHOPPING_WORDS = listOf("장보기", "장 보기", "마트", "쇼핑", "구매", "사기", "사고", "사야", "사러", "우유", "빵", "달걀", "계란", "과일", "채소")
        val HIGH_WORDS = listOf("긴급", "급하게", "급히", "급한", "서둘러", "빨리", "급해", "급함", "최우선", "중요")
        val LOW_WORDS = listOf("천천히", "여유", "나중에", "시간 나면", "시간나면")
    }
}
