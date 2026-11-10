package com.routemate.app.data

import android.content.Context
import androidx.room.*
import com.routemate.app.core.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "tasks")
data class TaskRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val dueDate: String?,
    val place: String,
    val priority: String,
    val status: String,
    val memo: String,
    val createdAt: Long,
    val completedAt: Long?,
    val snoozedTrip: Long,
    val isSample: Boolean,
    val demoStop: String?
) {
    fun domain() = TaskItem(id, title, Category.valueOf(category), dueDate?.let(LocalDate::parse), place, Priority.valueOf(priority), TaskStatus.valueOf(status), memo, createdAt, completedAt, snoozedTrip, isSample, demoStop)
    companion object {
        fun from(t: TaskItem) = TaskRecord(t.id, t.title, t.category.name, t.dueDate?.toString(), t.place, t.priority.name, t.status.name, t.memo, t.createdAt, t.completedAt, t.snoozedTrip, t.isSample, t.demoStop)
    }
}
@Entity(tableName = "settings")
data class SettingsRecord(@PrimaryKey val id: Int = 1, val routeId: String = "home", val phase: String = "READY", val tripId: Long = 0) {
    fun domain() = TripState(routeId, VehiclePhase.valueOf(phase), tripId)
}
@Dao
abstract class RouteMateDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC, id DESC") abstract fun observeTasks(): Flow<List<TaskRecord>>
    @Query("SELECT * FROM settings WHERE id = 1") abstract fun observeSettings(): Flow<SettingsRecord?>
    @Query("SELECT * FROM settings WHERE id = 1") abstract suspend fun settings(): SettingsRecord?
    @Query("SELECT * FROM tasks") abstract suspend fun allTasks(): List<TaskRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveSettings(settings: SettingsRecord)
    @Upsert abstract suspend fun upsertTask(task: TaskRecord): Long
    @Insert abstract suspend fun insertTasks(tasks: List<TaskRecord>)
    @Transaction open suspend fun upsertTasks(tasks: List<TaskRecord>) { tasks.forEach { upsertTask(it) } }
    @Query("DELETE FROM tasks WHERE id = :id") abstract suspend fun delete(id: Long)
    @Transaction open suspend fun seedOnce(samples: List<TaskRecord>) {
        if (settings() == null) { saveSettings(SettingsRecord()); insertTasks(samples) }
    }
    @Transaction open suspend fun saveTransition(settings: SettingsRecord, tasks: List<TaskRecord>) {
        saveSettings(settings)
        tasks.forEach { upsertTask(it) }
    }
}
@Database(entities = [TaskRecord::class, SettingsRecord::class], version = 1, exportSchema = true)
abstract class RouteMateDatabase : RoomDatabase() {
    abstract fun dao(): RouteMateDao
    companion object {
        @Volatile private var instance: RouteMateDatabase? = null
        fun get(context: Context): RouteMateDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RouteMateDatabase::class.java, "routemate.db").build().also { instance = it }
        }
    }
}
