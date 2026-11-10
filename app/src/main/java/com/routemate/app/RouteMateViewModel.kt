package com.routemate.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.core.*
import com.routemate.app.data.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RouteMateUiState(
    val tasks: List<TaskItem> = emptyList(),
    val trip: TripState = TripState(),
    val today: LocalDate = LocalDate.now(),
    val routes: List<RoutePlan> = DemoRouteProvider().routes(),
    val recommendations: List<Recommendation> = emptyList(),
    val busy: Boolean = true
) {
    val route: RoutePlan get() = routes.firstOrNull { it.id == trip.routeId } ?: routes.first()
    val driving get() = trip.phase == VehiclePhase.DRIVING
    val active get() = tasks.filter { it.status != TaskStatus.COMPLETED }
    val todayTasks get() = active.filter { it.dueDate == null || !it.dueDate.isAfter(today) }
    val todayCompleted get() = tasks.count { it.completedAt?.let { time -> java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today } == true }
    val urgent get() = active.count { it.dueDate?.let { due -> !due.isAfter(today.plusDays(1)) } == true }
}
class RouteMateViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TaskRepository(RouteMateDatabase.get(application))
    private val analyzer: TaskAnalyzer = RuleBasedTaskAnalyzer()
    private val routes: RouteProvider = DemoRouteProvider()
    private val vehicle: VehicleGateway = DemoVehicleGateway()
    private val engine = RecommendationEngine()
    private val lock = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val today = MutableStateFlow(LocalDate.now())
    private val busy = MutableStateFlow(true)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()
    val ui = combine(repo.tasks, repo.trip, today, busy) { tasks, trip, date, working ->
        val allRoutes = routes.routes()
        val route = allRoutes.firstOrNull { it.id == trip.routeId } ?: allRoutes.first()
        RouteMateUiState(tasks, trip, date, allRoutes, engine.suggest(tasks, route, trip, date), working)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RouteMateUiState())
    init {
        viewModelScope.launch {
            try { repo.seed(); reconcile(); ready.complete(Unit) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { ready.completeExceptionally(e); _messages.emit("로컬 저장소를 열지 못했어요. 앱을 다시 실행해 주세요.") }
            finally { busy.value = false }
        }
    }
    fun analyze(text: String) = analyzer.analyze(text, LocalDate.now())
    fun refreshDate() {
        val now = LocalDate.now()
        if (today.value != now) { today.value = now; perform { reconcile() } }
    }
    private fun perform(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { ready.await(); lock.withLock { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _messages.emit("저장하지 못했어요. 다시 시도해 주세요.") }
            finally { busy.value = false }
        }
    }
    private suspend fun reconcile() {
        val trip = repo.currentTrip()
        val tasks = repo.currentTasks()
        val eligible = engine.suggest(tasks, routes.routes().first { it.id == trip.routeId }, trip, today.value).map { it.task.id }.toSet()
        val updated = tasks.map { task ->
            when {
                task.status == TaskStatus.COMPLETED || task.status == TaskStatus.IN_PROGRESS -> task
                task.id in eligible -> task.copy(status = TaskStatus.RECOMMENDED)
                task.status == TaskStatus.MISSED || task.dueDate?.isBefore(today.value) == true -> task.copy(status = TaskStatus.MISSED)
                else -> task.copy(status = TaskStatus.PLANNED)
            }
        }
        repo.transition(trip, updated)
    }
    fun saveDrafts(drafts: List<TaskItem>, onSaved: () -> Unit) = perform {
        if (repo.currentTrip().phase == VehiclePhase.DRIVING) { _messages.emit("정차 후 등록해 주세요."); return@perform }
        check(drafts.isNotEmpty() && drafts.all { it.title.isNotBlank() })
        repo.transaction {
            repo.saveAll(drafts.map { it.copy(title = it.title.trim()) })
            reconcile()
        }
        _messages.emit("할 일을 저장했어요.")
        onSaved()
    }
    fun delete(task: TaskItem) = perform {
        if (repo.currentTrip().phase == VehiclePhase.DRIVING) return@perform
        repo.delete(task.id); reconcile(); _messages.emit("할 일을 삭제했어요.")
    }
    fun toggleComplete(task: TaskItem) = perform {
        if (repo.currentTrip().phase == VehiclePhase.DRIVING) return@perform
        val current = repo.currentTasks().firstOrNull { it.id == task.id } ?: return@perform
        val done = current.status != TaskStatus.COMPLETED
        repo.save(current.copy(status = if (done) TaskStatus.COMPLETED else TaskStatus.PLANNED, completedAt = if (done) System.currentTimeMillis() else null, snoozedTrip = -1, demoStop = if (done) current.demoStop else null))
        reconcile(); _messages.emit(if (done) "완료 기록에 반영했어요." else "완료를 취소했어요.")
    }
    fun approve(recommendation: Recommendation) = perform {
        val trip = repo.currentTrip()
        if (trip.phase != VehiclePhase.READY) return@perform
        val current = repo.currentTasks().firstOrNull { it.id == recommendation.task.id && it.status != TaskStatus.COMPLETED && it.completedAt == null } ?: return@perform
        repo.save(current.copy(status = TaskStatus.IN_PROGRESS, demoStop = recommendation.place.name, snoozedTrip = -1))
        reconcile(); _messages.emit("데모 경유지에 추가했어요. 실제 내비게이션에는 반영되지 않아요.")
    }
    fun snooze(task: TaskItem) = perform {
        val trip = repo.currentTrip()
        if (trip.phase == VehiclePhase.DRIVING) return@perform
        val current = repo.currentTasks().firstOrNull { it.id == task.id } ?: return@perform
        repo.save(current.copy(status = if (current.dueDate?.isBefore(today.value) == true) TaskStatus.MISSED else TaskStatus.PLANNED, snoozedTrip = trip.tripId))
        reconcile(); _messages.emit("이번 이동에서는 숨기고 다음 주행에 다시 추천할게요.")
    }
    fun selectRoute(id: String) = perform {
        val trip = repo.currentTrip()
        if ((trip.phase != VehiclePhase.READY && trip.phase != VehiclePhase.PARKED) || routes.routes().none { it.id == id } || trip.routeId == id) return@perform
        val tasks = repo.currentTasks().map { if (it.status == TaskStatus.IN_PROGRESS) it.copy(status = TaskStatus.PLANNED, demoStop = null) else it }
        repo.transition(trip.copy(routeId = id, phase = VehiclePhase.READY, tripId = if (trip.phase == VehiclePhase.PARKED) trip.tripId + 1 else trip.tripId), tasks)
        reconcile()
    }
    fun event(event: VehicleEvent) = perform {
        val trip = repo.currentTrip()
        val next = vehicle.transition(trip, event)
        if (trip == next) return@perform
        val tasks = repo.currentTasks().map { task ->
            if (event == VehicleEvent.PARK && task.status != TaskStatus.COMPLETED && (task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.RECOMMENDED)) task.copy(status = TaskStatus.MISSED, demoStop = null) else task
        }
        repo.transition(next, tasks)
        reconcile()
        val notice = when (event) {
            VehicleEvent.START -> "주행을 시작했어요. 할 일 확인은 정차 후에 해 주세요."
            VehicleEvent.ARRIVE -> "데모 목적지에 도착했어요. 경유지에서 처리한 일은 직접 완료를 확인해 주세요."
            VehicleEvent.PARK -> "주차 완료. 챙길 일을 확인해 주세요. 미완료 업무는 다음 주행에 다시 추천해요."
            VehicleEvent.PREPARE -> "다음 이동을 준비해요. 미룬 일을 다시 추천할게요. 출발 전에 확인해 주세요."
        }
        _messages.emit(notice)
    }
}
