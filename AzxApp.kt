package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.Direction
import com.example.ui.anatomy.AnatomyScreen
import com.example.ui.navigation.AzxRoute
import com.example.ui.screens.BasketballScreen
import com.example.ui.screens.BodyScreen
import com.example.ui.screens.CoachScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DirectionDetailScreen
import com.example.ui.screens.DisciplineScreen
import com.example.ui.screens.GoalsAndDirectionsScreen
import com.example.ui.screens.LifeSkillsScreen
import com.example.ui.screens.MindScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProgressAndGameScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StyleScreen
import com.example.ui.theme.AlmostBlack
import com.example.ui.theme.DarkGray
import com.example.ui.theme.Graphite
import com.example.ui.theme.MidGray
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private data class TopLevelTab(
  val route: AzxRoute,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

private val topLevelTabs =
  listOf(
    TopLevelTab(AzxRoute.Dashboard, "Главная", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    TopLevelTab(AzxRoute.Plans, "Планы", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_plans"),
    TopLevelTab(AzxRoute.Goals, "Цели", Icons.Rounded.Flag, Icons.Outlined.Flag, "nav_goals"),
    TopLevelTab(AzxRoute.Progress, "Прогресс", Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_progress"),
    TopLevelTab(AzxRoute.Profile, "Профиль", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
  )

@Composable
fun AzxApp(
  widthSizeClass: WindowWidthSizeClass,
  viewModel: MainViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val coachPlan by viewModel.coachPlan.collectAsStateWithLifecycle()
  val banner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val backupDialog by viewModel.backupDialog.collectAsStateWithLifecycle()
  val transferSession by viewModel.transferSession.collectAsStateWithLifecycle()
  val transferRunning by viewModel.transferRunning.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }

  val isWideLayout = widthSizeClass != WindowWidthSizeClass.Compact

  LaunchedEffect(banner) {
    val msg = banner
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  fun navigateTopLevel(route: AzxRoute) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun openSpecializedModule(direction: Direction) {
    val route: AzxRoute =
      when (direction) {
        Direction.BODY -> AzxRoute.BodyModule
        Direction.BASKETBALL -> AzxRoute.BasketballModule
        Direction.MIND -> AzxRoute.MindModule
        Direction.DISCIPLINE -> AzxRoute.DisciplineModule
        Direction.STYLE -> AzxRoute.StyleModule
        Direction.PROJECTS -> AzxRoute.ProjectsModule
        Direction.LIFE_SKILLS -> AzxRoute.LifeSkillsModule
      }
    navController.navigate(route)
  }

  val showMainNav =
    currentDest?.let { dest ->
      topLevelTabs.any { dest.hasRoute(it.route::class) }
    } ?: true

  Scaffold(
    containerColor = PureBlack,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = Graphite,
          contentColor = PureWhite,
          actionColor = PureWhite,
        )
      }
    },
    bottomBar = {
      if (!isWideLayout && showMainNav) {
        androidx.compose.foundation.layout.Column {
          HorizontalDivider(color = DarkGray, thickness = 1.dp)
          NavigationBar(containerColor = PureBlack, tonalElevation = 0.dp) {
            topLevelTabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopLevel(tab.route) },
                modifier = Modifier.testTag(tab.tag),
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = {
                  Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                  )
                },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = PureBlack,
                    selectedTextColor = PureWhite,
                    indicatorColor = PureWhite,
                    unselectedIconColor = MidGray,
                    unselectedTextColor = MidGray,
                  ),
              )
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding).background(PureBlack)) {
      if (isWideLayout && showMainNav) {
        NavigationRail(
          containerColor = AlmostBlack,
          modifier = Modifier.fillMaxHeight(),
        ) {
          topLevelTabs.forEach { tab ->
            val selected = currentDest?.hasRoute(tab.route::class) == true
            NavigationRailItem(
              selected = selected,
              onClick = { navigateTopLevel(tab.route) },
              modifier = Modifier.testTag(tab.tag),
              icon = {
                Icon(
                  imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                  contentDescription = tab.label,
                )
              },
              label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = PureBlack,
                  selectedTextColor = PureWhite,
                  indicatorColor = PureWhite,
                  unselectedIconColor = MidGray,
                  unselectedTextColor = MidGray,
                ),
            )
          }
        }
      }

      Box(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
      ) {
        val contentMaxWidthModifier =
          if (isWideLayout) Modifier.fillMaxSize() else Modifier.widthIn(max = 640.dp).fillMaxSize()

        Box(modifier = contentMaxWidthModifier) {
          NavHost(navController = navController, startDestination = AzxRoute.Dashboard) {
            composable<AzxRoute.Dashboard> {
              DashboardScreen(
                state = state,
                isWideLayout = isWideLayout,
                onStartDay = viewModel::startDay,
                onAddTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onOpenCoach = { navController.navigate(AzxRoute.CoachModule) },
                onOpenGame = { navController.navigate(AzxRoute.GameModule) },
                onOpenBody = { navController.navigate(AzxRoute.BodyModule) },
                onOpenAnatomy = { navController.navigate(AzxRoute.Anatomy3D(-1L)) },
                onOpenPlans = { navigateTopLevel(AzxRoute.Plans) },
                onOpenGoals = { navigateTopLevel(AzxRoute.Goals) },
                onOpenDirection = { dir ->
                  navController.navigate(AzxRoute.DirectionDetail(dir.code))
                },
              )
            }

            composable<AzxRoute.Plans> {
              PlansScreen(
                state = state,
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onDeleteTask = viewModel::deleteTask,
                onSaveHabit = viewModel::saveHabit,
                onToggleHabitDay = viewModel::toggleHabitForDay,
                onDeleteHabit = viewModel::deleteHabit,
                onSaveDiary = viewModel::saveDiaryEntry,
                onSaveCalendarEvent = viewModel::saveCalendarEvent,
                onDeleteCalendarEvent = viewModel::deleteCalendarEvent,
                onOpenCoach = { navController.navigate(AzxRoute.CoachModule) },
              )
            }

            composable<AzxRoute.Goals> {
              GoalsAndDirectionsScreen(
                state = state,
                onSaveGoal = viewModel::saveGoal,
                onDeleteGoal = viewModel::deleteGoal,
                onAddSubtask = viewModel::addSubtask,
                onToggleSubtask = viewModel::toggleSubtask,
                onDeleteSubtask = viewModel::deleteSubtask,
                onUpdateGoalProgress = viewModel::updateGoalProgressManual,
                onOpenDirection = { dir ->
                  navController.navigate(AzxRoute.DirectionDetail(dir.code))
                },
              )
            }

            composable<AzxRoute.Progress> {
              ProgressAndGameScreen(
                state = state,
                startInGameTab = false,
                onExportPdf = viewModel::exportStatisticsPdf,
                onOpenGameStandalone = { navController.navigate(AzxRoute.GameModule) },
              )
            }

            composable<AzxRoute.Profile> {
              ProfileScreen(
                state = state,
                backupDialog = backupDialog,
                transferSession = transferSession,
                transferRunning = transferRunning,
                onSaveProfile = viewModel::saveProfile,
                onSaveSettings = viewModel::saveSettings,
                onAddReminder = viewModel::addReminder,
                onToggleReminder = viewModel::toggleReminder,
                onDeleteReminder = viewModel::deleteReminder,
                onExportBackup = viewModel::exportBackupToUri,
                onImportBackup = viewModel::inspectBackupFromUri,
                onConfirmRestore = viewModel::confirmRestoreSnapshot,
                onDismissBackupDialog = viewModel::dismissBackupDialog,
                onStartWifiQrSender = viewModel::startWifiQrSender,
                onCancelWifiQrSender = viewModel::cancelWifiQrSender,
                onReceiveFromWifi = viewModel::receiveBackupFromWifi,
                onShowMessage = viewModel::showMessage,
              )
            }

            composable<AzxRoute.DirectionDetail> { entry ->
              val route = entry.toRoute<AzxRoute.DirectionDetail>()
              val dir =
                Direction.entries.find { it.code == route.directionCode } ?: Direction.BODY
              DirectionDetailScreen(
                direction = dir,
                state = state,
                onBack = { navController.popBackStack() },
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onSaveGoal = viewModel::saveGoal,
                onOpenSpecializedModule = ::openSpecializedModule,
              )
            }

            composable<AzxRoute.BodyModule> {
              BodyScreen(
                state = state,
                onOpenAnatomy = { exId ->
                  navController.navigate(AzxRoute.Anatomy3D(exId ?: -1L))
                },
                onSaveWorkout = viewModel::saveWorkout,
                onDeleteWorkout = viewModel::deleteWorkout,
                onCompleteWorkout = viewModel::completeWorkout,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.Anatomy3D> { entry ->
              val route = entry.toRoute<AzxRoute.Anatomy3D>()
              AnatomyScreen(
                exercises = state.exercises,
                initialExerciseId = route.exerciseId.takeIf { it > 0L },
                isWideLayout = isWideLayout,
                onBack = { navController.popBackStack() },
                onSideChanged = viewModel::setAnatomySide,
              )
            }

            composable<AzxRoute.BasketballModule> {
              BasketballScreen(
                state = state,
                onLogBasketballSession = { title, mins, muscles, notes ->
                  viewModel.completeWorkout(
                    workoutId = 0L,
                    title = title,
                    category = "BASKETBALL",
                    durationMinutes = mins,
                    totalSetsCompleted = 4,
                    targetMusclesCsv = muscles,
                    notes = notes,
                  )
                },
                onOpenAnatomy = { navController.navigate(AzxRoute.Anatomy3D(15L)) },
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.MindModule> {
              MindScreen(
                state = state,
                onLogMindSession = viewModel::logMindSession,
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onSaveGoal = viewModel::saveGoal,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.DisciplineModule> {
              DisciplineScreen(
                state = state,
                onSaveDiary = viewModel::saveDiaryEntry,
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onSaveHabit = viewModel::saveHabit,
                onToggleHabitToday = { h -> viewModel.toggleHabitForDay(h, state.todayEpoch) },
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.StyleModule> {
              StyleScreen(
                state = state,
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onAddReminder = viewModel::addReminder,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.ProjectsModule> {
              ProjectsScreen(
                state = state,
                onSaveProject = viewModel::saveProject,
                onDeleteProject = viewModel::deleteProject,
                onAddProjectStep = viewModel::addProjectStep,
                onToggleProjectStep = viewModel::toggleProjectStep,
                onDeleteProjectStep = viewModel::deleteProjectStep,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.LifeSkillsModule> {
              LifeSkillsScreen(
                state = state,
                onSaveTask = viewModel::saveTask,
                onToggleTask = viewModel::toggleTaskDone,
                onSaveGoal = viewModel::saveGoal,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.CoachModule> {
              CoachScreen(
                plan = coachPlan,
                onHorizonChange = viewModel::setCoachHorizon,
                onCarryOverOverdue = viewModel::carryOverOverdueTasks,
                onShiftBlock = viewModel::shiftCoachBlockTime,
                onRemoveBlock = viewModel::removeCoachBlock,
                onApplyToCalendar = viewModel::applyCoachPlanToCalendar,
                onBack = { navController.popBackStack() },
              )
            }

            composable<AzxRoute.GameModule> {
              ProgressAndGameScreen(
                state = state,
                startInGameTab = true,
                onExportPdf = viewModel::exportStatisticsPdf,
                onBack = { navController.popBackStack() },
              )
            }
          }
        }
      }
    }
  }
}
