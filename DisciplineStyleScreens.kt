package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.DiaryEntryEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.repository.AzxRepository
import com.example.domain.model.Direction
import com.example.ui.AzxUiState
import com.example.ui.theme.AlmostBlack
import com.example.ui.theme.AzxBadge
import com.example.ui.theme.AzxCard
import com.example.ui.theme.AzxChip
import com.example.ui.theme.AzxPrimaryButton
import com.example.ui.theme.AzxProgressBar
import com.example.ui.theme.AzxSecondaryButton
import com.example.ui.theme.AzxSectionHeader
import com.example.ui.theme.DarkGray
import com.example.ui.theme.LightGray
import com.example.ui.theme.MidGray
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

@Composable
fun DisciplineScreen(
  state: AzxUiState,
  onSaveDiary: (DiaryEntryEntity) -> Unit,
  onSaveTask: (TaskEntity) -> Unit,
  onToggleTask: (TaskEntity) -> Unit,
  onSaveHabit: (HabitEntity) -> Unit,
  onToggleHabitToday: (HabitEntity) -> Unit,
  onBack: () -> Unit,
) {
  val today = state.todayEpoch
  val todayEntry = remember(state.diaryEntries, today) { state.diaryEntries.firstOrNull { it.dateEpochDay == today } }
  var sleepHours by remember(todayEntry) { mutableFloatStateOf(todayEntry?.sleepHours ?: 8f) }
  var screenTimeMin by remember(todayEntry) { mutableIntStateOf(todayEntry?.screenTimeMinutes ?: 90) }
  var showAddTask by remember { mutableStateOf(false) }

  val discTasks = remember(state.tasks) { state.tasks.filter { it.direction == "DISCIPLINE" } }
  val discHabits = remember(state.habits) { state.habits.filter { it.direction == "DISCIPLINE" } }
  val doneHabitsToday =
    remember(state.habitHistories, today) {
      state.habitHistories.filter { it.dateEpochDay == today && it.completed }.map { it.habitId }.toSet()
    }

  Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = PureWhite)
      }
      Column {
        Text("DISCIPLINE — РЕЖИМ И КОНТРОЛЬ", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
        Text("Сон • Порядок • Уборка • Экранное время", style = MaterialTheme.typography.bodySmall, color = MidGray)
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Sleep & Screen-time reduction card
      item {
        AzxCard(containerColor = AlmostBlack) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = PureWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Сон и цифровой детокс сегодня", style = MaterialTheme.typography.titleMedium, color = PureWhite)
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text("Сон: ${"%.1f".format(sleepHours)} ч (рекомендовано 8–9 ч)", style = MaterialTheme.typography.bodySmall, color = LightGray)
          Slider(
            value = sleepHours,
            onValueChange = { sleepHours = it },
            valueRange = 5f..11f,
            colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = DarkGray),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhonelinkLock, contentDescription = null, tint = LightGray)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              "Экранное время: $screenTimeMin мин / цель ≤ ${state.settings.screenTimeLimitMinutes} мин",
              style = MaterialTheme.typography.bodySmall,
              color = LightGray,
            )
          }
          Slider(
            value = screenTimeMin.toFloat(),
            onValueChange = { screenTimeMin = it.toInt() },
            valueRange = 15f..360f,
            colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = DarkGray),
          )
          AzxPrimaryButton(
            text = "Сохранить показатели режима",
            onClick = {
              onSaveDiary(
                (todayEntry ?: DiaryEntryEntity(dateEpochDay = today)).copy(
                  sleepHours = sleepHours,
                  screenTimeMinutes = screenTimeMin,
                ),
              )
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      // Quick Discipline templates
      item {
        AzxSectionHeader(
          title = "Быстрый режим дня",
          actionLabel = "+ Задача",
          onAction = { showAddTask = true },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AzxSecondaryButton(
            text = "+ Уборка комнаты",
            onClick = {
              onSaveTask(
                TaskEntity(
                  title = "Уборка рабочего стола и комнаты (15 мин)",
                  direction = "DISCIPLINE",
                  dateEpochDay = today,
                ),
              )
            },
            modifier = Modifier.weight(1f),
          )
          AzxSecondaryButton(
            text = "+ Без телефона 1ч",
            onClick = {
              onSaveHabit(
                HabitEntity(
                  title = "Вечерний час без экрана перед сном",
                  direction = "DISCIPLINE",
                  targetPerWeek = 7,
                ),
              )
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      if (discHabits.isNotEmpty()) {
        item { Text("Привычки дисциплины", style = MaterialTheme.typography.titleMedium, color = PureWhite) }
        items(discHabits, key = { it.id }) { h ->
          val checked = h.id in doneHabitsToday
          AzxCard(onClick = { onToggleHabitToday(h) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = checked,
                onCheckedChange = { onToggleHabitToday(h) },
                colors = CheckboxDefaults.colors(checkedColor = PureWhite, checkmarkColor = PureBlack),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.titleSmall, color = PureWhite)
                Text("Серия: ${h.currentStreak} дн.", style = MaterialTheme.typography.bodySmall, color = MidGray)
              }
            }
          }
        }
      }

      item {
        AzxSectionHeader(title = "План и порядок (${discTasks.size})")
      }
      items(discTasks, key = { it.id }) { t ->
        AzxCard(onClick = { onToggleTask(t) }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = t.status == "DONE",
              onCheckedChange = { onToggleTask(t) },
              colors = CheckboxDefaults.colors(checkedColor = PureWhite, checkmarkColor = PureBlack),
            )
            Text(t.title, style = MaterialTheme.typography.bodyMedium, color = PureWhite, modifier = Modifier.weight(1f))
            AzxBadge(text = if (t.status == "DONE") "ОК" else "ПЛАН")
          }
        }
      }
    }
  }

  if (showAddTask) {
    TaskEditorDialog(
      defaultDirection = Direction.DISCIPLINE,
      goals = state.goals,
      onDismiss = { showAddTask = false },
      onSave = onSaveTask,
    )
  }
}

@Composable
fun StyleScreen(
  state: AzxUiState,
  onSaveTask: (TaskEntity) -> Unit,
  onToggleTask: (TaskEntity) -> Unit,
  onAddReminder: (ReminderEntity) -> Unit,
  onBack: () -> Unit,
) {
  var customChecklistItem by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Гигиена") }

  val styleTasks = remember(state.tasks) { state.tasks.filter { it.direction == "STYLE" } }
  val doneCount = styleTasks.count { it.status == "DONE" }
  val progress = if (styleTasks.isEmpty()) 0f else (doneCount.toFloat() / styleTasks.size).coerceIn(0f, 1f)

  val quickTemplates =
    listOf(
      "Гигиена" to "Утренний и вечерний душ и чистка зубов",
      "Уход за кожей" to "Мягкое умывание и увлажняющий крем по типу кожи",
      "Уход за волосами" to "Чистые волосы и аккуратная укладка",
      "Опрятность" to "Подготовить чистую, выглаженную одежду на завтра",
    )

  Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = PureWhite)
      }
      Column {
        Text("STYLE — УХОД И ОПРЯТНОСТЬ", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
        Text("Здоровая рутина без навязанных стандартов", style = MaterialTheme.typography.bodySmall, color = MidGray)
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        AzxCard(containerColor = AlmostBlack) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = PureWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Чек-лист заботы о себе", style = MaterialTheme.typography.titleMedium, color = PureWhite)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "Уход за собой — это комфорт, здоровье кожи и волос, чистота и уверенность в повседневных делах.",
            style = MaterialTheme.typography.bodySmall,
            color = LightGray,
          )
          Spacer(modifier = Modifier.height(8.dp))
          AzxProgressBar(progress = progress)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Выполнено: $doneCount из ${styleTasks.size}", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
      }

      item {
        Text("Добавить базовый пункт или свой", style = MaterialTheme.typography.titleSmall, color = LightGray)
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          quickTemplates.forEach { (cat, title) ->
            AzxSecondaryButton(
              text = "+ $cat: $title",
              onClick = {
                onSaveTask(
                  TaskEntity(
                    title = title,
                    description = cat,
                    direction = "STYLE",
                    dateEpochDay = state.todayEpoch,
                    repeatRule = "DAILY",
                  ),
                )
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      item {
        AzxCard {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Гигиена", "Кожа", "Волосы", "Стиль").forEach { c ->
              AzxChip(text = c, selected = category == c, onClick = { category = c })
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = customChecklistItem,
            onValueChange = { customChecklistItem = it },
            label = { Text("Свой пункт ухода") },
            colors = azxTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzxPrimaryButton(
              text = "Добавить пункт",
              icon = Icons.Rounded.Add,
              enabled = customChecklistItem.isNotBlank(),
              onClick = {
                onSaveTask(
                  TaskEntity(
                    title = customChecklistItem.trim(),
                    description = category,
                    direction = "STYLE",
                    dateEpochDay = state.todayEpoch,
                  ),
                )
                customChecklistItem = ""
              },
              modifier = Modifier.weight(1f),
            )
            AzxSecondaryButton(
              text = "Напомнить в 21:00",
              icon = Icons.Rounded.NotificationsActive,
              onClick = {
                onAddReminder(
                  ReminderEntity(
                    title = "STYLE: Вечерний уход",
                    targetType = "DAILY_PLAN",
                    hour = 21,
                    minute = 0,
                  ),
                )
              },
            )
          }
        }
      }

      items(styleTasks, key = { it.id }) { t ->
        AzxCard(onClick = { onToggleTask(t) }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = t.status == "DONE",
              onCheckedChange = { onToggleTask(t) },
              colors = CheckboxDefaults.colors(checkedColor = PureWhite, checkmarkColor = PureBlack),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(t.title, style = MaterialTheme.typography.titleSmall, color = PureWhite)
              Text(
                "${t.description.ifBlank { "STYLE" }} • ${AzxRepository.formatEpochDay(t.dateEpochDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = MidGray,
              )
            }
          }
        }
      }
    }
  }
}
