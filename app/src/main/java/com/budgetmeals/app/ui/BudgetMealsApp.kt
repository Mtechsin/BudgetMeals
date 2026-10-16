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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.absoluteValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState
import androidx.documentfile.provider.DocumentFile
import com.budgetmeals.app.backup.BackupManager
import com.budgetmeals.app.backup.BackupPreview
import com.budgetmeals.app.backup.SpreadsheetMlWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    data object LogFood : AppSheet
    data class HelpMeChoose(val mealType: MealType? = null) : AppSheet
    data class MealAction(val mealType: MealType, val date: LocalDate) : AppSheet
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
                    animationSpec = tween(durationMillis = Motion.NavigationMs, easing = Motion.Emphasized),
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

    val backupManager = remember { BackupManager(context) }
    val backupState by backupManager.state.collectAsState()

    // Trigger debounced backup after data changes if backup folder is set
    LaunchedEffect(uiState.snapshot) {
        if (backupState.folderUri != null && !backupState.needsFolderReselection) {
            backupManager.scheduleDebouncedBackup()
        }
    }

    // Connect backup folder
    val openBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val folder = DocumentFile.fromTreeUri(context, uri)
            backupManager.setBackupFolder(uri, folder?.name ?: "Selected folder")
            viewModel.notify("Backup folder connected: ${folder?.name ?: "Selected folder"}")
        } catch (e: Exception) {
            viewModel.notify(e.message ?: "Failed to connect backup folder")
        }
    }

    // Restore backup
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val preview = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        backupManager.previewBackup(stream)
                    } ?: error("Could not open backup file")
                }
                pendingRestoreUri = uri
                restorePreview = preview
                showRestoreConfirmDialog = true
            } catch (e: Exception) {
                viewModel.notify("Invalid backup archive: ${e.message}")
            }
        }
    }

    // Save Excel (.xlsx)
    val saveExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        SpreadsheetMlWorkbook.generate(uiState.snapshot, os)
                    } ?: error("Could not write to file")
                }
                viewModel.notify("Excel workbook saved successfully.")
            } catch (e: Exception) {
                viewModel.notify("Failed to save Excel file: ${e.message}")
            }
        }
    }

    // Share Excel (.xlsx)
    val onShareExcel: () -> Unit = {
        coroutineScope.launch {
            try {
                val file = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val exportFile = File(cacheDir, "BudgetMeals_export_${LocalDate.now()}.xlsx")
                    FileOutputStream(exportFile).use { fos ->
                        SpreadsheetMlWorkbook.generate(uiState.snapshot, fos)
                    }
                    exportFile
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share BudgetMeals Excel export"))
            } catch (e: Exception) {
                viewModel.notify("Share failed: ${e.message}")
            }
        }
    }

    // Save CSV Zip
    val saveCsvZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        SpreadsheetMlWorkbook.generateCsvZip(uiState.snapshot, os)
                    } ?: error("Could not write to file")
                }
                viewModel.notify("CSV package saved successfully.")
            } catch (e: Exception) {
                viewModel.notify("Failed to save CSV package: ${e.message}")
            }
        }
    }

    // Share CSV Zip
    val onShareCsvZip: () -> Unit = {
        coroutineScope.launch {
            try {
                val file = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val exportFile = File(cacheDir, "BudgetMeals_tables_${LocalDate.now()}.zip")
                    FileOutputStream(exportFile).use { fos ->
                        SpreadsheetMlWorkbook.generateCsvZip(uiState.snapshot, fos)
                    }
                    exportFile
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share BudgetMeals CSV package"))
            } catch (e: Exception) {
                viewModel.notify("Share failed: ${e.message}")
            }
        }
    }

    // Manual backup action
    val onBackupNow: () -> Unit = {
        coroutineScope.launch {
            val result = backupManager.performManualBackup()
            result.onSuccess { msg ->
                viewModel.notify(msg)
            }.onFailure { err ->
                viewModel.notify(err.message ?: "Backup failed")
            }
        }
    }

    LaunchedEffect(dayReviewRequestKey, sheet, uiState.isLoading) {
        if (!uiState.isLoading && dayReviewRequestKey > 0L && dayReviewRequestKey != lastHandledDayReviewRequest && sheet == null) {
            openSheet(AppSheet.DayReview)
            lastHandledDayReviewRequest = dayReviewRequestKey
        }
    }

    LaunchedEffect(uiState.snapshot.today) {
        while (true) {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val waitMillis = Duration.between(now, nextMidnight).toMillis() + 1_000L
            delay(waitMillis.coerceIn(1_000L, 60_000L))
            if (LocalDate.now() != uiState.snapshot.today) viewModel.refresh()
        }
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

    LaunchedEffect(pagerState.currentPage) {
        val settledTab = MainTab.entries.getOrNull(pagerState.currentPage) ?: MainTab.HOME
        if (mainRoute != settledTab.route) {
            mainRoute = settledTab.route
        }
    }

    BackHandler(enabled = detailRoute == null && sheet == null && (pagerState.currentPage != 0 || mainRoute != MainTab.HOME.route)) {
        openMainTab(MainTab.HOME)
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage(message)
    }

    LaunchedEffect(
        uiState.isLoading,
        uiState.snapshot.settings.remindersEnabled,
        uiState.snapshot.settings.shoppingDay,
        uiState.snapshot.settings.kosharyDay,
    ) {
        if (!uiState.isLoading) ReminderScheduler.sync(context, uiState.snapshot.settings)
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
    val activeTab = MainTab.entries.getOrElse(
        if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage,
    ) { MainTab.HOME }
    val mainFab = when (activeTab) {
        MainTab.HOME -> null
        MainTab.PLAN -> FabAction("New meal", AppSheet.AddTemplate)
        MainTab.STOCK -> FabAction("Purchase", AppSheet.AddPurchase)
        MainTab.SHOP -> FabAction("Add item", AppSheet.AddShopping)
        MainTab.MORE -> null
    }
    val detailMotionDistance = with(LocalDensity.current) { 30.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { detailContentWidth = it.width }
    ) {
        // --- BASE LAYER: Scaffold with Main Tabs & Navigation Bar ---
        val hasDetail = detailRoute != null
        val animatedDetailProgress by animateFloatAsState(
            targetValue = if (hasDetail) 1f else 0f,
            animationSpec = if (gestureCommitted) snap() else tween(durationMillis = Motion.NavigationMs, easing = Motion.Emphasized),
            label = "baseLayerRecede",
        )
        // A committed gesture has already restored the main screen. Keep that final
        // pose while clearing the overlay instead of starting a second scale animation.
        val baseProgress = when {
            isPredictiveBackActive -> 1f - predictiveBackProgress.value
            gestureCommitted -> 0f
            else -> animatedDetailProgress
        }
        val baseScale = 1f - 0.06f * baseProgress
        val baseCornerRadius = (20f * baseProgress).dp
        val baseScrimAlpha = 0.25f * baseProgress

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
                AnimatedActionButton(
                    action = mainFab,
                    visible = isMainRoute && !uiState.isLoading && sheet == null,
                    enabled = !pagerState.isScrollInProgress,
                    onAction = openSheet,
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = isMainRoute,
                    enter = slideInVertically(tween(Motion.MediumMs, easing = Motion.EmphasizedDecelerate)) { it } + fadeIn(tween(Motion.ShortMs)),
                    exit = slideOutVertically(tween(Motion.MediumMs, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(Motion.ShortMs)),
                ) {
                    val currentTab = MainTab.entries.getOrElse(
                        if (pagerState.isScrollInProgress) {
                            pagerState.targetPage
                        } else {
                            pagerState.currentPage
                        }
                    ) { MainTab.HOME }

                    InteractiveBottomBar(
                        currentTab = currentTab,
                        scrollProgress = {
                            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, (MainTab.entries.size - 1).toFloat())
                        },
                        badgeCount = uiState.snapshot.openShoppingCount,
                        onTabSelected = { openMainTab(it) },
                    )
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
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
                            key = { page -> MainTab.entries[page].route },
                        ) { page ->
                            val tab = MainTab.entries[page]
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                        alpha = 1f - 0.15f * pageOffset.coerceIn(0f, 1f)
                                        clip = true
                                    },
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                when (tab) {
                                    MainTab.HOME -> HomeScreen(
                                        snapshot = uiState.snapshot,
                                        onLogFood = { openSheet(AppSheet.LogFood) },
                                        onHelpMeChoose = { mealType -> openSheet(AppSheet.HelpMeChoose(mealType)) },
                                        onOpenMealAction = { mealType, date -> openSheet(AppSheet.MealAction(mealType, date)) },
                                        onOpenSpending = { openDetail("spending") },
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
                                        onEditItem = { openSheet(AppSheet.EditStock(it)) },
                                        onLogUsage = { openSheet(AppSheet.LogUsage(it)) },
                                        onClearSampleStock = viewModel::clearSampleStock,
                                    )

                                    MainTab.SHOP -> ShoppingScreen(
                                        snapshot = uiState.snapshot,
                                        onEditItem = { openSheet(AppSheet.EditShopping(it)) },
                                        onBuyItem = { openSheet(AppSheet.BuyShopping(it)) },
                                        onToggleItem = { item ->
                                            viewModel.toggleShopping(item) { openSheet(AppSheet.BuyShopping(it)) }
                                        },
                                        onDeleteItem = viewModel::deleteShopping,
                                        onUndoPurchase = viewModel::undoShoppingPurchase,
                                        onBuyAgain = viewModel::buyAgainShoppingItem,
                                        onAddItem = { openSheet(AppSheet.AddShopping) },
                                    )

                                    MainTab.MORE -> MoreScreen(
                                        snapshot = uiState.snapshot,
                                        onOpenShopping = { openMainTab(MainTab.SHOP) },
                                        onOpenExpenses = { openDetail("expenses") },
                                        onOpenSpares = { openDetail("spares") },
                                        onOpenFoodCatalog = { openDetail("items") },
                                        onOpenSettings = { openSheet(AppSheet.Settings) },
                                        onOpenBudgetCorrection = { openSheet(AppSheet.BudgetCorrection) },
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onResetPantry = viewModel::resetPantryToEmpty,
                                        onOpenFoodSpending = { openDetail("spending") },
                                        backupFolderName = backupState.folderDisplayName,
                                        lastBackupTimeText = backupState.lastBackupTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                                        isBackupPermissionLost = backupState.needsFolderReselection,
                                        onChooseBackupFolder = { openBackupFolderLauncher.launch(null) },
                                        onBackupNow = onBackupNow,
                                        onRestoreBackup = {
                                            restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                        },
                                        onSaveExcel = {
                                            saveExcelLauncher.launch("BudgetMeals_${LocalDate.now()}.xlsx")
                                        },
                                        onShareExcel = onShareExcel,
                                        onSaveCsvZip = {
                                            saveCsvZipLauncher.launch("BudgetMeals_tables_${LocalDate.now()}.zip")
                                        },
                                        onShareCsvZip = onShareCsvZip,
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
                    Motion.sharedAxisX(forward = true, distancePx = detailMotionDistance).using(null)
                } else if (initialState != null && targetState == null) {
                    if (gestureCommitted) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        Motion.sharedAxisX(forward = false, distancePx = detailMotionDistance).using(null)
                    }
                } else {
                    Motion.fadeThrough().using(null)
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
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.background,
                        floatingActionButton = {
                            val detailFab = when (activeDetail) {
                                "items" -> FabAction("Add food", AppSheet.AddFoodItem)
                                "expenses" -> FabAction("Add expense", AppSheet.AddExpense)
                                "spares" -> FabAction("Add money", AppSheet.AddSpares)
                                else -> null
                            }
                            AnimatedActionButton(
                                action = detailFab,
                                visible = detailRoute == activeDetail && sheet == null,
                                onAction = openSheet,
                            )
                        },
                    ) { detailPadding ->
                        Box(Modifier.fillMaxSize().padding(detailPadding)) {
                            saveableStateHolder.SaveableStateProvider(activeDetail) {
                                when (activeDetail) {
                                    "items" -> FoodCatalogScreen(
                                        snapshot = uiState.snapshot,
                                        onBack = { detailRoute = null },
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
                                        onEditExpense = { openSheet(AppSheet.EditExpense(it)) },
                                        onDeleteExpense = viewModel::deleteExpense,
                                        onAddCategory = { openSheet(AppSheet.AddCategory) },
                                        onEditPurchase = { openSheet(AppSheet.EditStock(it)) },
                                        onMatchSpending = { openSheet(AppSheet.BudgetCorrection) },
                                        onOpenSpares = { openDetail("spares") },
                                    )

                                    "spares" -> SparesScreen(
                                        snapshot = uiState.snapshot,
                                        onBack = { detailRoute = null },
                                        onSaveToday = viewModel::saveTodayToSpares,
                                    )

                                    "spending" -> FoodSpendingScreen(
                                        snapshot = uiState.snapshot,
                                        onBack = { detailRoute = null },
                                        onEditExpense = { openSheet(AppSheet.EditExpense(it)) },
                                        onEditPurchase = { openSheet(AppSheet.EditStock(it)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val halfScreenHeightPx = remember(density, configuration) {
        with(density) { configuration.screenHeightDp.dp.toPx() * 0.5f }
    }
    val dragSlopPx = remember(density) {
        with(density) { 8.dp.toPx() }
    }
    var sheetContentHeightPx by remember(sheetGeneration) { mutableFloatStateOf(0f) }
    var sheetStateRef by remember(sheetGeneration) { mutableStateOf<androidx.compose.material3.SheetState?>(null) }
    var sheetDismissGuard by remember(sheetGeneration) { mutableStateOf<(() -> Unit)?>(null) }
    val sheetBehavior = remember(sheetGeneration) { SheetScrollBehavior() }
    val sheetState = key(sheetGeneration) {
        val state = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { nextValue ->
                if (nextValue == SheetValue.Hidden && closingSheetGeneration != sheetGeneration) {
                    if (sheetDismissGuard != null) {
                        sheetDismissGuard?.invoke()
                        false
                    } else {
                        val currentOffset = try {
                            sheetStateRef?.requireOffset() ?: 0f
                        } catch (_: Exception) {
                            0f
                        }
                        val isFingerDrag = sheetBehavior.isRecentPhysicalDragDown() && currentOffset > dragSlopPx
                        if (isFingerDrag) {
                            val dismissThreshold = if (sheetContentHeightPx > 0f) {
                                minOf(halfScreenHeightPx, sheetContentHeightPx * 0.5f)
                            } else {
                                halfScreenHeightPx
                            }
                            currentOffset >= dismissThreshold
                        } else {
                            // Predictive back gesture, hardware back, scrim tap, or programmatic hide
                            true
                        }
                    }
                } else true
            },
        )
        sheetStateRef = state
        state
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
            behavior = sheetBehavior,
            modifier = Modifier.onSizeChanged { size ->
                sheetContentHeightPx = size.height.toFloat()
            },
        ) {
            AnimatedContent(
                targetState = currentSheet,
                transitionSpec = {
                    (Motion.SheetContentEnter togetherWith Motion.SheetContentExit)
                        .using(SizeTransform(clip = false) { _, _ -> Motion.CardExpandTween })
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
                        onDismiss = {
                            if (sheetGeneration == generationAtPresentation && sheet === activeSheet) closeSheet()
                        },
                        onOpenSheet = openSheet,
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

    if (showRestoreConfirmDialog && restorePreview != null && pendingRestoreUri != null) {
        val preview = restorePreview!!
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
                restorePreview = null
            },
            title = { Text("Restore backup archive?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Archive export date: ${preview.exportedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                    Text("Contains: ${preview.expensesCount} expenses, ${preview.stockCount} stock items, ${preview.mealsCount} meals, ${preview.plansCount} day plans.")
                    Text(
                        "Your current database will be safely backed up to a recovery copy before replacement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Confirm restore",
                    onClick = {
                        val uri = pendingRestoreUri
                        showRestoreConfirmDialog = false
                        if (uri != null) {
                            coroutineScope.launch {
                                try {
                                    val success = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(uri)?.use { stream ->
                                            backupManager.restoreBackup(stream)
                                        } ?: false
                                    }
                                    if (success) {
                                        viewModel.refresh()
                                        viewModel.notify("Data restored successfully.")
                                    } else {
                                        viewModel.notify("Restore failed. Previous data was preserved.")
                                    }
                                } catch (e: Exception) {
                                    viewModel.notify("Restore failed: ${e.message ?: "Invalid backup"}")
                                } finally {
                                    pendingRestoreUri = null
                                    restorePreview = null
                                }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        restorePreview = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
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

@Composable
private fun InteractiveBottomBar(
    currentTab: MainTab,
    scrollProgress: () -> Float,
    badgeCount: Int,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardSurfaceColor(),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.5f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp),
        ) {
            val tabCount = MainTab.entries.size
            val tabWidth = maxWidth / tabCount
            val pillWidth = 56.dp
            val pillHeight = 32.dp

            // Read progress during placement so moving the indicator doesn't recompose labels.
            Box(
                modifier = Modifier
                    .offset {
                        val position = scrollProgress().coerceIn(0f, (tabCount - 1).toFloat())
                        IntOffset(
                            x = (tabWidth.toPx() * position + (tabWidth - pillWidth).toPx() / 2).roundToInt(),
                            y = 6.dp.roundToPx(),
                        )
                    }
                    .size(width = pillWidth, height = pillHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ),
            )

            // Tabs row
            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val badge = if (tab == MainTab.SHOP) badgeCount else 0
                    InteractiveTabItem(
                        tab = tab,
                        selected = selected,
                        badgeCount = badge,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveTabItem(
    tab: MainTab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            selected -> 1.04f
            else -> 1.0f
        },
        animationSpec = Motion.MicroSpring,
        label = "tabItemScale",
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.ColorTween,
        label = "tabItemIconTint",
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        },
        animationSpec = Motion.ColorTween,
        label = "tabItemLabelColor",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) {
                            Text(
                                if (badgeCount > 99) "99+" else badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
            ) {
                Crossfade(
                    targetState = selected,
                    animationSpec = Motion.FadeTween,
                    label = "tabIconCrossfade",
                ) { isSel ->
                    Icon(
                        imageVector = tabIcon(tab, selected = isSel),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
        )
    }
}
