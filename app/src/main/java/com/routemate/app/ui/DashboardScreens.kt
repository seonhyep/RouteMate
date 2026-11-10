package com.routemate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routemate.app.*
import com.routemate.app.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable fun AppHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Navy)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun StatTile(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineLarge, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}
@Composable fun VehiclePanel(state: RouteMateUiState, model: RouteMateViewModel) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.DirectionsCar, null, tint = Navy); Text(state.trip.phase.label, style = MaterialTheme.typography.titleMedium) }
            LabelPill("차량 상태 · 데모")
        }
        Text(state.route.origin + " → " + state.route.destination + " · " + state.route.minutes + "분 (샘플)", color = Muted)
        val message = when (state.trip.phase) {
            VehiclePhase.READY -> "오늘 남은 할 일 " + state.todayTasks.size + "개, 이 데모 경로에서 추천 가능한 일 " + state.recommendations.size + "개예요."
            VehiclePhase.DRIVING -> "안전한 주행에 집중하세요. 복잡한 조작은 정차 후에 해 주세요."
            VehiclePhase.ARRIVED -> {
                val stops = state.tasks.filter { it.status == TaskStatus.IN_PROGRESS }.joinToString { it.demoStop ?: "가상 경유지" }
                if (stops.isNotBlank()) "데모 목적지에 도착했어요. 경유지(" + stops + ")에서 처리한 일의 완료 여부를 확인해 주세요." else "목적지 도착. 할 일은 자동 완료되지 않아요. 직접 확인해 주세요."
            }
            VehiclePhase.PARKED -> "차에서 내리기 전에 챙길 일을 확인하세요. 미완료 업무는 다음 주행에서 다시 추천해요."
        }
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text("버튼으로 가상 차량 상태를 바꿉니다. 실제 주행·위치는 감지하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Button(onClick = { model.event(if (state.trip.phase == VehiclePhase.PARKED) VehicleEvent.PREPARE else VehicleEvent.START) }, enabled = !state.busy && (state.trip.phase == VehiclePhase.READY || state.trip.phase == VehiclePhase.PARKED), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (state.trip.phase == VehiclePhase.PARKED) "다음 이동 준비 (데모)" else "주행 시작 (데모)") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.event(VehicleEvent.ARRIVE) }, enabled = !state.busy && state.trip.phase == VehiclePhase.DRIVING, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text("목적지 도착") }
            OutlinedButton(onClick = { model.event(VehicleEvent.PARK) }, enabled = !state.busy && state.trip.phase == VehiclePhase.ARRIVED, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text("주차 완료") }
        }
    }
}
@Composable fun DrivingScreen(state: RouteMateUiState, model: RouteMateViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { AppHeading("주행에 집중하세요", "실제 운전 중에는 화면을 조작하지 마세요.") }
        item {
            Surface(color = Navy, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.DirectionsCar, null, Modifier.size(56.dp), tint = Color.White)
                    Text("목적지 · " + state.route.destination, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text("데모 주행 중 · " + state.tasks.count { it.status == TaskStatus.IN_PROGRESS } + "개 경유지", color = Color.White.copy(alpha = .8f))
                    Text("등록·수정·완료는 도착 후에", color = Color.White)
                }
            }
        }
        item { VehiclePanel(state, model) }
    }
}
@Composable fun HomeScreen(state: RouteMateUiState, model: RouteMateViewModel, onAdd: () -> Unit, onRoute: () -> Unit, onEdit: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Navy, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AltRoute, null, Modifier.padding(10.dp).size(26.dp), tint = Color.White) }
                Text("RouteMate", color = Navy, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f)); LabelPill("MVP · 데모")
            }
        }
        item { AppHeading("오늘의 이동을, 더 가볍게", state.today.format(KoreanDate) + " · 잊고 있던 일도 함께 챙겨요.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatTile(state.todayTasks.size, "오늘 남은 일", Navy, Modifier.weight(1f)); StatTile(state.todayCompleted, "오늘 완료", Green, Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatTile(state.urgent, "마감 임박", Orange, Modifier.weight(1f)); StatTile(state.recommendations.size, "경로 내 추천", Blue, Modifier.weight(1f)) }
            }
        }
        item { VehiclePanel(state, model) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("이동 기회 추천", "규칙 기반 · 샘플 경로")
                TextButton(onClick = onRoute) { Text("모두 보기") }
            }
        }
        val top = state.recommendations.firstOrNull()
        if (top != null) item { RecommendationCard(top, !state.busy, { model.approve(top) }, { model.snooze(top.task) }) }
        else item { EmptyPanel("지금은 새 추천이 없어요", "미룬 일은 다음 주행에 다시 확인해요. 직접 할 일을 추가할 수도 있어요.") }
        item { SectionTitle("오늘의 할 일", "마감이 없거나 오늘까지인 미완료 업무 · 지난 마감 포함") }
        if (state.todayTasks.isEmpty()) item { EmptyPanel("오늘 할 일을 모두 챙겼어요", "새 할 일을 등록하거나 기록에서 전체 목록을 확인하세요.") }
        items(state.todayTasks, key = { it.id }) { task -> TaskCard(task, !state.busy, { model.toggleComplete(task) }, { onEdit(task) }, { onDelete(task) }) }
        item { Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("할 일 추가") } }
    }
}
@Composable fun RouteScreen(state: RouteMateUiState, model: RouteMateViewModel, onEdit: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeading("이동 추천", "목적지에 가는 길, 함께 처리해요.") }
        item {
            Panel {
                LabelPill("샘플 경로 · 실제 GPS 미사용", Orange)
                ChoiceField("목적지", state.route, state.routes, { it.origin + " → " + it.destination }, { if (!state.busy) model.selectRoute(it.id) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.MyLocation, null, tint = Blue)
                    Column { Text("현재 위치: " + state.route.origin + " (가상)"); Text("목적지: " + state.route.destination + " (가상)", color = Muted) }
                }
                Text("기본 " + state.route.minutes + "분 · 경유 시간은 고정 샘플 값입니다. 영업시간·실시간 교통·실제 거리는 확인하지 않습니다.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { VehiclePanel(state, model) }
        val accepted = state.tasks.filter { it.status == TaskStatus.IN_PROGRESS }
        if (accepted.isNotEmpty()) {
            item { SectionTitle("추가한 데모 경유지", "도착 후 직접 완료를 확인해 주세요.") }
            items(accepted, key = { "stop-" + it.id }) { task -> TaskCard(task, !state.busy, { model.toggleComplete(task) }, { onEdit(task) }, { onDelete(task) }) }
        }
        item { SectionTitle("실행 가능한 일", "마감 7일 이내 또는 날짜 없는 일 · 카테고리 일치 기준") }
        if (state.recommendations.isEmpty()) item { EmptyPanel("현재 추천이 없어요", "다른 데모 경로를 선택하거나 다음 주행을 시작해 보세요.") }
        items(state.recommendations, key = { "rec-" + it.task.id }) { rec -> RecommendationCard(rec, !state.busy, { model.approve(rec) }, { model.snooze(rec.task) }) }
        item { Text("추천은 실제 방문 가능 여부를 보장하지 않습니다. 등록 장소와 다른 가상 장소가 제안될 수 있습니다.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}
private fun epochDate(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
@Composable fun HistoryScreen(state: RouteMateUiState, model: RouteMateViewModel, onEdit: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var categoryName by rememberSaveable { mutableStateOf("ALL") }
    var dateString by rememberSaveable { mutableStateOf("") }
    var dateBasis by rememberSaveable { mutableStateOf("등록일") }
    val selectedDate = dateString.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    val filtered = state.tasks.filter { task ->
        val matchesState = when(filter) { 1 -> task.status == TaskStatus.COMPLETED; 2 -> task.status != TaskStatus.COMPLETED; else -> true }
        val date = when(dateBasis) { "마감일" -> task.dueDate; "완료일" -> task.completedAt?.let(::epochDate); else -> epochDate(task.createdAt) }
        matchesState && (categoryName == "ALL" || task.category.name == categoryName) && (selectedDate == null || date == selectedDate)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeading("나의 기록", "완료한 일도, 다음에 챙길 일도 한곳에.") }
        item {
            Panel {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("전체", "완료", "미완료").forEachIndexed { index, label -> FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) }) }
                }
                Text("미완료: 예정·실행 추천·진행 중·미완료 상태 전체", color = Muted, style = MaterialTheme.typography.bodySmall)
                ChoiceField("카테고리", categoryName, listOf("ALL") + Category.entries.map { it.name }, { if (it == "ALL") "전체" else Category.valueOf(it).label }, { categoryName = it })
                ChoiceField("날짜 기준", dateBasis, listOf("등록일", "마감일", "완료일"), { it }, { dateBasis = it })
                DateField("날짜 필터", selectedDate, { dateString = it?.toString() ?: "" })
                Text(filtered.size.toString() + "개의 기록", color = Blue, fontWeight = FontWeight.SemiBold)
            }
        }
        if (filtered.isEmpty()) item { EmptyPanel("조건에 맞는 기록이 없어요", "카테고리나 날짜 필터를 변경해 보세요.") }
        items(filtered, key = { it.id }) { task ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskCard(task, !state.busy, { model.toggleComplete(task) }, { onEdit(task) }, { onDelete(task) })
                task.completedAt?.let { Text("완료일 · " + epochDate(it).format(KoreanDate), color = Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp)) }
            }
        }
    }
}
