package com.morningsteps.kids.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.morningsteps.kids.AppContainer
import com.morningsteps.kids.data.repository.AppSettings
import com.morningsteps.kids.ui.history.HistoryDetailScreen
import com.morningsteps.kids.ui.history.HistoryDetailViewModel
import com.morningsteps.kids.ui.history.HistoryScreen
import com.morningsteps.kids.ui.history.HistoryViewModel
import com.morningsteps.kids.ui.parent.EditorScreen
import com.morningsteps.kids.ui.parent.EditorViewModel
import com.morningsteps.kids.ui.parent.ParentScreen
import com.morningsteps.kids.ui.parent.ParentViewModel
import com.morningsteps.kids.ui.parent.PrivacyScreen
import com.morningsteps.kids.ui.path.CompleteScreen
import com.morningsteps.kids.ui.path.FinishScreen
import com.morningsteps.kids.ui.path.FinishViewModel
import com.morningsteps.kids.ui.path.PathScreen
import com.morningsteps.kids.ui.path.PathViewModel
import com.morningsteps.kids.ui.path.RoutinePickerScreen
import com.morningsteps.kids.ui.path.RoutinePickerViewModel
import com.morningsteps.kids.ui.step.StepRoute
import com.morningsteps.kids.ui.step.StepViewModel
import com.morningsteps.kids.ui.theme.MorningStepsTheme
import com.morningsteps.kids.ui.theme.Palette

private object Routes {
    const val PATH = "path"
    const val PICKER = "routines"
    const val STEP = "step/{routineId}/{stepId}"
    const val FINISH = "finish/{routineId}"
    const val COMPLETE = "complete/{routineId}/{historyId}"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{historyId}"
    const val PARENT = "parent"
    const val EDITOR = "editor?routineId={routineId}"
    const val PRIVACY = "privacy"

    fun step(routineId: String, stepId: String) = "step/$routineId/$stepId"
    fun finish(routineId: String) = "finish/$routineId"
    fun complete(routineId: String, historyId: Long) = "complete/$routineId/$historyId"
    fun historyDetail(id: Long) = "history/$id"
    fun editor(routineId: String?) = if (routineId == null) "editor" else "editor?routineId=$routineId"
}

@Composable
fun AppRoot(container: AppContainer) {
    val settings by remember(container) { container.settings.settings }
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    MorningStepsTheme(reduceMotionSetting = settings.reduceMotion) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Ivory),
        ) {
            AppNavHost(container, rememberNavController())
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer, nav: NavHostController) {
    val repo = container.routines
    val factories = remember(container) { ViewModelFactories(container) }

    fun backToPath() {
        if (!nav.popBackStack(Routes.PATH, inclusive = false)) nav.navigate(Routes.PATH)
    }

    NavHost(navController = nav, startDestination = Routes.PATH) {
        composable(Routes.PATH) {
            val vm: PathViewModel = viewModel(factory = factories.path())
            val state by vm.state.collectAsStateWithLifecycle()
            PathScreen(
                state = state,
                onOpenPicker = { nav.navigate(Routes.PICKER) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenParents = { nav.navigate(Routes.PARENT) { launchSingleTop = true } },
                onOpenStep = { r, s -> nav.navigate(Routes.step(r, s)) },
                onToggleStep = vm::toggle,
                onOpenFinish = { r -> nav.navigate(Routes.finish(r)) },
                onStartFresh = vm::startFresh,
            )
        }

        composable(Routes.PICKER) {
            val vm: RoutinePickerViewModel = viewModel(factory = factories.picker())
            val state by vm.state.collectAsStateWithLifecycle()
            RoutinePickerScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onSelect = { id -> vm.select(id) { backToPath() } },
            )
        }

        composable(
            Routes.STEP,
            arguments = listOf(
                navArgument("routineId") { type = NavType.StringType },
                navArgument("stepId") { type = NavType.StringType },
            ),
        ) { entry ->
            val routineId = entry.arguments?.getString("routineId").orEmpty()
            val stepId = entry.arguments?.getString("stepId").orEmpty()
            val vm: StepViewModel = viewModel(factory = factories.step(routineId, stepId))
            val state by vm.state.collectAsStateWithLifecycle()
            StepRoute(
                viewModel = vm,
                state = state,
                onBack = { nav.popBackStack() },
                onOpenStep = { r, s ->
                    nav.navigate(Routes.step(r, s)) { popUpTo(Routes.PATH) }
                },
                onOpenFinish = { r -> nav.navigate(Routes.finish(r)) { popUpTo(Routes.PATH) } },
            )
        }

        composable(Routes.FINISH, arguments = listOf(navArgument("routineId") { type = NavType.StringType })) { entry ->
            val routineId = entry.arguments?.getString("routineId").orEmpty()
            val vm: FinishViewModel = viewModel(factory = factories.finish(routineId))
            val state by vm.state.collectAsStateWithLifecycle()
            val event by vm.event.collectAsStateWithLifecycle()
            FinishScreen(
                viewModel = vm,
                state = state,
                event = event,
                onBackToPath = { backToPath() },
                onSaved = { historyId ->
                    nav.navigate(Routes.complete(routineId, historyId)) { popUpTo(Routes.PATH) }
                },
            )
        }

        composable(
            Routes.COMPLETE,
            arguments = listOf(
                navArgument("routineId") { type = NavType.StringType },
                navArgument("historyId") { type = NavType.LongType },
            ),
        ) { entry ->
            val routineId = entry.arguments?.getString("routineId").orEmpty()
            val historyId = entry.arguments?.getLong("historyId") ?: 0L
            val historyEntry by remember(historyId) { repo.historyEntry(historyId) }
                .collectAsStateWithLifecycle(initialValue = null)
            CompleteScreen(
                entry = historyEntry,
                onBackToRoutines = {
                    nav.navigate(Routes.PICKER) { popUpTo(Routes.PATH) }
                },
                onStartAgain = {
                    // A new session only begins when the child completes a step: no empty session is created.
                    if (routineId.isNotEmpty()) {
                        nav.navigate(Routes.PATH) { popUpTo(Routes.PATH) { inclusive = true } }
                    } else {
                        backToPath()
                    }
                },
            )
        }

        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = factories.history())
            val state by vm.state.collectAsStateWithLifecycle()
            HistoryScreen(state = state, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.historyDetail(it)) })
        }

        composable(Routes.HISTORY_DETAIL, arguments = listOf(navArgument("historyId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("historyId") ?: 0L
            val vm: HistoryDetailViewModel = viewModel(factory = factories.historyDetail(id))
            val state by vm.state.collectAsStateWithLifecycle()
            HistoryDetailScreen(state = state, onBack = { nav.popBackStack() })
        }

        composable(Routes.PARENT) {
            val vm: ParentViewModel = viewModel(factory = factories.parent())
            val state by vm.state.collectAsStateWithLifecycle()
            ParentScreen(
                state = state,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onEditRoutine = { id -> nav.navigate(Routes.editor(id)) },
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                onAllCleared = { backToPath() },
            )
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("routineId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val routineId = entry.arguments?.getString("routineId")
            val vm: EditorViewModel = viewModel(factory = factories.editor(routineId))
            val state by vm.state.collectAsStateWithLifecycle()
            EditorScreen(state = state, viewModel = vm, onClosed = { nav.popBackStack() })
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() })
        }
    }
}
