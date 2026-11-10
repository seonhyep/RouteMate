package com.routemate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routemate.app.*
import com.routemate.app.core.*
import java.time.LocalDate
import org.json.JSONObject

// Save form fields through Android configuration changes. Room owns persisted tasks.
fun taskJson(t: TaskItem): String = JSONObject().apply {
    put("id", t.id); put("title", t.title); put("category", t.category.name); put("due", t.dueDate?.toString() ?: "")
    put("place", t.place); put("priority", t.priority.name); put("status", t.status.name); put("memo", t.memo)
    put("demoStop", t.demoStop ?: ""); put("created", t.createdAt); put("completed", t.completedAt ?: -1); put("snoozed", t.snoozedTrip); put("sample", t.isSample)
}.toString()
fun taskFromJson(value: String): TaskItem = JSONObject(value).let { j ->
    TaskItem(id = j.getLong("id"), title = j.getString("title"), category = Category.valueOf(j.getString("category")), dueDate = j.getString("due").takeIf { it.isNotBlank() }?.let(LocalDate::parse), place = j.getString("place"), priority = Priority.valueOf(j.getString("priority")), status = TaskStatus.valueOf(j.getString("status")), memo = j.getString("memo"), createdAt = j.getLong("created"), completedAt = j.getLong("completed").takeIf { it >= 0 }, snoozedTrip = j.getLong("snoozed"), isSample = j.getBoolean("sample"), demoStop = j.optString("demoStop").takeIf { it.isNotBlank() })
}
val TaskSaver = Saver<TaskItem, String>(save = { taskJson(it) }, restore = { taskFromJson(it) })

val NullableTaskSaver = Saver<TaskItem?, String>(save = { it?.let(::taskJson) ?: "" }, restore = { it.takeIf(String::isNotBlank)?.let(::taskFromJson) })

@Composable fun RouteMateApp(model: RouteMateViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable(stateSaver = NullableTaskSaver) { mutableStateOf<TaskItem?>(null) }
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = tab != 0 && !state.driving) { tab = 0 }
    val labels = listOf("홈", "할 일 등록", "이동 추천", "기록")
    val icons = listOf(Icons.Default.Home, Icons.Default.AddCircleOutline, Icons.Default.AltRoute, Icons.Default.History)
    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, enabled = !state.driving || index == 0 || index == 2,
                    icon = { Icon(icons[index], null) }, label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, selectedTextColor = Navy, indicatorColor = Blue.copy(alpha = .12f)))
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy && state.tasks.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.driving) {
                DrivingScreen(state, model)
            } else screenStates.SaveableStateProvider(tab) { when (tab) {
                0 -> HomeScreen(state, model, onAdd = { tab = 1 }, onRoute = { tab = 2 }, onEdit = { editing = it }, onDelete = { deleting = it })
                1 -> AddScreen(state, model, onSaved = { tab = 0 })
                2 -> RouteScreen(state, model, onEdit = { editing = it }, onDelete = { deleting = it })
                3 -> HistoryScreen(state, model, onEdit = { editing = it }, onDelete = { deleting = it })
            } }
        }
    }
    editing?.let { original ->
        EditTaskDialog(original, state.busy, onDismiss = { editing = null }, onSave = { task -> model.saveDrafts(listOf(task)) { editing = null } })
    }
    deleting?.let { task ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("할 일을 삭제할까요?") }, text = { Text(task.title + " 및 완료 기록이 삭제돼요. 되돌릴 수 없어요.") }, confirmButton = {
            TextButton(onClick = { model.delete(task); deleting = null }, enabled = !state.busy) { Text("삭제") }
        }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
}
@Composable fun EditTaskDialog(original: TaskItem, busy: Boolean, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit) {
    var task by rememberSaveable(original.id, stateSaver = TaskSaver) { mutableStateOf(original) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f).imePadding(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(Modifier.padding(20.dp)) {
                Text("할 일 수정", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TaskFields(task) { task = it }
                    Text("완료 여부는 목록의 체크 버튼에서 변경해 주세요.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") }
                    Button(onClick = { onSave(task) }, enabled = !busy && task.title.isNotBlank()) { Text("저장") }
                }
            }
        }
    }
}
