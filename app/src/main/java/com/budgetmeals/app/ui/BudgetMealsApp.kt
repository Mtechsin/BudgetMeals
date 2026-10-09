package com.budgetmeals.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.notifications.ReminderScheduler
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab(val route: String, val label: String) {
    HOME("home", "Home"),
    PLAN("plan", "Plan"),
    STOCK("stock", "Stock"),
    SHOP("shop", "Shop"),
    MORE("more", "More"),
}

sealed interface AppSheet {
    data object QuickAdd : AppSheet
    data object AddPurchase : AppSheet
    data class EditStock(val item: StockItem) : AppSheet
    data class LogUsage(val item: StockItem) : AppSheet
    data object AddExpense : AppSheet
    data class EditExpense(val expense: Expense) : AppSheet
    data object AddCategory : AppSheet
    data object AddFoodItem : AppSheet
    data class EditFoodItem(val item: FoodCatalogItem) : AppSheet
    data object AddTemplate : AppSheet
    data class EditTemplate(val template: MealTemplate) : AppSheet
    data class AssignDayMeal(
        val date: LocalDate,
        val mealType: MealType,
        val currentTemplate: MealTemplate?,
    ) : AppSheet
    data object AddShopping : AppSheet
    data class EditShopping(val item: ShoppingItem) : AppSheet
    data class BuyShopping(val item: ShoppingItem) : AppSheet
    data object AddSpares : AppSheet
    data object DayReview : AppSheet
    data object BudgetCorrection : AppSheet
    data object Settings : AppSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetMealsApp(
    viewModel: BudgetViewModel,
    dayReviewRequestKey: Long = 0L,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val coroutineScope = rememberCoroutineScope()
    var mainRoute by rememberSaveable { mutableStateOf(MainTab.HOME.route) }
    val pagerState = rememberPagerState(
        initialPage = mainTabOrdinal(mainRoute).coerceAtLeast(0),
        pageCount = { MainTab.entries.size },
    )
    var detailRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable(stateSaver = mealEditorSheetSaver) { mutableStateOf<AppSheet?>(null) }
    var sheetGeneration by rememberSaveable { mutableIntStateOf(0) }
    var closingSheetGeneration by remember { mutableIntStateOf(-1) }
    var lastHandledDayReviewRequest by rememberSaveable(saver = mutableLongStateSaver) {
        mutableLongStateOf(0L)
    }
    var csvToShare by remember { mutableStateOf<String?>(null) }
    var foodJsonToShare by remember { mutableStateOf<String?>(null) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    val predictiveBackProgress = remember { Animatable(0f) }
    val predictiveBackOffset = remember { Animatable(0f) }
    val predictiveBackScale = remember { Animatable(1f) }
    var gestureCommitted by remember { mutableStateOf(false) }
    var detailContentWidth by remember { mutableIntStateOf(0) }

    val openSheet: (AppSheet) -> Unit = { nextSheet ->
        sheetGeneration += 1
        sheet = nextSheet
    }

    val openMainTab: (MainTab) -> Unit = { tab ->
        mainRoute = tab.route
        detailRoute = null
        if (pagerState.currentPage != tab.ordinal) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(
                    page = tab.ordinal,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                )
            }
        }
    }
    val openDetail: (String) -> Unit = {
        if (gestureCommitted || predictiveBackOffset.value != 0f) {
            coroutineScope.launch {
                predictiveBackOffset.snapTo(0f)
                predictiveBackScale.snapTo(1f)
                predictiveBackProgress.snapTo(0f)
                gestureCommitted = false
            }
        }
        detailRoute = it
    }

    val importFoodJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("The selected food catalog file could not be opened.")
                }
                viewModel.importFoodCatalogJson(json)
            } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                throw error
            } catch (error: Exception) {
                viewModel.notify(error.message ?: "The selected food catalog file could not be read.")
            }
        }
    }

    LaunchedEffect(dayReviewRequestKey, sheet) {
        if (dayReviewRequestKey > 0L && dayReviewRequestKey != lastHandledDayReviewRequest && sheet == null) {
            openSheet(AppSheet.DayReview)
            lastHandledDayReviewRequest = dayReviewRequestKey
        }
    }

    LaunchedEffect(uiState.snapshot.today) {
        val nextMidnight = LocalDateTime.of(uiState.snapshot.today.plusDays(1), LocalTime.MIDNIGHT)
        val waitMillis = Duration.between(LocalDateTime.now(), nextMidnight).toMillis() + 1_000L
        delay(waitMillis.coerceAtLeast(1_000L))
        viewModel.refresh()
    }

    PredictiveBackHandler(enabled = detailRoute != null && sheet == null) { progress ->
        isPredictiveBackActive = true
        gestureCommitted = false
        var lastSwipeEdge = BackEventCompat.EDGE_LEFT
        try {
            progress.collect { event: BackEventCompat ->
                lastSwipeEdge = event.swipeEdge
                predictiveBackProgress.snapTo(event.progress)
                val direction = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                val safeWidth = if (detailContentWidth > 0) detailContentWidth.toFloat() else 1080f
                val maxDrag = safeWidth * 0.32f
                predictiveBackOffset.snapTo(direction * event.progress * maxDrag)
                predictiveBackScale.snapTo(1f - (0.10f * event.progress))
            }
            // User committed gesture to go back
            gestureCommitted = true
            val exitDirection = if (lastSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            val safeWidth = if (detailContentWidth > 0) detailContentWidth.toFloat() else 1080f
            val exitTarget = exitDirection * safeWidth * 1.15f
            kotlinx.coroutines.coroutineScope {
                launch {
                    predictiveBackOffset.animateTo(
                        targetValue = exitTarget,
                        animationSpec = tween(durationMillis = 200, easing = Motion.EmphasizedAccelerate),
                    )
                }
                launch {
                    predictiveBackProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 200, easing = Motion.EmphasizedDecelerate),
                    )
                }
            }
            detailRoute = null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // User cancelled gesture
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                kotlinx.coroutines.coroutineScope {
                    launch {
                        predictiveBackOffset.animateTo(0f, tween(durationMillis = 200, easing = Motion.EmphasizedDecelerate))
                    }
                    launch {
                        predictiveBackScale.animateTo(1f, tween(durationMillis = 200, easing = Motion.EmphasizedDecelerate))
                    }
                    launch {
                        predictiveBackProgress.animateTo(0f, tween(durationMillis = 200, easing = Motion.EmphasizedDecelerate))
                    }
                }
            }
            throw e
        } finally {
            isPredictiveBackActive = false
            if (!gestureCommitted) {
                predictiveBackOffset.snapTo(0f)
                predictiveBackScale.snapTo(1f)
                predictiveBackProgress.snapTo(0f)
            }
        }
    }

    LaunchedEffect(detailRoute) {
        if (detailRoute == null && gestureCommitted) {
            delay(80)
            predictiveBackOffset.snapTo(0f)
            predictiveBackScale.snapTo(1f)
            predictiveBackProgress.snapTo(0f)
            gestureCommitted = false
        }
    }

    BackHandler(enabled = detailRoute == null && sheet == null && (pagerState.currentPage != 0 || mainRoute != MainTab.HOME.route)) {
        openMainTab(MainTab.HOME)
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(
        uiState.snapshot.settings.remindersEnabled,
        uiState.snapshot.settings.shoppingDay,
        uiState.snapshot.settings.kosharyDay,
    ) {
        ReminderScheduler.sync(context, uiState.snapshot.settings)
    }

    LaunchedEffect(csvToShare) {
        val csv = csvToShare ?: return@LaunchedEffect
        try {
            shareExport(
                context = context,
                content = csv,
                fileNamePrefix = "budgetmeals",
                extension = "csv",
                mimeType = "text/csv",
                subject = "BudgetMeals export",
                chooserTitle = "Share budget summary",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            viewModel.notify(error.message?.takeIf { it.isNotBlank() } ?: "Could not share the budget summary.")
        } finally {
            if (csvToShare == csv) csvToShare = null
        }
    }

    LaunchedEffect(foodJsonToShare) {
        val json = foodJsonToShare ?: return@LaunchedEffect
        try {
            shareExport(
                context = context,
                content = json,
                fileNamePrefix = "budgetmeals-food-catalog",
                extension = "json",
                mimeType = "application/json",
                subject = "BudgetMeals food catalog",
                chooserTitle = "Share food catalog JSON",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            viewModel.notify(error.message?.takeIf { it.isNotBlank() } ?: "Could not share the food catalog.")
        } finally {
            if (foodJsonToShare == json) foodJsonToShare = null
        }
    }

    val isMainRoute = detailRoute == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { detailContentWidth = it.width }
    ) {
        // --- BASE LAYER: Scaffold with Main Tabs & Navigation Bar ---
        val baseScale = if (isPredictiveBackActive) (0.94f + 0.06f * predictiveBackProgress.value) else 1.0f
        val baseCornerRadius = if (isPredictiveBackActive) ((20f * (1f - predictiveBackProgress.value)).dp) else 0.dp
        val baseScrimAlpha = if (isPredictiveBackActive) (0.22f * (1f - predictiveBackProgress.value)) else 0.0f

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = baseScale
                    scaleY = baseScale
                    clip = baseCornerRadius > 0.dp
                    shape = RoundedCornerShape(baseCornerRadius)
                },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.extendedColors.snackColor,
                        contentColor = MaterialTheme.extendedColors.onSnackColor,
                        actionContentColor = MaterialTheme.extendedColors.onSnackColor,
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isMainRoute && !uiState.isLoading,
                    enter = scaleIn(tween(Motion.MediumMs, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Motion.ShortMs)),
                    exit = scaleOut(tween(Motion.ShortMs, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Motion.ShortMs)),
                ) {
                    val activeTab = MainTab.entries.getOrElse(if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage) { MainTab.HOME }
                    val fab: Pair<String, () -> Unit>? = when (activeTab) {
                        MainTab.HOME -> "Add" to { openSheet(AppSheet.QuickAdd) }
                        MainTab.PLAN -> "New meal" to { openSheet(AppSheet.AddTemplate) }
                        MainTab.STOCK -> "Purchase" to { openSheet(AppSheet.AddPurchase) }
                        MainTab.SHOP -> "Add item" to { openSheet(AppSheet.AddShopping) }
                        else -> null
                    }
                    if (fab != null) {
                        ExtendedFloatingActionButton(
                            onClick = fab.second,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(20.dp),
                            icon = { Icon(AppIcons.Add, contentDescription = null) },
                            text = { Text(fab.first, style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = isMainRoute,
                    enter = slideInVertically(tween(Motion.MediumMs, easing = Motion.EmphasizedDecelerate)) { it } + fadeIn(tween(Motion.ShortMs)),
                    exit = slideOutVertically(tween(Motion.MediumMs, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(Motion.ShortMs)),
                ) {
                    NavigationBar(
                        containerColor = cardSurfaceColor(),
                        tonalElevation = 0.dp,
                    ) {
                        val activeTab = MainTab.entries.getOrElse(if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage) { MainTab.HOME }
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab && detailRoute == null
                            val badgeCount = if (tab == MainTab.SHOP) uiState.snapshot.openShoppingCount else 0
                            NavigationBarItem(
                                selected = selected,
                                onClick = { openMainTab(tab) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = tabIcon(tab, selected = selected),
                                            modifier = Modifier.size(24.dp),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                Crossfade(
                    targetState = uiState.isLoading,
                    animationSpec = Motion.FadeTween,
                    label = "loading",
                ) { isLoading ->
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                            userScrollEnabled = false,
                            beyondViewportPageCount = 4,
                            key = { page -> MainTab.entries[page].route },
                        ) { page ->
                            val tab = MainTab.entries[page]
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { clip = true },
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                when (tab) {
                                    MainTab.HOME -> HomeScreen(
                                        snapshot = uiState.snapshot,
                                        onOpenShopping = { openMainTab(MainTab.SHOP) },
                                        onOpenStock = { openMainTab(MainTab.STOCK) },
                                        onOpenPlan = { openMainTab(MainTab.PLAN) },
                                        onOpenSpares = { openDetail("spares") },
                                        onOpenDayReview = { openSheet(AppSheet.DayReview) },
                                        onOpenBudgetCorrection = { openSheet(AppSheet.BudgetCorrection) },
                                        onEditItem = { openSheet(AppSheet.EditStock(it)) },
                                        onMarkMeal = viewModel::recordMeal,
                                        onRememberStock = viewModel::rememberStock,
                                        onAssignMeal = { mealType, currentTemplate ->
                                            openSheet(AppSheet.AssignDayMeal(uiState.snapshot.today, mealType, currentTemplate))
                                        },
                                    )

                                    MainTab.PLAN -> PlanScreen(
                                        snapshot = uiState.snapshot,
                                        onAddMeal = { openSheet(AppSheet.AddTemplate) },
                                        onAssignMeal = { date, mealType, template ->
                                            openSheet(AppSheet.AssignDayMeal(date, mealType, template))
                                        },
                                        onRemoveMeal = { date, mealType ->
                                            viewModel.assignMealToDay(date, mealType, null)
                                        },
                                        onEditTemplate = { openSheet(AppSheet.EditTemplate(it)) },
                                        onDeleteTemplate = viewModel::deleteTemplate,
                                        onOpenSettings = { openSheet(AppSheet.Settings) },
                                        onRebuildPlan = { dates -> viewModel.rebuildPlanWindow(dates) },
                                    )

                                    MainTab.STOCK -> StockScreen(
                                        snapshot = uiState.snapshot,
                                        onAddItem = { openSheet(AppSheet.AddPurchase) },
                                        onEditItem = { openSheet(AppSheet.EditStock(it)) },
                                        onLogUsage = { openSheet(AppSheet.LogUsage(it)) },
                                        onClearSampleStock = viewModel::clearSampleStock,
                                    )

                                    MainTab.SHOP -> ShoppingScreen(
                                        snapshot = uiState.snapshot,
                                        onAddItem = { openSheet(AppSheet.AddShopping) },
                                        onEditItem = { openSheet(AppSheet.EditShopping(it)) },
                                        onBuyItem = { openSheet(AppSheet.BuyShopping(it)) },
                                        onToggleItem = { item ->
                                            viewModel.toggleShopping(item) { openSheet(AppSheet.BuyShopping(it)) }
                                        },
                                        onDeleteItem = viewModel::deleteShopping,
                                        onUndoPurchase = viewModel::undoShoppingPurchase,
                                        onBuyAgain = viewModel::buyAgainShoppingItem,
                                    )

                                    MainTab.MORE -> MoreScreen(
                                        snapshot = uiState.snapshot,
                                        onOpenShopping = { openMainTab(MainTab.SHOP) },
                                        onOpenExpenses = { openDetail("expenses") },
                                        onOpenSpares = { openDetail("spares") },
                                        onOpenFoodCatalog = { openDetail("items") },
                                        onOpenSettings = { openSheet(AppSheet.Settings) },
                                        onOpenBudgetCorrection = { openSheet(AppSheet.BudgetCorrection) },
                                        onExport = {
                                            viewModel.exportCsv { csvToShare = it }
                                        },
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onResetPantry = viewModel::resetPantryToEmpty,
                                        onLoadSamplePantry = viewModel::loadSamplePantry,
                                    )
                                }
                            }
                        }
                    }
                }

                // Scrim over main content when detail screen is active
                if (baseScrimAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = baseScrimAlpha))
                    )
                }
            }
        }

        // --- TOP LAYER: Detail Screen Overlay (Spares, Expenses, Food Catalog) ---
        AnimatedContent(
            targetState = detailRoute,
            transitionSpec = {
                if (initialState == null && targetState != null) {
                    Motion.DetailEnterForward togetherWith Motion.DetailExitForward
                } else if (initialState != null && targetState == null) {
                    if (gestureCommitted) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        Motion.DetailEnterBackward togetherWith Motion.DetailExitBackward
                    }
                } else {
                    Motion.tabEnter(true) togetherWith Motion.tabExit(true)
                }
            },
            label = "detailOverlay",
        ) { activeDetail ->
            if (activeDetail != null) {
                val detailCornerRadius = if (isPredictiveBackActive || gestureCommitted) {
                    (28f * predictiveBackProgress.value).coerceAtLeast(0f).dp
                } else {
                    0.dp
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isPredictiveBackActive || gestureCommitted) {
                                translationX = predictiveBackOffset.value
                                scaleX = predictiveBackScale.value
                                scaleY = predictiveBackScale.value
                                shadowElevation = 16.dp.toPx()
                                clip = detailCornerRadius > 0.dp
                                shape = RoundedCornerShape(detailCornerRadius)
                            }
                        },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        saveableStateHolder.SaveableStateProvider(activeDetail) {
                            when (activeDetail) {
                                "items" -> FoodCatalogScreen(
                                    snapshot = uiState.snapshot,
                                    onBack = { detailRoute = null },
                                    onAddItem = { openSheet(AppSheet.AddFoodItem) },
                                    onEditItem = { openSheet(AppSheet.EditFoodItem(it)) },
                                    onDeleteItem = viewModel::deleteFoodCatalogItem,
                                    onImportJson = { importFoodJsonLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                                    onExportJson = { viewModel.exportFoodCatalogJson { foodJsonToShare = it } },
                                    onCopyAiPrompt = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("BudgetMeals AI food catalog instructions", com.budgetmeals.app.data.FoodCatalogJsonCodec.prompt(context)))
                                        viewModel.notify("AI catalog instructions copied")
                                    },
                                )

                                "expenses" -> ExpensesScreen(
                                    snapshot = uiState.snapshot,
                                    onBack = { detailRoute = null },
                                    onAddExpense = { openSheet(AppSheet.AddExpense) },
                                    onEditExpense = { openSheet(AppSheet.EditExpense(it)) },
                                    onDeleteExpense = viewModel::deleteExpense,
                                    onAddCategory = { openSheet(AppSheet.AddCategory) },
                                )

                                "spares" -> SparesScreen(
                                    snapshot = uiState.snapshot,
                                    onBack = { detailRoute = null },
                                    onAddSpares = { openSheet(AppSheet.AddSpares) },
                                    onSaveToday = viewModel::saveTodayToSpares,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    var sheetDismissGuard by remember(sheetGeneration) { mutableStateOf<(() -> Unit)?>(null) }
    val sheetState = key(sheetGeneration) {
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { nextValue ->
                if (nextValue == SheetValue.Hidden && sheetDismissGuard != null && closingSheetGeneration != sheetGeneration) {
                    sheetDismissGuard?.invoke()
                    false
                } else true
            },
        )
    }
    val sheetScope = rememberCoroutineScope()
    val closeSheet: () -> Unit = close@{
        val sheetBeingClosed = sheet ?: return@close
        val generationAtClose = sheetGeneration
        if (closingSheetGeneration == generationAtClose) return@close

        closingSheetGeneration = generationAtClose
        sheetScope.launch {
            try {
                sheetState.hide()
                if (sheetGeneration == generationAtClose && sheet === sheetBeingClosed) {
                    sheetGeneration += 1
                    sheet = null
                }
            } finally {
                if (closingSheetGeneration == generationAtClose) {
                    closingSheetGeneration = -1
                }
            }
        }
    }

    sheet?.let { currentSheet ->
        val generationAtPresentation = sheetGeneration
        ScrollAwareModalBottomSheet(
            onDismissRequest = {
                if (sheetGeneration == generationAtPresentation && sheet === currentSheet) {
                    val dismissGuard = sheetDismissGuard
                    if (dismissGuard != null) dismissGuard() else {
                        sheetGeneration += 1
                        sheet = null
                    }
                }
            },
            sheetState = sheetState,
        ) {
            AnimatedContent(
                targetState = currentSheet,
                transitionSpec = {
                    Motion.SheetContentEnter togetherWith Motion.SheetContentExit
                },
                label = "sheetContentTransition",
            ) { activeSheet ->
                if (activeSheet is AppSheet.QuickAdd) {
                    QuickAddSheetContent(
                        onPurchase = { openSheet(AppSheet.AddPurchase) },
                        onExpense = { openSheet(AppSheet.AddExpense) },
                        onShoppingItem = { openSheet(AppSheet.AddShopping) },
                        onMeal = { openSheet(AppSheet.AddTemplate) },
                    )
                } else {
                    SheetContent(
                        sheet = activeSheet,
                        viewModel = viewModel,
                        snapshot = uiState.snapshot,
                        onDismiss = closeSheet,
                        onDismissGuardChanged = { guard ->
                            if (sheetGeneration == generationAtPresentation && sheet === activeSheet) {
                                sheetDismissGuard = guard
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun isMainTabRoute(route: String) = MainTab.entries.any { it.route == route }

private fun mainTabOrdinal(route: String) = MainTab.entries.firstOrNull { it.route == route }?.ordinal ?: -1

@Composable
private fun tabIcon(tab: MainTab, selected: Boolean) = when (tab) {
    MainTab.HOME -> if (selected) AppIcons.HomeFilled else AppIcons.Home
    MainTab.PLAN -> if (selected) AppIcons.CalendarMonthFilled else AppIcons.CalendarMonth
    MainTab.STOCK -> if (selected) AppIcons.Inventory2Filled else AppIcons.Inventory2
    MainTab.SHOP -> if (selected) AppIcons.ShoppingCartFilled else AppIcons.ShoppingCart
    MainTab.MORE -> if (selected) AppIcons.GridViewFilled else AppIcons.GridView
}
