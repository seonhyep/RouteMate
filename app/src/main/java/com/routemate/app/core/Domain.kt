package com.routemate.app.core

import java.time.LocalDate

enum class TaskStatus(val label: String) {
    PLANNED("예정"), RECOMMENDED("실행 추천"), IN_PROGRESS("진행 중"), COMPLETED("완료"), MISSED("미완료")
}
data class TaskItem(
    val id: Long = 0,
    val title: String,
    val category: Category = Category.OTHER,
    val dueDate: LocalDate? = null,
    val place: String = "",
    val priority: Priority = Priority.NORMAL,
    val status: TaskStatus = TaskStatus.PLANNED,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val snoozedTrip: Long = -1,
    val isSample: Boolean = false,
    val demoStop: String? = null
)
enum class VehiclePhase(val label: String) { READY("출발 전"), DRIVING("주행 중"), ARRIVED("목적지 도착"), PARKED("주차 완료") }
enum class VehicleEvent { START, ARRIVE, PARK, PREPARE }
data class TripState(val routeId: String = "home", val phase: VehiclePhase = VehiclePhase.READY, val tripId: Long = 0)
data class DemoPlace(val name: String, val category: Category, val extraMinutes: Int)
data class RoutePlan(val id: String, val origin: String, val destination: String, val minutes: Int, val places: List<DemoPlace>)
interface RouteProvider { val isDemo: Boolean; fun routes(): List<RoutePlan> }
class DemoRouteProvider : RouteProvider {
    override val isDemo = true
    override fun routes() = listOf(
        RoutePlan("home", "회사", "집", 28, listOf(DemoPlace("동네마트", Category.SHOPPING, 4), DemoPlace("깨끗한 세탁소", Category.LAUNDRY, 6), DemoPlace("편의점 택배", Category.PARCEL, 5))),
        RoutePlan("work", "집", "회사", 32, listOf(DemoPlace("출근길 마트", Category.SHOPPING, 5), DemoPlace("회사 앞 우체국", Category.PARCEL, 7))),
        RoutePlan("errands", "집", "동네 생활권", 15, listOf(DemoPlace("동네마트", Category.SHOPPING, 3), DemoPlace("깨끗한 세탁소", Category.LAUNDRY, 4), DemoPlace("동네 우체국", Category.PARCEL, 6), DemoPlace("약속 장소 (가상)", Category.APPOINTMENT, 8)))
    )
}
// A future Pleos adapter can replace this source. The MVP uses buttons only.
interface VehicleGateway { fun transition(state: TripState, event: VehicleEvent): TripState }
class DemoVehicleGateway : VehicleGateway {
    override fun transition(state: TripState, event: VehicleEvent): TripState = when (event) {
        VehicleEvent.START -> if (state.phase == VehiclePhase.READY) state.copy(phase = VehiclePhase.DRIVING) else state
        VehicleEvent.ARRIVE -> if (state.phase == VehiclePhase.DRIVING) state.copy(phase = VehiclePhase.ARRIVED) else state
        VehicleEvent.PARK -> if (state.phase == VehiclePhase.ARRIVED) state.copy(phase = VehiclePhase.PARKED) else state
        VehicleEvent.PREPARE -> if (state.phase == VehiclePhase.PARKED) state.copy(phase = VehiclePhase.READY, tripId = state.tripId + 1) else state
    }
}
data class Recommendation(val task: TaskItem, val place: DemoPlace, val reason: String)
class RecommendationEngine {
    fun suggest(tasks: List<TaskItem>, route: RoutePlan, trip: TripState, today: LocalDate): List<Recommendation> {
        if (trip.phase == VehiclePhase.ARRIVED || trip.phase == VehiclePhase.PARKED) return emptyList()
        return tasks.asSequence()
            .filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.IN_PROGRESS && it.snoozedTrip != trip.tripId }
            .filter { it.dueDate == null || !it.dueDate.isAfter(today.plusDays(7)) }
            .mapNotNull { task -> route.places.firstOrNull { it.category == task.category }?.let { place ->
                val deadline = when {
                    task.dueDate == null -> "날짜가 정해지지 않은 할 일이에요."
                    task.dueDate.isBefore(today) -> "마감이 지난 미완료 업무예요."
                    task.dueDate == today -> "오늘까지 처리할 일이에요."
                    else -> "7일 안에 마감되는 일이에요."
                }
                val match = if (task.place.isNotBlank() && task.place != place.name) "등록 장소 대신 같은 카테고리의 가상 장소를 제안해요. 실제 위치는 확인하지 않았어요." else "선택한 데모 경로의 샘플 장소예요."
                Recommendation(task, place, deadline + " " + match)
            } }
            .sortedWith(compareBy<Recommendation> { it.task.dueDate ?: LocalDate.MAX }.thenByDescending { it.task.priority.ordinal }.thenBy { it.place.extraMinutes })
            .toList()
    }
}
