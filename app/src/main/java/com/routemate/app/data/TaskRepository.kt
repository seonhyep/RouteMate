package com.routemate.app.data

import androidx.room.withTransaction

import com.routemate.app.core.*
import java.time.LocalDate
import kotlinx.coroutines.flow.map

class TaskRepository(private val database: RouteMateDatabase) {
    private val dao = database.dao()
    suspend fun transaction(block: suspend () -> Unit) = database.withTransaction { block() }
    val tasks = dao.observeTasks().map { rows -> rows.map { it.domain() } }
    val trip = dao.observeSettings().map { it?.domain() ?: TripState() }
    suspend fun seed() {
        val today = LocalDate.now()
        dao.seedOnce(listOf(
            TaskItem(title = "우유 구매", category = Category.SHOPPING, dueDate = today, place = "동네마트", priority = Priority.HIGH, memo = "우유 1팩 · 샘플 할 일", isSample = true),
            TaskItem(title = "세탁소에서 옷 찾기", category = Category.LAUNDRY, dueDate = today.minusDays(1), place = "깨끗한 세탁소", status = TaskStatus.MISSED, memo = "접수증 챙기기 · 샘플 할 일", isSample = true),
            TaskItem(title = "택배 보내기", category = Category.PARCEL, dueDate = today.plusDays(2), place = "편의점 택배", memo = "포장한 물건 챙기기 · 샘플 할 일", isSample = true)
        ).map(TaskRecord::from))
    }
    suspend fun save(task: TaskItem) { dao.upsertTask(TaskRecord.from(task)) }
    suspend fun saveAll(tasks: List<TaskItem>) { dao.upsertTasks(tasks.map(TaskRecord::from)) }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun currentTasks() = dao.allTasks().map { it.domain() }
    suspend fun currentTrip() = dao.settings()?.domain() ?: TripState()
    suspend fun transition(trip: TripState, tasks: List<TaskItem>) = dao.saveTransition(SettingsRecord(routeId = trip.routeId, phase = trip.phase.name, tripId = trip.tripId), tasks.map(TaskRecord::from))
}
