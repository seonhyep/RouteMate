package com.routemate.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.routemate.app.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val KoreanDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
fun categoryIcon(category: Category): ImageVector = when(category) {
    Category.SHOPPING -> Icons.Default.ShoppingCart
    Category.LAUNDRY -> Icons.Default.LocalLaundryService
    Category.PARCEL -> Icons.Default.LocalShipping
    Category.APPOINTMENT -> Icons.Default.Event
    Category.OTHER -> Icons.Default.Checklist
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun LabelPill(text: String, color: Color = Blue) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium) }
}
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Navy)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun EmptyPanel(title: String, body: String) {
    Panel { Icon(Icons.Default.CheckCircle, null, tint = Blue, modifier = Modifier.size(30.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = Muted) }
}
@Composable fun TaskCard(task: TaskItem, enabled: Boolean, onComplete: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onComplete, enabled = enabled) {
                Icon(if (task.status == TaskStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, if (task.status == TaskStatus.COMPLETED) "완료 취소: " + task.title else "완료: " + task.title, tint = if (task.status == TaskStatus.COMPLETED) Green else Blue, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(task.category.label + " · " + (task.dueDate?.format(KoreanDate) ?: "마감 없음"), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Icon(categoryIcon(task.category), null, tint = Blue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelPill(task.status.label, if (task.status == TaskStatus.COMPLETED) Green else if (task.status == TaskStatus.MISSED) Orange else Blue)
            if (task.isSample) LabelPill("데모", Muted)
            if (task.priority == Priority.HIGH) LabelPill("중요", Orange)
        }
        if (task.place.isNotBlank()) Text(task.place, color = Muted, style = MaterialTheme.typography.bodyMedium)
        task.demoStop?.let { Text("데모 경유지: " + it, color = Blue, style = MaterialTheme.typography.bodyMedium) }
        if (task.memo.isNotBlank()) Text(task.memo, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("수정") }
            TextButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("삭제") }
        }
    }
}
@Composable fun RecommendationCard(recommendation: Recommendation, enabled: Boolean, approve: () -> Unit, snooze: () -> Unit) {
    val task = recommendation.task
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LabelPill("데모 추천")
            Text("추가 +" + recommendation.place.extraMinutes + "분", color = Blue, style = MaterialTheme.typography.labelLarge)
        }
        Text(task.title, style = MaterialTheme.typography.titleLarge, color = Navy)
        Text(recommendation.place.name + " · " + (task.dueDate?.format(KoreanDate) ?: "마감 없음"), color = Muted)
        Text(recommendation.reason, style = MaterialTheme.typography.bodyMedium)
        LabelPill(task.status.label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = approve, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text("경유지 추가") }
            OutlinedButton(onClick = snooze, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text("나중에 하기") }
        }
    }
}
@Composable fun <T> ChoiceField(label: String, value: T, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) {
            Text(label + ": " + display(value), Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}
@Composable fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val initial = date ?: LocalDate.now()
            DatePickerDialog(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label + ": " + (date?.format(KoreanDate) ?: "선택 안 함"))
        }
        if (date != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Close, "날짜 지우기") }
    }
}
@Composable fun TaskFields(task: TaskItem, onChange: (TaskItem) -> Unit) {
    OutlinedTextField(task.title, { onChange(task.copy(title = it)) }, label = { Text("할 일 이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = task.title.isBlank())
    ChoiceField("카테고리", task.category, Category.entries, { it.label }, { onChange(task.copy(category = it)) })
    DateField("마감", task.dueDate, { onChange(task.copy(dueDate = it)) })
    OutlinedTextField(task.place, { onChange(task.copy(place = it)) }, label = { Text("관련 장소 (직접 입력)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    ChoiceField("중요도", task.priority, Priority.entries, { it.label }, { onChange(task.copy(priority = it)) })
    OutlinedTextField(task.memo, { onChange(task.copy(memo = it)) }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
}
