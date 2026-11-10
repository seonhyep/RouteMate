package com.routemate.app.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val engine = RecommendationEngine()
    private val route = DemoRouteProvider().routes().first()
    private val trip = TripState(tripId = 7)
    private fun milk(id: Long = 1) = TaskItem(id = id, title = "우유 구매", category = Category.SHOPPING, dueDate = today)
    private fun suggest(tasks: List<TaskItem>, state: TripState = trip) = engine.suggest(tasks, route, state, today)
    @Test fun matchingCategoryGetsDemoPlace() { assertEquals("동네마트", suggest(listOf(milk())).single().place.name) }
    @Test fun completedTaskIsNeverRecommended() { assertTrue(suggest(listOf(milk().copy(status = TaskStatus.COMPLETED, completedAt = 1))).isEmpty()) }
    @Test fun acceptedStopIsNotRecommendedTwice() { assertTrue(suggest(listOf(milk().copy(status = TaskStatus.IN_PROGRESS))).isEmpty()) }
    @Test fun snoozeHidesOnlyCurrentTrip() {
        val tasks = listOf(milk().copy(snoozedTrip = trip.tripId))
        assertTrue(suggest(tasks).isEmpty())
        assertEquals(1, suggest(tasks, trip.copy(tripId = trip.tripId + 1)).size)
    }
    @Test fun pastDueMissedTaskReturnsNextTrip() {
        val task = milk().copy(status = TaskStatus.MISSED, dueDate = today.minusDays(2), snoozedTrip = 6)
        assertTrue(suggest(listOf(task)).single().reason.contains("미완료"))
    }
    @Test fun farFutureTasksDoNotDistract() { assertTrue(suggest(listOf(milk().copy(dueDate = today.plusDays(8)))).isEmpty()) }
    @Test fun seventhDayIsIncluded() { assertEquals(1, suggest(listOf(milk().copy(dueDate = today.plusDays(7)))).size) }
    @Test fun undatedTaskCanBeRecommended() { assertEquals(1, suggest(listOf(milk().copy(dueDate = null))).size) }
    @Test fun unknownCategoryDoesNotInventAPlace() { assertTrue(suggest(listOf(milk().copy(category = Category.OTHER))).isEmpty()) }
    @Test fun earliestDeadlineComesFirst() {
        val older = milk(2).copy(dueDate = today.minusDays(1))
        assertEquals(2L, suggest(listOf(milk(), older)).first().task.id)
    }
    @Test fun highPriorityBreaksDateTie() {
        assertEquals(2L, suggest(listOf(milk(), milk(2).copy(priority = Priority.HIGH))).first().task.id)
    }
    @Test fun mismatchedRegisteredPlaceIsDisclosed() { assertTrue(suggest(listOf(milk().copy(place = "실제 우리동네 마트"))).single().reason.contains("가상 장소")) }
    @Test fun arrivalDoesNotCompleteTask() {
        val task = milk().copy(status = TaskStatus.IN_PROGRESS)
        val next = DemoVehicleGateway().transition(trip.copy(phase = VehiclePhase.DRIVING), VehicleEvent.ARRIVE)
        assertEquals(VehiclePhase.ARRIVED, next.phase)
        assertEquals(TaskStatus.IN_PROGRESS, task.status)
        assertNull(task.completedAt)
        assertTrue(suggest(listOf(milk()), next).isEmpty())
    }
    @Test fun parkingHasNoNewRecommendationsUntilNextTrip() { assertTrue(suggest(listOf(milk()), trip.copy(phase = VehiclePhase.PARKED)).isEmpty()) }
}
