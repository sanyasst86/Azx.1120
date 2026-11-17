package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AzxApplication
import com.example.backup.BackupManager
import com.example.backup.BackupVerifyResult
import com.example.backup.LocalTransferManager
import com.example.backup.PdfReportGenerator
import com.example.backup.StatsReportData
import com.example.backup.TransferSessionInfo
import com.example.coach.CoachBlock
import com.example.coach.CoachHorizon
import com.example.coach.CoachPlanResult
import com.example.coach.CoachPlanner
import com.example.data.local.entity.AchievementEntity
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.DiaryEntryEntity
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.HabitHistoryEntity
import com.example.data.local.entity.MissionEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectStepEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.SubtaskEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.WorkoutEntity
import com.example.data.local.entity.WorkoutHistoryEntity
import com.example.data.local.entity.XPTransactionEntity
import com.example.data.repository.AzxRepository
import com.example.data.repository.FullBackupSnapshot
import com.example.domain.model.AnatomySide
import com.example.domain.model.Direction
import com.example.domain.model.LevelProgress
import com.example.domain.model.LevelSystem
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AzxUiState(
  val profile: ProfileEntity = ProfileEntity(),
  val settings: SettingsEntity = SettingsEntity(),
  val tasks: List<TaskEntity> = emptyList(),
  val goals: List<GoalEntity> = emptyList(),
  val subtasks: List<SubtaskEntity> = emptyList(),
  val habits: List<HabitEntity> = emptyList(),
  val habitHistories: List<HabitHistoryEntity> = emptyList(),
  val exercises: List<ExerciseEntity> = emptyList(),
  val workouts: List<WorkoutEntity> = emptyList(),
  val workoutHistory: List<WorkoutHistoryEntity> = emptyList(),
  val xpTransactions: List<XPTransactionEntity> = emptyList(),
  val totalXp: Int = 0,
  val levelProgress: LevelProgress = LevelSystem.calculate(0),
  val streakDays: Int = 0,
  val achievements: List<AchievementEntity> = emptyList(),
  val missions: List<MissionEntity> = emptyList(),
  val projects: List<ProjectEntity> = emptyList(),
  val projectSteps: List<ProjectStepEntity> = emptyList(),
  val diaryEntries: List<DiaryEntryEntity> = emptyList(),
  val calendarEvents: List<CalendarEventEntity> = emptyList(),
  val reminders: List<ReminderEntity> = emptyList(),
  val dayStartedEpoch: Long = 0L,
  val todayEpoch: Long = AzxRepository.todayEpochDay(),
)

sealed interface BackupDialogState {
  data object None : BackupDialogState
  data class ConfirmRestore(
    val preview: BackupVerifyResult.Valid,
    val sourceLabel: String,
  ) : BackupDialogState
  data class Error(val message: String) : BackupDialogState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val repo: AzxRepository = (application as AzxApplication).repository
  private val appContext: Context = application.applicationContext

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _backupDialog = MutableStateFlow<BackupDialogState>(BackupDialogState.None)
  val backupDialog: StateFlow<BackupDialogState> = _backupDialog.asStateFlow()

  private val _transferSession = MutableStateFlow<TransferSessionInfo?>(null)
  val transferSession: StateFlow<TransferSessionInfo?> = _transferSession.asStateFlow()

  private val _transferRunning = MutableStateFlow(false)
  val transferRunning: StateFlow<Boolean> = _transferRunning.asStateFlow()

  private val _coachHorizon = MutableStateFlow(CoachHorizon.DAY)
  val coachHorizon: StateFlow<CoachHorizon> = _coachHorizon.asStateFlow()

  private val _customCoachOverrides = MutableStateFlow<List<CoachBlock>?>(null)

  private val baseFlowA =
    combine(
      repo.profile,
      repo.settings,
      repo.tasks,
      repo.goals,
      repo.subtasks,
    ) { profile, settings, tasks, goals, subtasks ->
      Tuple5(profile, settings, tasks, goals, subtasks)
    }

  private val baseFlowB =
    combine(
      repo.habits,
      repo.habitHistory,
      repo.exercises,
      repo.workouts,
      repo.workoutHistory,
    ) { habits, habitHist, exercises, workouts, workoutHist ->
      Tuple5(habits, habitHist, exercises, workouts, workoutHist)
    }

  private val baseFlowC =
    combine(
      repo.xpTransactions,
      repo.totalXp,
      repo.achievements,
      repo.missions,
      repo.projects,
    ) { xpTx, totalXp, achs, missions, projects ->
      Tuple5(xpTx, totalXp, achs, missions, projects)
    }

  private val baseFlowD =
    combine(
      repo.projectSteps,
      repo.diaryEntries,
      repo.calendarEvents,
      repo.reminders,
      repo.prefs.dayStartedEpoch,
    ) { steps, diary, events, reminders, dayStarted ->
      Tuple5(steps, diary, events, reminders, dayStarted)
    }

  val uiState: StateFlow<AzxUiState> =
    combine(baseFlowA, baseFlowB, baseFlowC, baseFlowD) { a, b, c, d ->
      val streak = repo.computeOverallStreak(a.third, b.second, b.fifth, d.second)
      val totalXp = c.second
      AzxUiState(
        profile = a.first,
        settings = a.second,
        tasks = a.third,
        goals = a.fourth,
        subtasks = a.fifth,
        habits = b.first,
        habitHistories = b.second,
        exercises = b.third,
        workouts = b.fourth,
        workoutHistory = b.fifth,
        xpTransactions = c.first,
        totalXp = totalXp,
        levelProgress = LevelSystem.calculate(totalXp),
        streakDays = streak,
        achievements = c.third,
        missions = c.fourth,
        projects = c.fifth,
        projectSteps = d.first,
        diaryEntries = d.second,
        calendarEvents = d.third,
        reminders = d.fourth,
        dayStartedEpoch = d.fifth,
        todayEpoch = AzxRepository.todayEpochDay(),
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AzxUiState(),
    )

  val coachPlan: StateFlow<CoachPlanResult> =
    combine(uiState, _coachHorizon, _customCoachOverrides) { state, horizon, custom ->
      val generated =
        CoachPlanner.buildPlan(
          horizon = horizon,
          baseDay = state.todayEpoch,
          profile = state.profile,
          tasks = state.tasks,
          goals = state.goals,
          workouts = state.workouts,
          existingEvents = state.calendarEvents,
        )
      if (custom != null) generated.copy(blocks = custom) else generated
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue =
        CoachPlanResult(
          horizon = CoachHorizon.DAY,
          blocks = emptyList(),
          overdueCount = 0,
          freeMinutesEstimate = 0,
          recommendations = emptyList(),
        ),
    )

  init {
    viewModelScope.launch {
      repo.ensureInitialized()
      val settings = uiState.value.settings
      NotificationHelper.scheduleDailyCheckWorker(appContext, settings.notificationsEnabled)
    }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun showMessage(msg: String) {
    _statusBanner.value = msg
  }

  // --- Dashboard ---
  fun startDay() {
    viewModelScope.launch {
      repo.prefs.setDayStarted(AzxRepository.todayEpochDay())
      repo.ensureCurrentPeriodMissions()
      showMessage("День активирован. Фокус на главных задачах и здоровье.")
    }
  }

  // --- Tasks ---
  fun saveTask(task: TaskEntity) {
    viewModelScope.launch {
      val id = repo.saveTask(task)
      if (task.reminderEnabled && task.timeMinutes != null && uiState.value.settings.notificationsEnabled) {
        val h = task.timeMinutes / 60
        val m = task.timeMinutes % 60
        NotificationHelper.scheduleReminderAlarm(
          appContext,
          requestCode = (id % 100_000).toInt() + 2000,
          hour = h,
          minute = m,
          title = "Задача: ${task.title}",
          body = "Направление: ${task.direction}",
        )
      }
    }
  }

  fun toggleTaskDone(task: TaskEntity) {
    viewModelScope.launch { repo.toggleTaskDone(task) }
  }

  fun deleteTask(task: TaskEntity) {
    viewModelScope.launch { repo.deleteTask(task) }
  }

  // --- Goals & Subtasks ---
  fun saveGoal(goal: GoalEntity, initialSubtasks: List<String> = emptyList()) {
    viewModelScope.launch { repo.saveGoal(goal, initialSubtasks) }
  }

  fun deleteGoal(goal: GoalEntity) {
    viewModelScope.launch { repo.deleteGoal(goal) }
  }

  fun addSubtask(goalId: Long, title: String) {
    viewModelScope.launch { repo.addSubtask(goalId, title) }
  }

  fun toggleSubtask(subtask: SubtaskEntity) {
    viewModelScope.launch { repo.toggleSubtask(subtask) }
  }

  fun deleteSubtask(subtask: SubtaskEntity) {
    viewModelScope.launch { repo.deleteSubtask(subtask) }
  }

  fun updateGoalProgressManual(goal: GoalEntity, percent: Int) {
    viewModelScope.launch { repo.updateGoalProgressManual(goal, percent) }
  }

  // --- Habits & Diary ---
  fun saveHabit(habit: HabitEntity) {
    viewModelScope.launch { repo.saveHabit(habit) }
  }

  fun deleteHabit(habit: HabitEntity) {
    viewModelScope.launch { repo.deleteHabit(habit) }
  }

  fun toggleHabitForDay(habit: HabitEntity, epochDay: Long) {
    viewModelScope.launch { repo.toggleHabitForDay(habit, epochDay) }
  }

  fun saveDiaryEntry(entry: DiaryEntryEntity) {
    viewModelScope.launch {
      repo.saveDiaryEntry(entry)
      showMessage("Дневник сохранён.")
    }
  }

  // --- Workouts & Exercises ---
  fun saveWorkout(workout: WorkoutEntity) {
    viewModelScope.launch {
      repo.saveWorkout(workout)
      showMessage("Комплекс сохранён.")
    }
  }

  fun deleteWorkout(workout: WorkoutEntity) {
    viewModelScope.launch { repo.deleteWorkout(workout) }
  }

  fun completeWorkout(
    workoutId: Long,
    title: String,
    category: String,
    durationMinutes: Int,
    totalSetsCompleted: Int,
    targetMusclesCsv: String,
    notes: String = "",
  ) {
    viewModelScope.launch {
      repo.completeWorkout(
        workoutId = workoutId,
        title = title,
        category = category,
        durationMinutes = durationMinutes,
        totalSetsCompleted = totalSetsCompleted,
        targetMusclesCsv = targetMusclesCsv,
        notes = notes,
      )
      showMessage("Тренировка завершена! Данные и XP сохранены.")
    }
  }

  fun logMindSession(title: String, minutes: Int) {
    viewModelScope.launch {
      repo.logMindSession(title, minutes)
      showMessage("Учебная сессия ($minutes мин) записана.")
    }
  }

  fun setAnatomySide(side: AnatomySide) {
    viewModelScope.launch { repo.prefs.setAnatomySide(side.name) }
  }

  // --- Projects ---
  fun saveProject(project: ProjectEntity, initialSteps: List<String> = emptyList()) {
    viewModelScope.launch { repo.saveProject(project, initialSteps) }
  }

  fun deleteProject(project: ProjectEntity) {
    viewModelScope.launch { repo.deleteProject(project) }
  }

  fun addProjectStep(projectId: Long, title: String) {
    viewModelScope.launch { repo.addProjectStep(projectId, title) }
  }

  fun toggleProjectStep(step: ProjectStepEntity) {
    viewModelScope.launch { repo.toggleProjectStep(step) }
  }

  fun deleteProjectStep(step: ProjectStepEntity) {
    viewModelScope.launch { repo.deleteProjectStep(step) }
  }

  // --- Calendar & COACH ---
  fun saveCalendarEvent(event: CalendarEventEntity) {
    viewModelScope.launch { repo.saveCalendarEvent(event) }
  }

  fun deleteCalendarEvent(event: CalendarEventEntity) {
    viewModelScope.launch { repo.deleteCalendarEvent(event) }
  }

  fun setCoachHorizon(horizon: CoachHorizon) {
    _coachHorizon.value = horizon
    _customCoachOverrides.value = null
  }

  fun shiftCoachBlockTime(blockId: String, deltaMinutes: Int) {
    val current = coachPlan.value.blocks
    val updated =
      current.map { b ->
        if (b.id == blockId) {
          b.copy(startMinutes = (b.startMinutes + deltaMinutes).coerceIn(6 * 60, 22 * 60))
        } else {
          b
        }
      }.sortedWith(compareBy({ it.dateEpochDay }, { it.startMinutes }))
    _customCoachOverrides.value = updated
  }

  fun removeCoachBlock(blockId: String) {
    _customCoachOverrides.value = coachPlan.value.blocks.filterNot { it.id == blockId }
  }

  fun carryOverOverdueTasks() {
    viewModelScope.launch {
      val moved = CoachPlanner.carryOverOverdueTasks(repo, uiState.value.tasks, uiState.value.todayEpoch)
      _customCoachOverrides.value = null
      showMessage(
        if (moved > 0) "Перенесено незавершённых задач на сегодня: $moved"
        else "Просроченных задач нет.",
      )
    }
  }

  fun applyCoachPlanToCalendar() {
    viewModelScope.launch {
      val plan = coachPlan.value
      val byDay = plan.blocks.groupBy { it.dateEpochDay }
      byDay.forEach { (day, blocks) ->
        val events =
          blocks.map { b ->
            CalendarEventEntity(
              title = b.title,
              direction = b.direction,
              dateEpochDay = day,
              startTimeMinutes = b.startMinutes,
              durationMinutes = b.durationMinutes,
              type = b.type,
              linkedTaskId = b.linkedTaskId ?: -1L,
              notes = b.note,
            )
          }
        repo.replaceCoachTaskEventsForDay(day, events)
      }
      showMessage("План COACH сохранён в календарь.")
    }
  }

  // --- Profile, Settings, Reminders ---
  fun saveProfile(profile: ProfileEntity) {
    viewModelScope.launch {
      repo.saveProfile(profile)
      showMessage("Профиль обновлён.")
    }
  }

  fun saveSettings(settings: SettingsEntity) {
    viewModelScope.launch {
      repo.saveSettings(settings)
      NotificationHelper.scheduleDailyCheckWorker(appContext, settings.notificationsEnabled)
      if (settings.notificationsEnabled) {
        NotificationHelper.scheduleReminderAlarm(
          appContext,
          requestCode = 9001,
          hour = settings.dailyPlanHour,
          minute = settings.dailyPlanMinute,
          title = "AZX.V16 — Ежедневный план",
          body = "Время открыть приложение и проверить задачи дня.",
        )
      } else {
        NotificationHelper.cancelReminderAlarm(appContext, 9001)
      }
    }
  }

  fun addReminder(reminder: ReminderEntity) {
    viewModelScope.launch {
      val id = repo.saveReminder(reminder)
      if (reminder.enabled && uiState.value.settings.notificationsEnabled) {
        NotificationHelper.scheduleReminderAlarm(
          appContext,
          requestCode = (id % 100_000).toInt() + 5000,
          hour = reminder.hour,
          minute = reminder.minute,
          title = reminder.title,
          body = "Напоминание (${reminder.targetType})",
        )
      }
    }
  }

  fun toggleReminder(reminder: ReminderEntity) {
    viewModelScope.launch {
      val updated = reminder.copy(enabled = !reminder.enabled)
      repo.saveReminder(updated)
      val code = (updated.id % 100_000).toInt() + 5000
      if (updated.enabled && uiState.value.settings.notificationsEnabled) {
        NotificationHelper.scheduleReminderAlarm(
          appContext,
          requestCode = code,
          hour = updated.hour,
          minute = updated.minute,
          title = updated.title,
          body = "Напоминание (${updated.targetType})",
        )
      } else {
        NotificationHelper.cancelReminderAlarm(appContext, code)
      }
    }
  }

  fun deleteReminder(reminder: ReminderEntity) {
    viewModelScope.launch {
      NotificationHelper.cancelReminderAlarm(appContext, (reminder.id % 100_000).toInt() + 5000)
      repo.deleteReminder(reminder)
    }
  }

  // --- PDF Export ---
  fun exportStatisticsPdf(uri: Uri, periodTitle: String, periodDays: Int) {
    viewModelScope.launch {
      val st = uiState.value
      val cutoffDay = st.todayEpoch - (periodDays - 1).coerceAtLeast(0)
      val cutoffMillis = AzxRepository.millisFromEpochDay(cutoffDay)

      val periodTasks = st.tasks.filter { it.dateEpochDay >= cutoffDay }
      val tasksDone = periodTasks.count { it.status == "DONE" }
      val workoutsPeriod = st.workoutHistory.filter { it.completedAt >= cutoffMillis }
      val basketballPeriod = workoutsPeriod.count { it.category == "BASKETBALL" }
      val habitsPeriod = st.habitHistories.count { it.completed && it.dateEpochDay >= cutoffDay }
      val breakdown =
        Direction.entries.map { dir ->
          val workoutPts =
            when (dir) {
              Direction.BODY -> workoutsPeriod.count { it.category != "BASKETBALL" }
              Direction.BASKETBALL -> basketballPeriod
              else -> 0
            }
          val count =
            periodTasks.count { it.direction == dir.code && it.status == "DONE" } + workoutPts
          dir.title to count
        }

      val report =
        StatsReportData(
          periodTitle = periodTitle,
          displayName = st.profile.displayName,
          level = st.levelProgress.level,
          totalXp = st.totalXp,
          streakDays = st.streakDays,
          tasksDone = tasksDone,
          tasksTotal = periodTasks.size,
          goalsCompleted = st.goals.count { it.isCompleted },
          goalsTotal = st.goals.size,
          workoutsCount = workoutsPeriod.size,
          basketballCount = basketballPeriod,
          habitsDoneCount = habitsPeriod,
          projectsDone = st.projects.count { it.status == "DONE" },
          projectsTotal = st.projects.size,
          unlockedAchievements = st.achievements.count { it.isUnlocked },
          totalAchievements = st.achievements.size,
          directionBreakdown = breakdown,
        )
      val res = PdfReportGenerator.exportStatisticsPdf(appContext.contentResolver, uri, report)
      showMessage(
        res.fold(
          onSuccess = { "PDF-отчёт сохранён." },
          onFailure = { "Ошибка PDF: ${it.message}" },
        ),
      )
    }
  }

  // --- Backup Export / Import & Wi-Fi QR Transfer ---
  fun exportBackupToUri(uri: Uri) {
    viewModelScope.launch {
      val snapshot = repo.createBackupSnapshot()
      val res = BackupManager.writeBackupToUri(appContext.contentResolver, uri, snapshot)
      if (res.isSuccess) {
        repo.saveSettings(uiState.value.settings.copy(lastBackupAt = System.currentTimeMillis()))
        showMessage("Резервная копия сохранена (SHA-256).")
      } else {
        showMessage("Ошибка экспорта: ${res.exceptionOrNull()?.message}")
      }
    }
  }

  fun inspectBackupFromUri(uri: Uri) {
    viewModelScope.launch {
      val read = BackupManager.readBackupFromUri(appContext.contentResolver, uri)
      read.fold(
        onSuccess = { verifyAndPromptRestore(it, "Файл резервной копии") },
        onFailure = { _backupDialog.value = BackupDialogState.Error(it.message ?: "Ошибка чтения файла") },
      )
    }
  }

  private fun verifyAndPromptRestore(rawJson: String, sourceLabel: String) {
    when (val res = BackupManager.verifyAndParseEnvelope(rawJson)) {
      is BackupVerifyResult.Valid ->
        _backupDialog.value = BackupDialogState.ConfirmRestore(res, sourceLabel)
      is BackupVerifyResult.Invalid ->
        _backupDialog.value = BackupDialogState.Error(res.reason)
    }
  }

  fun confirmRestoreSnapshot(snapshot: FullBackupSnapshot) {
    viewModelScope.launch {
      _backupDialog.value = BackupDialogState.None
      repo.restoreBackupSnapshot(snapshot)
      showMessage("Резервная копия проверена и восстановлена.")
    }
  }

  fun dismissBackupDialog() {
    _backupDialog.value = BackupDialogState.None
  }

  fun startWifiQrSender() {
    viewModelScope.launch {
      _transferRunning.value = true
      val snapshot = repo.createBackupSnapshot()
      val json = BackupManager.serializeEnvelope(snapshot)
      val res =
        LocalTransferManager.startServerOnce(json) { session ->
          _transferSession.value = session
        }
      _transferRunning.value = false
      _transferSession.value = null
      showMessage(
        res.fold(
          onSuccess = { it },
          onFailure = { "Передача не выполнена: ${it.message}" },
        ),
      )
    }
  }

  fun cancelWifiQrSender() {
    _transferSession.value = null
    _transferRunning.value = false
  }

  fun receiveBackupFromWifi(ip: String, port: Int, pin: String) {
    viewModelScope.launch {
      _transferRunning.value = true
      val res = LocalTransferManager.receiveFromSender(ip, port, pin)
      _transferRunning.value = false
      res.fold(
        onSuccess = { rawJson -> verifyAndPromptRestore(rawJson, "Wi-Fi передача ($ip)") },
        onFailure = {
          _backupDialog.value =
            BackupDialogState.Error("Не удалось получить данные от отправителя: ${it.message}")
        },
      )
    }
  }
}

private data class Tuple5<A, B, C, D, E>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
)
