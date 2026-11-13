package com.routemate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routemate.app.core.*
import com.routemate.app.ui.*
import kotlinx.coroutines.flow.collect

/*
 * RouteMate v1 UI-only redesign.
 * This is intentionally self-contained: replace ONLY MainActivity.kt.
 * Existing ViewModel, Room entities, parser and the three other tabs remain untouched.
 * No GPS, actual navigation or driving-speed readings are implied.
 */
class MainActivity : ComponentActivity() {
    private lateinit var model: RouteMateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[RouteMateViewModel::class.java]
        setContent {
            RouteMateTheme { RouteMateV2Shell(model) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.refreshDate()
    }
}

private val V2Navy = Color(0xFF14263D)
private val V2Blue = Color(0xFF3478F6)
private val V2Muted = Color(0xFF64748B)
private val V2Bg = Color(0xFFF6F9FE)
private val V2Green = Color(0xFF22A06B)
private val V2Orange = Color(0xFFF59E0B)

@Composable
private fun RouteMateV2Shell(model: RouteMateViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable(stateSaver = NullableTaskSaver) { mutableStateOf<TaskItem?>(null) }
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    val savedScreens = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.messages.collect { snackbarHost.showSnackbar(it) } }
    BackHandler(enabled = tab != 0 && !state.driving) { tab = 0 }

    val labels = listOf("홈", "할 일 등록", "이동 추천", "기록")
    val icons = listOf(Icons.Default.Home, Icons.Default.AddCircleOutline, Icons.Default.AltRoute, Icons.Default.History)
    Scaffold(
        containerColor = V2Bg,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        enabled = !state.driving || index == 0 || index == 2,
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(label, maxLines = 1, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = V2Navy,
                            selectedTextColor = V2Navy,
                            indicatorColor = V2Blue.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.busy && state.tasks.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.driving) {
                // Keep the original driving safety screen unchanged.
                DrivingScreen(state, model)
            } else {
                savedScreens.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> RouteMateV2Home(
                            state = state, model = model,
                            onAdd = { tab = 1 }, onRoute = { tab = 2 },
                            onEdit = { editing = it }, onDelete = { deleting = it }
                        )
                        1 -> AddScreen(state, model, onSaved = { tab = 0 })
                        2 -> RouteScreen(state, model, onEdit = { editing = it }, onDelete = { deleting = it })
                        3 -> HistoryScreen(state, model, onEdit = { editing = it }, onDelete = { deleting = it })
                    }
                }
            }
        }
    }
    editing?.let { original ->
        EditTaskDialog(
            original, state.busy,
            onDismiss = { editing = null },
            onSave = { task -> model.saveDrafts(listOf(task)) { editing = null } }
        )
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("할 일을 삭제할까요?") },
            text = { Text("${task.title} 및 완료 기록이 삭제돼요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { model.delete(task); deleting = null }, enabled = !state.busy) {
                    Text("삭제")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun RouteMateV2Home(
    state: RouteMateUiState,
    model: RouteMateViewModel,
    onAdd: () -> Unit,
    onRoute: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit
) {
    val tasks = state.active
    val recs = state.recommendations
    val accepted = state.tasks.filter { it.status == TaskStatus.IN_PROGRESS }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { V2Header() }
        item {
            V2TasksCard(
                modifier = Modifier.padding(horizontal = 18.dp),
                tasks = tasks.take(4),
                recommendations = recs,
                completed = state.todayCompleted,
                total = tasks.size,
                enabled = !state.busy,
                onComplete = model::toggleComplete,
                onOpenAll = onRoute,
                onEdit = onEdit
            )
        }
        item {
            V2RouteCard(
                modifier = Modifier.padding(horizontal = 18.dp),
                state = state,
                accepted = accepted,
                onRoute = onRoute
            )
        }
        item {
            V2VehicleCard(
                modifier = Modifier.padding(horizontal = 18.dp),
                state = state,
                model = model
            )
        }
        if (recs.isNotEmpty() && state.trip.phase == VehiclePhase.READY) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 21.dp, end = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("지금 처리하기 좋은 일", color = V2Navy, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRoute) { Text("모두 보기") }
                }
            }
            items(recs.take(2), key = { "v2-rec-${it.task.id}" }) { rec ->
                V2Suggestion(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    rec = rec,
                    enabled = !state.busy,
                    onApprove = { model.approve(rec) },
                    onSnooze = { model.snooze(rec.task) }
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.busy
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("새 할 일 등록")
            }
        }
        item {
            Text(
                "샘플 경로·추천 시간은 데모 정보입니다. 실제 위치나 교통 정보는 사용하지 않습니다.",
                color = V2Muted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 23.dp)
            )
        }
    }
}

@Composable
private fun V2Header() {
    Box(modifier = Modifier.fillMaxWidth().height(174.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(V2Bg)
            val w = size.width
            val h = size.height
            val back = Path().apply {
                moveTo(0f, h * .43f)
                cubicTo(w * .23f, h * .14f, w * .44f, h * .74f, w * .69f, h * .34f)
                cubicTo(w * .82f, h * .19f, w * .94f, h * .28f, w, h * .40f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(back, Color(0xFFE8F1FF))
            val mid = Path().apply {
                moveTo(0f, h * .69f)
                cubicTo(w * .25f, h * .34f, w * .48f, h * .89f, w * .76f, h * .55f)
                cubicTo(w * .87f, h * .40f, w * .97f, h * .50f, w, h * .48f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(mid, Color(0xFFDDEBFF).copy(alpha = .68f))
            val front = Path().apply {
                moveTo(0f, h * .92f)
                cubicTo(w * .19f, h * .70f, w * .45f, h * 1.06f, w * .67f, h * .76f)
                cubicTo(w * .83f, h * .58f, w * .92f, h * .74f, w, h * .66f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(front, Color.White.copy(alpha = .78f))
        }
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.routemate_logo),
                        contentDescription = "RouteMate 로고",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(60.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Row {
                        Text("Route", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = V2Navy)
                        Text("Mate", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = V2Blue)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = .90f), shape = CircleShape, shadowElevation = 2.dp) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "알림 표시", tint = V2Navy)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(color = Color.White.copy(alpha = .85f), shape = CircleShape) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DirectionsCar, null, tint = V2Blue, modifier = Modifier.size(25.dp))
                    }
                }
                Column {
                    Text("좋은 하루예요!", color = V2Navy, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("오늘도 스마트하게 이동해 볼까요?", color = Color(0xFF536783), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun V2Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)),
        color = Color.White,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun V2TasksCard(
    modifier: Modifier,
    tasks: List<TaskItem>,
    recommendations: List<Recommendation>,
    completed: Int,
    total: Int,
    enabled: Boolean,
    onComplete: (TaskItem) -> Unit,
    onOpenAll: () -> Unit,
    onEdit: (TaskItem) -> Unit
) {
    V2Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = V2Green)
            Spacer(Modifier.width(8.dp))
            Text("오늘의 할 일", color = V2Navy, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenAll, contentPadding = PaddingValues(0.dp)) { Text("추천 보기", fontSize = 12.sp) }
        }
        if (tasks.isEmpty()) {
            Text("남아 있는 할 일이 없어요. 새로운 할 일을 등록해 보세요.", color = V2Muted, fontSize = 13.sp)
        } else {
            tasks.forEachIndexed { index, task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.status == TaskStatus.COMPLETED,
                        onCheckedChange = { onComplete(task) }, enabled = enabled,
                        modifier = Modifier.size(39.dp),
                        colors = CheckboxDefaults.colors(checkedColor = V2Green)
                    )
                    Icon(V2TaskIcon(task.category), contentDescription = null, tint = V2Muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f).clickable(enabled = enabled) { onEdit(task) },
                        fontWeight = FontWeight.Medium, fontSize = 14.sp, color = V2Navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    val onRoute = recommendations.any { it.task.id == task.id } || task.status == TaskStatus.IN_PROGRESS
                    val tag = when {
                        task.status == TaskStatus.IN_PROGRESS -> "경유 선택"
                        onRoute -> "경로상"
                        task.dueDate?.isBefore(java.time.LocalDate.now()) == true -> "마감 지남"
                        else -> task.status.label
                    }
                    V2Tag(tag, if (onRoute) V2Green else V2Orange, if (onRoute) Color(0xFFE8F7EE) else Color(0xFFFFF3E0))
                }
                if (index != tasks.lastIndex) HorizontalDivider(color = Color(0xFFF0F3F8))
            }
        }
        HorizontalDivider(color = Color(0xFFF0F3F8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("남은 할 일 ${total}개", fontSize = 12.sp, color = V2Muted)
            Text("오늘 완료 ${completed}개", fontSize = 12.sp, color = V2Green)
        }
    }
}

@Composable
private fun V2Tag(text: String, color: Color, bg: Color) {
    Surface(color = bg, shape = CircleShape) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun V2TaskIcon(category: Category): ImageVector = when (category) {
    Category.SHOPPING -> Icons.Default.ShoppingCart
    Category.LAUNDRY -> Icons.Default.Checkroom
    Category.PARCEL -> Icons.Default.LocalShipping
    Category.APPOINTMENT -> Icons.Default.Event
    else -> Icons.Default.Checklist
}

@Composable
private fun V2RouteCard(
    modifier: Modifier,
    state: RouteMateUiState,
    accepted: List<TaskItem>,
    onRoute: () -> Unit
) {
    val stops = accepted.map { it.title } + state.recommendations.map { it.task.title }
    val shown = stops.distinct().take(2)
    Surface(
        modifier = modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)),
        color = Color(0xFFEAF2FF), shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, null, tint = V2Blue)
                Spacer(Modifier.width(8.dp))
                Text("오늘의 추천 경로", style = MaterialTheme.typography.titleMedium, color = V2Navy)
                Spacer(Modifier.weight(1f))
                V2Tag("데모 추천", V2Blue, Color.White)
            }
            Text(
                if (shown.isEmpty()) "현재 경로에 새로운 추천이 없어요. 다른 경로를 확인해 보세요."
                else "현재 샘플 경로에서 처리할 일 ${shown.size}건을 확인해 보세요.",
                fontSize = 12.sp, color = Color(0xFF536783)
            )
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    V2RouteStop(Icons.Default.Home, state.route.origin, Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = V2Muted, modifier = Modifier.padding(top = 2.dp).size(17.dp))
                    V2RouteStop(Icons.Default.ShoppingBag, shown.getOrNull(0) ?: "경유 미정", Modifier.weight(1.2f))
                    Icon(Icons.Default.ChevronRight, null, tint = V2Muted, modifier = Modifier.padding(top = 2.dp).size(17.dp))
                    V2RouteStop(Icons.Default.Flag, state.route.destination, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("기본 이동 ${state.route.minutes}분 (샘플)", color = V2Muted, fontSize = 11.sp)
                Text("추천 업무 ${state.recommendations.size}건", color = V2Navy, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
            Button(
                onClick = onRoute,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(51.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V2Blue)
            ) {
                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("추천 경유지 확인", fontWeight = FontWeight.SemiBold)
            }
            Text("경로 선은 안내용 데모이며 실제 내비게이션을 변경하지 않아요.", fontSize = 10.sp, color = V2Muted)
        }
    }
}

@Composable
private fun V2RouteStop(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = V2Blue, modifier = Modifier.size(20.dp))
        Text(text, color = V2Navy, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V2VehicleCard(modifier: Modifier, state: RouteMateUiState, model: RouteMateViewModel) {
    V2Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, null, tint = V2Navy)
            Spacer(Modifier.width(8.dp))
            Text("주행 상태", color = V2Navy, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            val active = state.trip.phase == VehiclePhase.DRIVING
            V2Tag(state.trip.phase.label + " · 데모", if (active) V2Green else V2Blue, if (active) Color(0xFFE7F7EB) else Color(0xFFE8F0FF))
        }
        HorizontalDivider(color = Color(0xFFF0F3F8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("선택한 경로", fontSize = 11.sp, color = V2Muted)
                Text("${state.route.origin} → ${state.route.destination}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = V2Navy)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("기본 예상시간", fontSize = 11.sp, color = V2Muted)
                Text("${state.route.minutes}분", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = V2Navy)
            }
        }
        val phase = state.trip.phase
        val hint = when (phase) {
            VehiclePhase.READY -> "출발 전 추천을 확인해 보세요."
            VehiclePhase.DRIVING -> "데모 주행 중입니다. 조작은 도착 후에 해 주세요."
            VehiclePhase.ARRIVED -> "도착했어요. 처리한 업무는 직접 완료해 주세요."
            VehiclePhase.PARKED -> "미완료 업무는 다음 이동에 다시 추천됩니다."
        }
        Text(hint, color = V2Muted, fontSize = 12.sp)
        val event = when (phase) {
            VehiclePhase.READY -> VehicleEvent.START
            VehiclePhase.DRIVING -> VehicleEvent.ARRIVE
            VehiclePhase.ARRIVED -> VehicleEvent.PARK
            VehiclePhase.PARKED -> VehicleEvent.PREPARE
        }
        val label = when (phase) {
            VehiclePhase.READY -> "주행 시작 (데모)"
            VehiclePhase.DRIVING -> "목적지 도착 (데모)"
            VehiclePhase.ARRIVED -> "주차 완료 (데모)"
            VehiclePhase.PARKED -> "다음 이동 준비 (데모)"
        }
        Button(
            onClick = { model.event(event) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(51.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = V2Blue)
        ) { Text(label, fontWeight = FontWeight.SemiBold) }
        Text("실제 속도·주차·위치 정보와 연결되지 않은 버튼 시뮬레이션입니다.", fontSize = 10.sp, color = V2Muted)
    }
}

@Composable
private fun V2Suggestion(
    modifier: Modifier,
    rec: Recommendation,
    enabled: Boolean,
    onApprove: () -> Unit,
    onSnooze: () -> Unit
) {
    V2Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V2Tag("실행 기회", V2Blue, Color(0xFFE8F0FF))
            Spacer(Modifier.weight(1f))
            Text("추가 +${rec.place.extraMinutes}분 (샘플)", color = V2Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(rec.task.title, style = MaterialTheme.typography.titleMedium, color = V2Navy)
        Text("${rec.place.name} · ${rec.reason}", fontSize = 12.sp, color = V2Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 49.dp), shape = RoundedCornerShape(12.dp)) {
                Text("경유지 추가", fontSize = 13.sp)
            }
            OutlinedButton(onClick = onSnooze, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 49.dp), shape = RoundedCornerShape(12.dp)) {
                Text("나중에 하기", fontSize = 13.sp)
            }
        }
    }
}
