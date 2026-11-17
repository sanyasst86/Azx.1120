package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AzxDatabase
import com.example.data.local.AzxPreferencesDataStore
import com.example.data.local.SeedData
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class FullBackupSnapshot(
  val schemaVersion: Int = 1,
  val exportedAt: Long,
  val profile: ProfileEntity? = null,
  val settings: SettingsEntity? = null,
  val tasks: List<TaskEntity> = emptyList(),
  val goals: List<GoalEntity> = emptyList(),
  val subtasks: List<SubtaskEntity> = emptyList(),
  val habits: List<HabitEntity> = emptyList(),
  val habitHistories: List<HabitHistoryEntity> = emptyList(),
  val workouts: List<WorkoutEntity> = emptyList(),
  val exercises: List<ExerciseEntity> = emptyList(),
  val workoutHistories: List<WorkoutHistoryEntity> = emptyList(),
  val xpTransactions: List<XPTransactionEntity> = emptyList(),
  val achievements: List<AchievementEntity> = emptyList(),
  val missions: List<MissionEntity> = emptyList(),
  val projects: List<ProjectEntity> = emptyList(),
  val projectSteps: List<ProjectStepEntity> = emptyList(),
  val diaryEntries: List<DiaryEntryEntity> = emptyList(),
  val calendarEvents: List<CalendarEventEntity> = emptyList(),
  val reminders: List<ReminderEntity> = emptyList(),
)

class AzxRepository(
  private val db: AzxDatabase,
  val prefs: AzxPreferencesDataStore,
) {
  private val coreDao = db.coreDao()
  private val fitnessDao = db.fitnessDao()
  private val gpDao = db.gameProjectDao()

  val profile: Flow<ProfileEntity> =
    coreDao.observeProfile().map { it ?: ProfileEntity() }
  val settings: Flow<SettingsEntity> =
    coreDao.observeSettings().map { it ?: SettingsEntity() }
  val tasks: Flow<List<TaskEntity>> = coreDao.observeAllTasks()
  val goals: Flow<List<GoalEntity>> = coreDao.observeAllGoals()
  val subtasks: Flow<List<SubtaskEntity>> = coreDao.observeAllSubtasks()
  val habits: Flow<List<HabitEntity>> = coreDao.observeAllHabits()
  val habitHistory: Flow<List<HabitHistoryEntity>> = coreDao.observeAllHabitHistory()
  val exercises: Flow<List<ExerciseEntity>> = fitnessDao.observeAllExercises()
  val workouts: Flow<List<WorkoutEntity>> = fitnessDao.observeAllWorkouts()
  val workoutHistory: Flow<List<WorkoutHistoryEntity>> = fitnessDao.observeWorkoutHistory()
  val xpTransactions: Flow<List<XPTransactionEntity>> = gpDao.observeXpTransactions()
  val totalXp: Flow<Int> = gpDao.observeTotalXp()
  val achievements: Flow<List<AchievementEntity>> = gpDao.observeAchievements()
  val missions: Flow<List<MissionEntity>> = gpDao.observeMissions()
  val projects: Flow<List<ProjectEntity>> = gpDao.observeProjects()
  val projectSteps: Flow<List<ProjectStepEntity>> = gpDao.observeProjectSteps()
  val diaryEntries: Flow<List<DiaryEntryEntity>> = gpDao.observeDiaryEntries()
  val calendarEvents: Flow<List<CalendarEventEntity>> = gpDao.observeCalendarEvents()
  val reminders: Flow<List<ReminderEntity>> = gpDao.observeReminders()

  suspend fun ensureInitialized() {
    if (coreDao.getProfileOnce() == null) {
      coreDao.upsertProfile(ProfileEntity())
    }
    if (coreDao.getSettingsOnce() == null) {
      coreDao.upsertSettings(SettingsEntity())
    }
    if (fitnessDao.countExercises() == 0) {
      fitnessDao.insertExercises(SeedData.defaultExercises())
    }
    if (fitnessDao.countWorkouts() == 0) {
      fitnessDao.insertWorkouts(SeedData.defaultWorkouts())
    }
    if (gpDao.countAchievements() == 0) {
      gpDao.insertAchievements(SeedData.defaultAchievements())
    }
    ensureCurrentPeriodMissions()
  }

  suspend fun ensureCurrentPeriodMissions() {
    val todayKey = dayKeyFor(todayEpochDay())
    if (gpDao.getMissionsForPeriod(todayKey).isEmpty()) {
      gpDao.insertMissions(SeedData.createDailyMissions(todayKey))
    }
    val weekKey = weekKeyFor(todayEpochDay())
    if (gpDao.getMissionsForPeriod(weekKey).isEmpty()) {
      gpDao.insertMissions(SeedData.createWeeklyMissions(weekKey))
    }
  }

  // --- Profile & Settings ---
  suspend fun saveProfile(profile: ProfileEntity) {
    coreDao.upsertProfile(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))
  }

  suspend fun saveSettings(settings: SettingsEntity) {
    coreDao.upsertSettings(settings.copy(id = 1))
  }

  // --- Tasks ---
  suspend fun saveTask(task: TaskEntity): Long {
    val id = coreDao.insertTask(task)
    evaluateProgressAndAchievements()
    return id
  }

  suspend fun toggleTaskDone(task: TaskEntity) {
    val newStatus = if (task.status == "DONE") "TODO" else "DONE"
    val now = System.currentTimeMillis()
    val updated =
      task.copy(
        status = newStatus,
        completedAt = if (newStatus == "DONE") now else null,
      )
    coreDao.updateTask(updated)
    if (newStatus == "DONE") {
      val amount = task.xpAwarded.coerceIn(10, 100)
      awardXpUnique(
        actionKey = "task_done_${task.id}",
        sourceType = "TASK",
        description = "Задача выполнена: ${task.title}",
        amount = amount,
      )
      // Handle repeating tasks: create next occurrence only if none exists for next date
      if (task.repeatRule == "DAILY" || task.repeatRule == "WEEKLY") {
        val nextDay = task.dateEpochDay + if (task.repeatRule == "DAILY") 1L else 7L
        val existing = coreDao.getAllTasksOnce().any {
          it.title == task.title && it.dateEpochDay == nextDay && it.direction == task.direction
        }
        if (!existing) {
          coreDao.insertTask(
            task.copy(
              id = 0,
              dateEpochDay = nextDay,
              status = "TODO",
              completedAt = null,
            ),
          )
        }
      }
    }
    evaluateProgressAndAchievements()
  }

  suspend fun deleteTask(task: TaskEntity) {
    coreDao.deleteTask(task)
  }

  // --- Goals & Subtasks ---
  suspend fun saveGoal(goal: GoalEntity, initialSubtasks: List<String> = emptyList()): Long {
    val now = System.currentTimeMillis()
    val stamp = formatDateShort(now)
    val logEntry =
      if (goal.id == 0L) "$stamp: Цель создана"
      else "${goal.historyLog}\n$stamp: Цель обновлена".trim()
    val id = coreDao.insertGoal(goal.copy(historyLog = logEntry, updatedAt = now))
    if (initialSubtasks.isNotEmpty()) {
      initialSubtasks.filter { it.isNotBlank() }.forEachIndexed { idx, title ->
        coreDao.insertSubtask(SubtaskEntity(goalId = id, title = title.trim(), orderIndex = idx))
      }
      recalculateGoalProgress(id)
    }
    evaluateProgressAndAchievements()
    return id
  }

  suspend fun deleteGoal(goal: GoalEntity) {
    coreDao.deleteSubtasksByGoal(goal.id)
    coreDao.deleteGoal(goal)
  }

  suspend fun addSubtask(goalId: Long, title: String) {
    val existing = coreDao.getSubtasksForGoalOnce(goalId)
    coreDao.insertSubtask(
      SubtaskEntity(goalId = goalId, title = title.trim(), orderIndex = existing.size),
    )
    recalculateGoalProgress(goalId)
  }

  suspend fun toggleSubtask(subtask: SubtaskEntity) {
    val updated = subtask.copy(isDone = !subtask.isDone)
    coreDao.updateSubtask(updated)
    if (updated.isDone) {
      awardXpUnique(
        actionKey = "subtask_done_${updated.id}",
        sourceType = "SUBTASK",
        description = "Подзадача цели: ${updated.title}",
        amount = 15,
      )
    }
    recalculateGoalProgress(updated.goalId)
    evaluateProgressAndAchievements()
  }

  suspend fun deleteSubtask(subtask: SubtaskEntity) {
    coreDao.deleteSubtask(subtask)
    recalculateGoalProgress(subtask.goalId)
  }

  suspend fun updateGoalProgressManual(goal: GoalEntity, percent: Int) {
    val clamped = percent.coerceIn(0, 100)
    val now = System.currentTimeMillis()
    val stamp = formatDateShort(now)
    val done = clamped == 100
    val updated =
      goal.copy(
        progressPercent = clamped,
        isCompleted = done,
        historyLog = "${goal.historyLog}\n$stamp: Прогресс $clamped%".trim(),
        updatedAt = now,
      )
    coreDao.updateGoal(updated)
    if (done) {
      awardXpUnique(
        actionKey = "goal_done_${goal.id}",
        sourceType = "GOAL",
        description = "Цель достигнута: ${goal.title}",
        amount = 80,
      )
    }
    evaluateProgressAndAchievements()
  }

  private suspend fun recalculateGoalProgress(goalId: Long) {
    val goal = coreDao.getGoalById(goalId) ?: return
    val subs = coreDao.getSubtasksForGoalOnce(goalId)
    if (subs.isEmpty()) return
    val doneCount = subs.count { it.isDone }
    val percent = ((doneCount * 100f) / subs.size).toInt().coerceIn(0, 100)
    val done = percent == 100
    val now = System.currentTimeMillis()
    val stamp = formatDateShort(now)
    coreDao.updateGoal(
      goal.copy(
        progressPercent = percent,
        isCompleted = done,
        historyLog = "${goal.historyLog}\n$stamp: Подзадачи $doneCount/${subs.size} ($percent%)".trim(),
        updatedAt = now,
      ),
    )
    if (done) {
      awardXpUnique(
        actionKey = "goal_done_${goal.id}",
        sourceType = "GOAL",
        description = "Цель достигнута: ${goal.title}",
        amount = 80,
      )
    }
  }

  // --- Habits ---
  suspend fun saveHabit(habit: HabitEntity): Long = coreDao.insertHabit(habit)

  suspend fun deleteHabit(habit: HabitEntity) {
    coreDao.deleteHistoryForHabit(habit.id)
    coreDao.deleteHabit(habit)
  }

  suspend fun toggleHabitForDay(habit: HabitEntity, epochDay: Long) {
    val existing = coreDao.getHabitHistoryEntry(habit.id, epochDay)
    if (existing == null) {
      coreDao.insertHabitHistory(
        HabitHistoryEntity(habitId = habit.id, dateEpochDay = epochDay, completed = true),
      )
      awardXpUnique(
        actionKey = "habit_done_${habit.id}_$epochDay",
        sourceType = "HABIT",
        description = "Привычка: ${habit.title}",
        amount = 20,
      )
    } else {
      coreDao.deleteHabitHistory(existing)
    }
    recalculateHabitStreaks(habit.id)
    evaluateProgressAndAchievements()
  }

  private suspend fun recalculateHabitStreaks(habitId: Long) {
    val habit = coreDao.getAllHabitsOnce().find { it.id == habitId } ?: return
    val days =
      coreDao.getAllHabitHistoryOnce()
        .filter { it.habitId == habitId && it.completed }
        .map { it.dateEpochDay }
        .toSet()
    val today = todayEpochDay()
    var streak = 0
    var cursor = if (days.contains(today)) today else today - 1L
    while (days.contains(cursor)) {
      streak++
      cursor--
    }
    val best = maxOf(habit.bestStreak, streak)
    coreDao.updateHabit(habit.copy(currentStreak = streak, bestStreak = best))
  }

  // --- Workouts & Exercises ---
  suspend fun saveExercise(exercise: ExerciseEntity): Long = fitnessDao.insertExercise(exercise)

  suspend fun saveWorkout(workout: WorkoutEntity): Long = fitnessDao.insertWorkout(workout)

  suspend fun deleteWorkout(workout: WorkoutEntity) = fitnessDao.deleteWorkout(workout)

  suspend fun completeWorkout(
    workoutId: Long,
    title: String,
    category: String,
    durationMinutes: Int,
    totalSetsCompleted: Int,
    targetMusclesCsv: String,
    notes: String = "",
  ): Long {
    val xp = if (category == "RECOVERY" || category == "LFK") 40 else 65
    val historyId =
      fitnessDao.insertWorkoutHistory(
        WorkoutHistoryEntity(
          workoutId = workoutId,
          workoutTitle = title,
          category = category,
          durationMinutes = durationMinutes.coerceAtLeast(1),
          totalSetsCompleted = totalSetsCompleted,
          targetMusclesCsv = targetMusclesCsv,
          completedAt = System.currentTimeMillis(),
          notes = notes,
          xpAwarded = xp,
        ),
      )
    val source = if (category == "BASKETBALL") "BASKETBALL" else "WORKOUT"
    awardXpUnique(
      actionKey = "workout_done_$historyId",
      sourceType = source,
      description = "Тренировка: $title",
      amount = xp,
    )
    evaluateProgressAndAchievements()
    return historyId
  }

  suspend fun logMindSession(title: String, minutes: Int) {
    val day = todayEpochDay()
    val taskId =
      coreDao.insertTask(
        TaskEntity(
          title = title,
          description = "Учебная сессия концентрации ($minutes мин)",
          direction = "MIND",
          dateEpochDay = day,
          durationMinutes = minutes,
          priority = 2,
          status = "DONE",
          completedAt = System.currentTimeMillis(),
          xpAwarded = 30,
        ),
      )
    awardXpUnique(
      actionKey = "mind_session_$taskId",
      sourceType = "MIND",
      description = "Концентрация ($minutes мин): $title",
      amount = 30,
    )
    evaluateProgressAndAchievements()
  }

  // --- Projects & Steps ---
  suspend fun saveProject(project: ProjectEntity, initialSteps: List<String> = emptyList()): Long {
    val now = System.currentTimeMillis()
    val stamp = formatDateShort(now)
    val log =
      if (project.id == 0L) "$stamp: Проект создан"
      else "${project.historyLog}\n$stamp: Проект обновлён".trim()
    val id = gpDao.insertProject(project.copy(historyLog = log, updatedAt = now))
    if (initialSteps.isNotEmpty()) {
      initialSteps.filter { it.isNotBlank() }.forEachIndexed { idx, stepTitle ->
        gpDao.insertProjectStep(
          ProjectStepEntity(projectId = id, title = stepTitle.trim(), orderIndex = idx),
        )
      }
      recalculateProjectProgress(id)
    }
    return id
  }

  suspend fun deleteProject(project: ProjectEntity) {
    gpDao.deleteStepsForProject(project.id)
    gpDao.deleteProject(project)
  }

  suspend fun addProjectStep(projectId: Long, title: String) {
    val existing = gpDao.getStepsForProjectOnce(projectId)
    gpDao.insertProjectStep(
      ProjectStepEntity(projectId = projectId, title = title.trim(), orderIndex = existing.size),
    )
    recalculateProjectProgress(projectId)
  }

  suspend fun toggleProjectStep(step: ProjectStepEntity) {
    val updated = step.copy(isDone = !step.isDone)
    gpDao.updateProjectStep(updated)
    if (updated.isDone) {
      awardXpUnique(
        actionKey = "project_step_${updated.id}",
        sourceType = "PROJECT_STEP",
        description = "Этап проекта: ${updated.title}",
        amount = 25,
      )
    }
    recalculateProjectProgress(updated.projectId)
    evaluateProgressAndAchievements()
  }

  suspend fun deleteProjectStep(step: ProjectStepEntity) {
    gpDao.deleteProjectStep(step)
    recalculateProjectProgress(step.projectId)
  }

  private suspend fun recalculateProjectProgress(projectId: Long) {
    val project = gpDao.getAllProjectsOnce().find { it.id == projectId } ?: return
    val steps = gpDao.getStepsForProjectOnce(projectId)
    if (steps.isEmpty()) return
    val doneCount = steps.count { it.isDone }
    val percent = ((doneCount * 100f) / steps.size).toInt().coerceIn(0, 100)
    val status = if (percent == 100) "DONE" else "ACTIVE"
    val now = System.currentTimeMillis()
    val stamp = formatDateShort(now)
    gpDao.updateProject(
      project.copy(
        progressPercent = percent,
        status = status,
        historyLog = "${project.historyLog}\n$stamp: Этапы $doneCount/${steps.size} ($percent%)".trim(),
        updatedAt = now,
      ),
    )
    if (status == "DONE") {
      awardXpUnique(
        actionKey = "project_done_${project.id}",
        sourceType = "PROJECT",
        description = "Проект завершён: ${project.title}",
        amount = 90,
      )
    }
  }

  // --- Diary, Calendar, Reminders ---
  suspend fun saveDiaryEntry(entry: DiaryEntryEntity) {
    gpDao.upsertDiaryEntry(entry.copy(updatedAt = System.currentTimeMillis()))
    awardXpUnique(
      actionKey = "diary_entry_${entry.dateEpochDay}",
      sourceType = "DIARY",
      description = "Запись самочувствия и итогов дня",
      amount = 15,
    )
    evaluateProgressAndAchievements()
  }

  suspend fun saveCalendarEvent(event: CalendarEventEntity): Long = gpDao.insertCalendarEvent(event)

  suspend fun deleteCalendarEvent(event: CalendarEventEntity) = gpDao.deleteCalendarEvent(event)

  suspend fun replaceCoachTaskEventsForDay(epochDay: Long, events: List<CalendarEventEntity>) {
    db.withTransaction {
      gpDao.clearPlannedTasksForDay(epochDay)
      if (events.isNotEmpty()) gpDao.insertCalendarEvents(events)
    }
  }

  suspend fun saveReminder(reminder: ReminderEntity): Long = gpDao.insertReminder(reminder)

  suspend fun deleteReminder(reminder: ReminderEntity) = gpDao.deleteReminder(reminder)

  // --- XP & Streak & Achievements & Missions ---
  suspend fun awardXpUnique(
    actionKey: String,
    sourceType: String,
    description: String,
    amount: Int,
  ): Boolean {
    if (amount <= 0) return false
    val inserted =
      gpDao.insertXpIfUnique(
        XPTransactionEntity(
          actionKey = actionKey,
          sourceType = sourceType,
          description = description,
          amount = amount,
          createdAt = System.currentTimeMillis(),
        ),
      )
    return inserted != -1L
  }

  suspend fun evaluateProgressAndAchievements() {
    ensureCurrentPeriodMissions()
    val today = todayEpochDay()
    val dayKey = dayKeyFor(today)
    val weekKey = weekKeyFor(today)

    val allTasks = coreDao.getAllTasksOnce()
    val doneTasks = allTasks.filter { it.status == "DONE" }
    val todayDoneTasks = doneTasks.count { it.dateEpochDay == today || epochDayFromMillis(it.completedAt) == today }
    val weekStart = today - 6L
    val weekDoneTasks = doneTasks.count { (epochDayFromMillis(it.completedAt) ?: it.dateEpochDay) >= weekStart }

    val allHabitsHistory = coreDao.getAllHabitHistoryOnce().filter { it.completed }
    val todayHabitsDone = allHabitsHistory.count { it.dateEpochDay == today }

    val allWorkouts = fitnessDao.getAllWorkoutHistoryOnce()
    val todayWorkouts = allWorkouts.count { epochDayFromMillis(it.completedAt) == today }
    val weekWorkouts = allWorkouts.count { (epochDayFromMillis(it.completedAt) ?: 0L) >= weekStart }
    val basketballWorkouts = allWorkouts.count { it.category == "BASKETBALL" }

    val completedGoals = coreDao.getAllGoalsOnce().count { it.isCompleted }
    val completedProjects = gpDao.getAllProjectsOnce().count { it.status == "DONE" }

    // Update missions
    val missions = gpDao.getMissionsForPeriod(dayKey) + gpDao.getMissionsForPeriod(weekKey)
    missions.forEach { m ->
      val current =
        when (m.code) {
          "DAILY_TASKS" -> todayDoneTasks
          "DAILY_HABIT" -> todayHabitsDone
          "DAILY_BODY_OR_BALL" -> todayWorkouts
          "WEEKLY_WORKOUTS" -> weekWorkouts
          "WEEKLY_TASKS" -> weekDoneTasks
          else -> m.currentCount
        }.coerceAtMost(m.targetCount)
      val completed = current >= m.targetCount
      var claimed = m.xpClaimed
      if (completed && !claimed) {
        val ok =
          awardXpUnique(
            actionKey = "mission_${m.code}_${m.periodKey}",
            sourceType = "MISSION",
            description = "Миссия: ${m.title}",
            amount = m.xpReward,
          )
        if (ok) claimed = true
      }
      if (current != m.currentCount || completed != m.isCompleted || claimed != m.xpClaimed) {
        gpDao.updateMission(
          m.copy(currentCount = current, isCompleted = completed, xpClaimed = claimed),
        )
      }
    }

    // Calculate current streak (including rest days without penalty)
    val streak = computeOverallStreak(allTasks, allHabitsHistory, allWorkouts, gpDao.getAllDiaryEntriesOnce())
    val totalXpNow = gpDao.getTotalXpOnce()

    // Evaluate achievements
    val achievements = gpDao.getAllAchievementsOnce()
    achievements.filter { !it.isUnlocked }.forEach { ach ->
      val progressVal =
        when (ach.requirementType) {
          "TASKS" -> doneTasks.size
          "WORKOUTS" -> allWorkouts.size
          "BASKETBALL" -> basketballWorkouts
          "GOALS" -> completedGoals
          "PROJECTS" -> completedProjects
          "STREAK" -> streak
          "XP" -> totalXpNow
          else -> 0
        }
      if (progressVal >= ach.requirementTarget) {
        gpDao.updateAchievement(
          ach.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis()),
        )
        awardXpUnique(
          actionKey = "ach_${ach.code}",
          sourceType = "ACHIEVEMENT",
          description = "Достижение: ${ach.title}",
          amount = ach.xpReward,
        )
      }
    }
  }

  fun computeOverallStreak(
    tasks: List<TaskEntity>,
    habitHistories: List<HabitHistoryEntity>,
    workoutHistories: List<WorkoutHistoryEntity>,
    diaryEntries: List<DiaryEntryEntity>,
  ): Int {
    val activeDays = mutableSetOf<Long>()
    tasks.filter { it.status == "DONE" }.forEach { t ->
      epochDayFromMillis(t.completedAt)?.let { activeDays.add(it) } ?: activeDays.add(t.dateEpochDay)
    }
    habitHistories.filter { it.completed }.forEach { activeDays.add(it.dateEpochDay) }
    workoutHistories.forEach { w -> epochDayFromMillis(w.completedAt)?.let { activeDays.add(it) } }
    // Rest day recorded in diary preserves streak without penalty
    diaryEntries.filter { it.isRestDay }.forEach { activeDays.add(it.dateEpochDay) }

    val today = todayEpochDay()
    var streak = 0
    var cursor = if (activeDays.contains(today)) today else today - 1L
    while (activeDays.contains(cursor)) {
      streak++
      cursor--
    }
    return streak
  }

  // --- Backup & Restore ---
  suspend fun createBackupSnapshot(): FullBackupSnapshot =
    FullBackupSnapshot(
      exportedAt = System.currentTimeMillis(),
      profile = coreDao.getProfileOnce(),
      settings = coreDao.getSettingsOnce(),
      tasks = coreDao.getAllTasksOnce(),
      goals = coreDao.getAllGoalsOnce(),
      subtasks = coreDao.getAllSubtasksOnce(),
      habits = coreDao.getAllHabitsOnce(),
      habitHistories = coreDao.getAllHabitHistoryOnce(),
      workouts = fitnessDao.getAllWorkoutsOnce(),
      exercises = fitnessDao.getAllExercisesOnce(),
      workoutHistories = fitnessDao.getAllWorkoutHistoryOnce(),
      xpTransactions = gpDao.getAllXpTransactionsOnce(),
      achievements = gpDao.getAllAchievementsOnce(),
      missions = gpDao.getAllMissionsOnce(),
      projects = gpDao.getAllProjectsOnce(),
      projectSteps = gpDao.getAllProjectStepsOnce(),
      diaryEntries = gpDao.getAllDiaryEntriesOnce(),
      calendarEvents = gpDao.getAllCalendarEventsOnce(),
      reminders = gpDao.getAllRemindersOnce(),
    )

  suspend fun restoreBackupSnapshot(snapshot: FullBackupSnapshot) {
    db.withTransaction {
      coreDao.clearTasks()
      coreDao.clearSubtasks()
      coreDao.clearGoals()
      coreDao.clearHabitHistory()
      coreDao.clearHabits()
      fitnessDao.clearWorkoutHistory()
      fitnessDao.clearWorkouts()
      fitnessDao.clearExercises()
      gpDao.clearXpTransactions()
      gpDao.clearAchievements()
      gpDao.clearMissions()
      gpDao.clearProjectSteps()
      gpDao.clearProjects()
      gpDao.clearDiaryEntries()
      gpDao.clearCalendarEvents()
      gpDao.clearReminders()

      snapshot.profile?.let { coreDao.upsertProfile(it.copy(id = 1)) }
      snapshot.settings?.let {
        coreDao.upsertSettings(it.copy(id = 1, lastBackupAt = System.currentTimeMillis()))
      }
      if (snapshot.tasks.isNotEmpty()) coreDao.insertTasks(snapshot.tasks)
      if (snapshot.goals.isNotEmpty()) coreDao.insertGoals(snapshot.goals)
      if (snapshot.subtasks.isNotEmpty()) coreDao.insertSubtasks(snapshot.subtasks)
      if (snapshot.habits.isNotEmpty()) coreDao.insertHabits(snapshot.habits)
      if (snapshot.habitHistories.isNotEmpty()) coreDao.insertHabitHistories(snapshot.habitHistories)
      if (snapshot.exercises.isNotEmpty()) fitnessDao.insertExercises(snapshot.exercises)
      else fitnessDao.insertExercises(SeedData.defaultExercises())
      if (snapshot.workouts.isNotEmpty()) fitnessDao.insertWorkouts(snapshot.workouts)
      else fitnessDao.insertWorkouts(SeedData.defaultWorkouts())
      if (snapshot.workoutHistories.isNotEmpty()) fitnessDao.insertWorkoutHistories(snapshot.workoutHistories)
      if (snapshot.xpTransactions.isNotEmpty()) gpDao.insertXpTransactions(snapshot.xpTransactions)
      if (snapshot.achievements.isNotEmpty()) gpDao.upsertAchievements(snapshot.achievements)
      else gpDao.insertAchievements(SeedData.defaultAchievements())
      if (snapshot.missions.isNotEmpty()) gpDao.insertMissions(snapshot.missions)
      if (snapshot.projects.isNotEmpty()) gpDao.insertProjects(snapshot.projects)
      if (snapshot.projectSteps.isNotEmpty()) gpDao.insertProjectSteps(snapshot.projectSteps)
      if (snapshot.diaryEntries.isNotEmpty()) gpDao.insertDiaryEntries(snapshot.diaryEntries)
      if (snapshot.calendarEvents.isNotEmpty()) gpDao.insertCalendarEvents(snapshot.calendarEvents)
      if (snapshot.reminders.isNotEmpty()) gpDao.insertReminders(snapshot.reminders)
    }
    ensureInitialized()
  }

  companion object {
    fun todayEpochDay(): Long {
      val cal = Calendar.getInstance()
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      val offsetMs = cal.timeZone.getOffset(cal.timeInMillis)
      return (cal.timeInMillis + offsetMs) / 86_400_000L
    }

    fun epochDayFromMillis(millis: Long?): Long? {
      if (millis == null) return null
      val cal = Calendar.getInstance()
      cal.timeInMillis = millis
      val offsetMs = cal.timeZone.getOffset(millis)
      return (millis + offsetMs) / 86_400_000L
    }

    fun millisFromEpochDay(epochDay: Long): Long {
      val cal = Calendar.getInstance()
      val approx = epochDay * 86_400_000L
      val offsetMs = cal.timeZone.getOffset(approx)
      return approx - offsetMs + 12 * 3_600_000L // Noon local to avoid DST drift
    }

    fun dayKeyFor(epochDay: Long): String {
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      return fmt.format(Date(millisFromEpochDay(epochDay)))
    }

    fun weekKeyFor(epochDay: Long): String {
      val cal = Calendar.getInstance()
      cal.timeInMillis = millisFromEpochDay(epochDay)
      val year = cal.get(Calendar.YEAR)
      val week = cal.get(Calendar.WEEK_OF_YEAR)
      return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    fun formatDateShort(millis: Long): String {
      val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
      return fmt.format(Date(millis))
    }

    fun formatEpochDay(epochDay: Long): String {
      val fmt = SimpleDateFormat("dd MMM, EEE", Locale("ru"))
      return fmt.format(Date(millisFromEpochDay(epochDay)))
    }
  }
}
