package com.routemate.app.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class VehicleGatewayTest {
    private val vehicle = DemoVehicleGateway()
    @Test fun fullDemoLifecycle() {
        val initial = TripState()
        val driving = vehicle.transition(initial, VehicleEvent.START)
        val arrived = vehicle.transition(driving, VehicleEvent.ARRIVE)
        val parked = vehicle.transition(arrived, VehicleEvent.PARK)
        val prepared = vehicle.transition(parked, VehicleEvent.PREPARE)
        val next = vehicle.transition(prepared, VehicleEvent.START)
        assertEquals(0L, driving.tripId)
        assertEquals(VehiclePhase.ARRIVED, arrived.phase)
        assertEquals(VehiclePhase.PARKED, parked.phase)
        assertEquals(VehiclePhase.READY, prepared.phase)
        assertEquals(1L, next.tripId)
    }
    @Test fun duplicateStartDoesNotCreateATrip() {
        val state = TripState(phase = VehiclePhase.DRIVING, tripId = 5)
        assertEquals(state, vehicle.transition(state, VehicleEvent.START))
    }
    @Test fun cannotArriveBeforeStarting() { val state = TripState(); assertEquals(state, vehicle.transition(state, VehicleEvent.ARRIVE)) }
    @Test fun cannotParkBeforeArrival() { val state = TripState(phase = VehiclePhase.DRIVING); assertEquals(state, vehicle.transition(state, VehicleEvent.PARK)) }
    @Test fun routeIsPreservedAcrossEvents() { assertEquals("work", vehicle.transition(TripState(routeId = "work"), VehicleEvent.START).routeId) }
    @Test fun snoozeStaysHiddenOnTheSameDeparture() {
        val today = LocalDate.of(2026, 11, 4)
        val state = TripState(tripId = 3)
        val task = TaskItem(id = 1, title = "우유", category = Category.SHOPPING, dueDate = today, snoozedTrip = 3)
        val driving = vehicle.transition(state, VehicleEvent.START)
        assertTrue(RecommendationEngine().suggest(listOf(task), DemoRouteProvider().routes().first(), driving, today).isEmpty())
    }
    @Test fun nextTripRecommendationsAreActionableBeforeDriving() {
        val today = LocalDate.of(2026, 11, 4)
        val parked = TripState(tripId = 3, phase = VehiclePhase.PARKED)
        val prepared = vehicle.transition(parked, VehicleEvent.PREPARE)
        val task = TaskItem(id = 1, title = "우유", category = Category.SHOPPING, dueDate = today, status = TaskStatus.MISSED, snoozedTrip = 3)
        assertEquals(VehiclePhase.READY, prepared.phase)
        assertEquals(1, RecommendationEngine().suggest(listOf(task), DemoRouteProvider().routes().first(), prepared, today).size)
    }
    @Test fun preparingTwiceDoesNotSkipTripIds() { val ready = TripState(tripId = 3); assertEquals(ready, vehicle.transition(ready, VehicleEvent.PREPARE)) }
}
