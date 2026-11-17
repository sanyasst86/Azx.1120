package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.SubtaskEntity
import com.example.data.repository.AzxRepository
import com.example.domain.model.Direction
import com.example.ui.AzxUiState
import com.example.ui.theme.AlmostBlack
import com.example.ui.theme.AzxBadge
import com.example.ui.theme.AzxCard
import com.example.ui.theme.AzxChip
import com.example.ui.theme.AzxEmptyState
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
fun GoalsAndDirectionsScreen(
  state: AzxUiState,
  onSaveGoal: (GoalEntity, List<String>) -> Unit,
  onDeleteGoal: (GoalEntity) -> Unit,
  onAddSubtask: (Long, String) -> Unit,
  onToggleSubtask: (SubtaskEntity) -> Unit,
  onDeleteSubtask: (SubtaskEntity) -> Unit,
  onUpdateGoalProgress: (GoalEntity, Int) -> Unit,
  onOpenDirection: (Direction) -> Unit,
) {
  var showGoalDialog by remember { mutableStateOf(false) }
  var editingGoal by remember { mutableStateOf<GoalEntity?>(null) }
  var directionFilter by remember { mutableStateOf<Direction?>(null) }
  var expandedGoalId by remember { mutableStateOf<Long?>(null) }
  var newSubtaskTitle by remember { mutableStateOf("") }

  val filteredGoals =
    remember(state.goals, directionFilter) {
      state.goals.filter { directionFilter == null || it.direction == directionFilter!!.code }
    }

  val completedGoalsCount = state.goals.count { it.isCompleted }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(PureBlack),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("ЦЕЛИ И НАПРАВЛЕНИЯ", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
          Text(
            "7 сфер развития • Достигнуто целей: $completedGoalsCount из ${state.goals.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MidGray,
          )
        }
        AzxPrimaryButton(
          text = "+ Цель",
          onClick = { showGoalDialog = true },
          modifier = Modifier.testTag("add_goal_btn"),
        )
      }
    }

    // 7 Directions Hub
    item {
      AzxSectionHeader(
        title = "Семь направлений",
        subtitle = "Нажми на направление, чтобы открыть его страницу и инструменты",
      )
      Spacer(modifier = Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Direction.entries.forEach { dir ->
          val dirTasks = state.tasks.filter { it.direction == dir.code }
          val dirDone = dirTasks.count { it.status == "DONE" }
          val dirGoals = state.goals.count { it.direction == dir.code }
          AzxCard(
            onClick = { onOpenDirection(dir) },
            containerColor = AlmostBlack,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(dir.icon, contentDescription = dir.title, tint = PureWhite, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    dir.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite,
                  )
                  Text(dir.subtitle, style = MaterialTheme.typography.bodySmall, color = MidGray)
                }
              }
              AzxBadge(text = "$dirDone зад • $dirGoals цел")
            }
          }
        }
      }
    }

    // Goals Filter
    item {
      Spacer(modifier = Modifier.height(4.dp))
      AzxSectionHeader(
        title = "Список целей",
        subtitle = "Подзадачи, приоритеты, дедлайны и история изменений",
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        AzxChip(text = "Все", selected = directionFilter == null, onClick = { directionFilter = null })
        Direction.entries.forEach { d ->
          AzxChip(text = d.title, selected = directionFilter == d, onClick = { directionFilter = d })
        }
      }
    }

    if (filteredGoals.isEmpty()) {
      item {
        AzxEmptyState(
          icon = Icons.Rounded.Flag,
          title = "Целей пока нет",
          description = "Создай цель с подзадачами и дедлайном — привязанные задачи из планов помогут её закрыть.",
          actionLabel = "Создать цель",
          onAction = { showGoalDialog = true },
        )
      }
    } else {
      items(filteredGoals, key = { it.id }) { goal ->
        val subs = state.subtasks.filter { it.goalId == goal.id }
        val linkedTasks = state.tasks.filter { it.goalId == goal.id }
        val expanded = expandedGoalId == goal.id
        AzxCard(
          onClick = { expandedGoalId = if (expanded) null else goal.id },
          borderColor = if (expanded) PureWhite else DarkGray,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                AzxBadge(text = goal.direction)
                Spacer(modifier = Modifier.width(6.dp))
                AzxBadge(
                  text = when (goal.priority) {
                    3 -> "ВЫСОКИЙ"
                    1 -> "НИЗКИЙ"
                    else -> "СРЕДНИЙ"
                  },
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(goal.title, style = MaterialTheme.typography.titleMedium, color = PureWhite)
              Text(
                text = "Дедлайн: ${AzxRepository.formatEpochDay(goal.deadlineEpochDay)} • Подзадач: ${subs.count { it.isDone }}/${subs.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MidGray,
              )
            }
            AzxBadge(text = "${goal.progressPercent}%", filled = goal.isCompleted)
          }
          Spacer(modifier = Modifier.height(8.dp))
          AzxProgressBar(progress = goal.progressPercent / 100f, heightDp = 7)

          if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            if (goal.description.isNotBlank()) {
              Text(goal.description, style = MaterialTheme.typography.bodySmall, color = LightGray)
              Spacer(modifier = Modifier.height(8.dp))
            }

            if (subs.isEmpty()) {
              Text(
                "Ручной прогресс: ${goal.progressPercent}%",
                style = MaterialTheme.typography.labelMedium,
                color = LightGray,
              )
              Slider(
                value = goal.progressPercent.toFloat(),
                onValueChange = { onUpdateGoalProgress(goal, it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = DarkGray),
              )
            } else {
              subs.forEach { sub ->
                Row(
                  modifier = Modifier.fillMaxWidth().clickable { onToggleSubtask(sub) },
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = sub.isDone,
                    onCheckedChange = { onToggleSubtask(sub) },
                    colors = CheckboxDefaults.colors(checkedColor = PureWhite, checkmarkColor = PureBlack),
                  )
                  Text(
                    text = sub.title,
                    style =
                      MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None,
                      ),
                    color = if (sub.isDone) MidGray else PureWhite,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(onClick = { onDeleteSubtask(sub) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Удалить подзадачу", tint = MidGray)
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = newSubtaskTitle,
                onValueChange = { newSubtaskTitle = it },
                label = { Text("Добавить подзадачу") },
                singleLine = true,
                colors = azxTextFieldColors(),
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(6.dp))
              AzxSecondaryButton(
                text = "+",
                enabled = newSubtaskTitle.isNotBlank(),
                onClick = {
                  onAddSubtask(goal.id, newSubtaskTitle)
                  newSubtaskTitle = ""
                },
              )
            }

            if (linkedTasks.isNotEmpty()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "Связанные задачи (${linkedTasks.count { it.status == "DONE" }}/${linkedTasks.size}): " +
                  linkedTasks.joinToString { it.title },
                style = MaterialTheme.typography.bodySmall,
                color = LightGray,
              )
            }

            if (goal.historyLog.isNotBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text("История изменений:", style = MaterialTheme.typography.labelSmall, color = MidGray)
              Text(goal.historyLog, style = MaterialTheme.typography.bodySmall, color = MidGray)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              IconButton(onClick = { editingGoal = goal }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Редактировать цель", tint = LightGray)
              }
              IconButton(onClick = { onDeleteGoal(goal) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Удалить цель", tint = MidGray)
              }
            }
          }
        }
      }
    }
  }

  if (showGoalDialog || editingGoal != null) {
    GoalEditorDialog(
      initialGoal = editingGoal,
      onDismiss = {
        showGoalDialog = false
        editingGoal = null
      },
      onSave = onSaveGoal,
    )
  }
}
