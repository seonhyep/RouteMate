package com.routemate.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.routemate.app.RouteMateUiState
import com.routemate.app.RouteMateViewModel
import com.routemate.app.core.TaskItem
import com.routemate.app.platform.AndroidSpeechIntentProvider

private val DraftSaver = listSaver<List<TaskItem>, String>(save = { items -> items.map(::taskJson) }, restore = { strings -> strings.map(::taskFromJson) })
@Composable fun AddScreen(state: RouteMateUiState, model: RouteMateViewModel, onSaved: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var drafts by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(emptyList<TaskItem>()) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val speech = remember { AndroidSpeechIntentProvider() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            input = if (input.isBlank()) text else input + " " + text
            drafts = emptyList()
            feedback = "음성을 입력했어요. 분석 결과를 확인한 뒤 저장해 주세요."
        } else {
            feedback = "음성 입력이 취소되었거나 사용할 수 없어요. 텍스트로 입력해 주세요."
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeading("할 일 등록", "생각났을 때 간단히 남겨 두세요.") }
        item {
            Panel {
                LabelPill("오프라인 · 규칙 기반 분석")
                Text("AI API 없이 날짜와 키워드로 분류해요. 분석 결과는 저장 전에 직접 수정할 수 있어요.", color = Muted)
                OutlinedTextField(input, { input = it; drafts = emptyList(); feedback = "" }, label = { Text("어떤 일을 해야 하나요?") }, placeholder = { Text("오늘 우유 사고, 이번 주 금요일까지 세탁소에서 옷 찾아야 해.") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    try { voice.launch(speech.createIntent()) }
                    catch (e: ActivityNotFoundException) { feedback = "기기에 음성 인식 앱이 없어요. 텍스트 입력을 이용해 주세요." }
                    catch (e: SecurityException) { feedback = "음성 인식 권한을 사용할 수 없어요. 텍스트로 입력해 주세요." }
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("음성으로 입력")
                }
                Text("기기 음성 인식기를 사용합니다. 인식기 설정에 따라 네트워크를 사용할 수 있어요. 운전 중에는 사용하지 마세요.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    drafts = model.analyze(input).map { parsed -> TaskItem(title = parsed.title, category = parsed.category, dueDate = parsed.dueDate, place = parsed.place, priority = parsed.priority, memo = parsed.memo) }
                    feedback = if (drafts.isEmpty()) "할 일을 텍스트로 입력해 주세요." else drafts.size.toString() + "개로 나눴어요. 날짜·장소를 확인해 주세요."
                }, enabled = !state.busy && input.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("문장 분석하기") }
                TextButton(onClick = { drafts = listOf(TaskItem(title = input.trim())); feedback = "직접 입력 모드예요. 아래 정보를 채워 주세요." }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("분석 없이 직접 입력") }
                if (feedback.isNotBlank()) Text(feedback, color = Blue)
            }
        }
        if (drafts.isNotEmpty()) {
            item { SectionTitle("저장 전 확인", "잘못 인식한 항목은 수정하거나 제외하세요.") }
            items(drafts.size) { index ->
                val task = drafts[index]
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("할 일 " + (index + 1), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { drafts = drafts.filterIndexed { i, _ -> i != index } }) { Text("제외") }
                    }
                    TaskFields(task) { updated -> drafts = drafts.toMutableList().also { it[index] = updated } }
                }
            }
            item {
                Button(onClick = { model.saveDrafts(drafts) { input = ""; drafts = emptyList(); feedback = ""; onSaved() } }, enabled = !state.busy && drafts.all { it.title.isNotBlank() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (state.busy) "저장 중…" else drafts.size.toString() + "개 할 일 저장")
                }
            }
        }
    }
}
