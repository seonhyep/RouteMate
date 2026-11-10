package com.routemate.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.routemate.app.core.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private lateinit var db: RouteMateDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "room-test-" + UUID.randomUUID() + ".db"
    @Before fun open() { db = Room.databaseBuilder(context, RouteMateDatabase::class.java, name).build() }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    @Test fun samplesSeedExactlyOnceEvenAfterDeletingAllTasks() = runBlocking {
        val repo = TaskRepository(db)
        repo.seed(); repo.seed()
        assertEquals(3, repo.currentTasks().size)
        repo.currentTasks().forEach { repo.delete(it.id) }
        repo.seed()
        assertTrue(repo.currentTasks().isEmpty())
    }
    @Test fun completionAndTripSurviveDatabaseReopen() = runBlocking {
        val repo = TaskRepository(db); repo.seed()
        val task = repo.currentTasks().first()
        val completedAt = System.currentTimeMillis()
        repo.save(task.copy(status = TaskStatus.COMPLETED, completedAt = completedAt))
        repo.transition(TripState(routeId = "work", phase = VehiclePhase.ARRIVED, tripId = 9), emptyList())
        db.close()
        db = Room.databaseBuilder(context, RouteMateDatabase::class.java, name).build()
        val reopened = TaskRepository(db)
        val saved = reopened.currentTasks().first { it.id == task.id }
        assertEquals(TaskStatus.COMPLETED, saved.status)
        assertEquals(completedAt, saved.completedAt)
        assertEquals(9L, reopened.currentTrip().tripId)
        assertEquals(VehiclePhase.ARRIVED, reopened.currentTrip().phase)
    }
    @Test fun batchRollbackDoesNotLeavePartialTasks() = runBlocking {
        val repo = TaskRepository(db); repo.seed()
        val initial = repo.currentTasks().size
        try {
            repo.transaction {
                repo.saveAll(listOf(TaskItem(title = "롤백 대상 1"), TaskItem(title = "롤백 대상 2")))
                error("simulate failure before commit")
            }
        } catch (_: IllegalStateException) { /* expected: the entire save must roll back */ }
        assertEquals(initial, repo.currentTasks().size)
        assertTrue(repo.currentTasks().none { it.title.startsWith("롤백 대상") })
    }
    @Test fun snoozeAndEditSurviveReopen() = runBlocking {
        val repo = TaskRepository(db); repo.seed()
        val task = repo.currentTasks().first()
        repo.save(task.copy(title = "수정한 할 일", dueDate = LocalDate.of(2026, 12, 1), memo = "저장 확인", snoozedTrip = 4))
        db.close()
        db = Room.databaseBuilder(context, RouteMateDatabase::class.java, name).build()
        val saved = TaskRepository(db).currentTasks().first { it.id == task.id }
        assertEquals("수정한 할 일", saved.title)
        assertEquals(4L, saved.snoozedTrip)
        assertEquals("저장 확인", saved.memo)
    }
}
