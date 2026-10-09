package com.budgetmeals.app.ui

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.BudgetStorageRule
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.theme.BudgetMealsTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class MealEditorUiTest {
    private val storage = BudgetStorageRule()
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Close the host Activity (and its ViewModelStore) before the storage rule removes its
    // uniquely prefixed database and preferences.
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(storage).around(composeRule)

    private lateinit var viewModel: BudgetViewModel

    @Before
    fun createIsolatedViewModel() {
        val isolatedApplication = IsolatedMealEditorApplication().apply {
            initialize(storage.context)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(BudgetViewModel::class.java))
                return BudgetViewModel(isolatedApplication) as T
            }
        }
        composeRule.runOnUiThread {
            viewModel = ViewModelProvider(composeRule.activity, factory)[BudgetViewModel::class.java]
        }
    }

    @Test
    fun catalogSearchResultShowsFoodNameAndCanBeAdded() {
        val lentils = FoodCatalogItem(
            id = "lentils",
            name = "Red lentils",
            defaultCostPerPortion = 4.0,
        )
        setEditor(existing = null, snapshot = AppSnapshot(foodCatalog = listOf(lentils)))

        composeRule.onNodeWithTag("catalog_search").performScrollTo().performTextInput("red")

        composeRule.onNodeWithTag("catalog_result_lentils").performScrollTo().assertExists()
        composeRule.onNodeWithText("Red lentils").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_result_lentils").performScrollTo().performClick()
        composeRule.onNodeWithText("Red lentils").assertExists()
    }

    @Test
    fun invalidQuantityBlocksSaveAndDecimalQuantityCanBeSaved() {
        val component = MealComponent(
            id = "rice-component",
            name = "Rice",
            quantity = 1.0,
            unit = "cup",
            costPerUnit = 10.0,
            useStock = false,
        )
        val meal = meal(id = "rice-meal", name = "Rice bowl", total = 10.0, components = listOf(component))
        val dismissed = AtomicBoolean(false)
        setEditor(meal, AppSnapshot(), onDismiss = { dismissed.set(true) })

        val quantity = composeRule.onNodeWithTag("ingredient_quantity_rice-component").performScrollTo()
        quantity.performTextReplacement("0")
        composeRule.onNodeWithText("Enter a quantity greater than zero").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save_meal").assertIsNotEnabled()

        quantity.performTextReplacement("0.5")
        quantity.assert(hasText("0.5"))
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()
        composeRule.onNodeWithTag("save_meal").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }

        val saved = storage.openRepository().loadSnapshot().templates.single { it.id == meal.id }
        assertEquals(0.5, saved.components.single().quantity, 0.0001)
        assertEquals(5.0, saved.cost, 0.0001)
    }

    @Test
    fun quantityButtonsKeepWholePortionsWholeAtTheLowerLimit() {
        val component = MealComponent(id = "whole-stepper", name = "Eggs", quantity = 2.0, unit = "piece", costPerUnit = 5.0)
        val meal = meal("whole-stepper-meal", "Egg plate", 10.0, listOf(component))
        val dismissed = AtomicBoolean(false)
        setEditor(meal, AppSnapshot(), onDismiss = { dismissed.set(true) })

        val quantity = composeRule.onNodeWithTag("ingredient_quantity_whole-stepper").performScrollTo()
        val minus = composeRule.onNodeWithContentDescription("Decrease Eggs quantity")
        val plus = composeRule.onNodeWithContentDescription("Increase Eggs quantity")
        minus.performClick()
        quantity.assert(hasText("1"))
        minus.assertIsNotEnabled()
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()

        plus.performClick()
        quantity.assert(hasText("2"))
        minus.assertIsEnabled()
        plus.performClick()
        quantity.assert(hasText("3"))
        composeRule.onNodeWithTag("save_meal").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }

        val saved = storage.openRepository().loadSnapshot().templates.single { it.id == meal.id }
        assertEquals(3.0, saved.components.single().quantity, 0.000001)
        assertEquals(15.0, saved.cost, 0.000001)
    }

    @Test
    fun quantityButtonsPreserveExplicitFractionsWithoutChangingStepSize() {
        val component = MealComponent(id = "fraction-stepper", name = "Milk", quantity = 0.5, unit = "cup", costPerUnit = 10.0)
        setEditor(meal("fraction-stepper-meal", "Milk", 5.0, listOf(component)), AppSnapshot())

        val quantity = composeRule.onNodeWithTag("ingredient_quantity_fraction-stepper").performScrollTo()
        val minus = composeRule.onNodeWithContentDescription("Decrease Milk quantity")
        val plus = composeRule.onNodeWithContentDescription("Increase Milk quantity")
        minus.assertIsNotEnabled()
        plus.performClick()
        quantity.assert(hasText("1.5"))
        minus.performClick()
        quantity.assert(hasText("0.5"))
        minus.assertIsNotEnabled()

        quantity.performTextReplacement("1.005")
        minus.performClick()
        quantity.assert(hasText("0.005"))
        minus.assertIsNotEnabled()
        plus.performClick()
        quantity.assert(hasText("1.005"))
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()
    }

    @Test
    fun existingManualMealTotalRemainsEditableAlongsidePricedIngredients() {
        val component = MealComponent(
            id = "priced-component",
            name = "Beans",
            quantity = 2.0,
            unit = "portion",
            costPerUnit = 5.0,
            useStock = false,
        )
        val meal = meal(id = "manual-meal", name = "Bean bowl", total = 27.0, components = listOf(component))
        val dismissed = AtomicBoolean(false)
        setEditor(meal, AppSnapshot(), onDismiss = { dismissed.set(true) })

        val totalField = composeRule.onNodeWithTag("manual_price").performScrollTo()
        totalField.assert(hasText("27"))
        totalField.performTextReplacement("32.5")
        composeRule.onNodeWithTag("save_meal").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }

        val saved = storage.openRepository().loadSnapshot().templates.single { it.id == meal.id }
        assertEquals(32.5, saved.cost, 0.0001)
        assertEquals(10.0, saved.components.single().estimatedCost, 0.0001)
    }

    @Test
    fun failedSaveKeepsEditsVisibleAndLeavesOriginalMealUnchanged() {
        val meal = meal(id = "blocked-save-meal", name = "Original name", total = 12.0)
        val database = storage.openDatabase()
        database.upsertTemplate(meal)
        database.writableDatabase.execSQL(
            """CREATE TRIGGER fail_meal_editor_save BEFORE INSERT ON meal_templates
                WHEN NEW.id = '${meal.id}' BEGIN SELECT RAISE(ABORT, 'test failure'); END""",
        )
        setEditor(meal, AppSnapshot())

        composeRule.onNodeWithTag("meal_name").performScrollTo().performTextReplacement("Edited name")
        composeRule.onNodeWithTag("save_meal").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("save this meal", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("meal_name").assert(hasText("Edited name"))
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()
        val persisted = storage.openRepository().loadSnapshot().templates.single { it.id == meal.id }
        assertEquals("Original name", persisted.name)
    }

    @Test
    fun cancellingDeleteConfirmationKeepsMealEditorOpen() {
        val meal = meal(id = "delete-meal", name = "Chickpea stew", total = 12.0)
        val dismissed = AtomicBoolean(false)
        setEditor(meal, AppSnapshot(), onDismiss = { dismissed.set(true) })

        composeRule.onNodeWithTag("delete_meal").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete Chickpea stew?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_delete").performClick()

        composeRule.onNodeWithText("Delete Chickpea stew?").assertDoesNotExist()
        composeRule.onNodeWithTag("meal_name").performScrollTo().assertIsDisplayed()
        assertFalse("Canceling deletion must leave the editor open", dismissed.get())
    }

    @Test
    fun coreEditorControlsRemainAvailableAtLargeFontInNarrowLayout() {
        val component = MealComponent(id = "narrow", name = "A long ingredient name", quantity = 1.0, unit = "tablespoon", costPerUnit = 2.0)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    BudgetMealsTheme(darkTheme = true) {
                        TemplateFormSheet(
                            existing = meal("narrow-meal", "Narrow meal", 2.0, listOf(component)),
                            snapshot = AppSnapshot(),
                            viewModel = viewModel,
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("meal_name").assertIsDisplayed()
        composeRule.onNodeWithTag("save_meal").assertIsDisplayed()
        val quantity = composeRule.onNodeWithTag("ingredient_quantity_narrow").performScrollTo()
        quantity.assertIsDisplayed().performTextReplacement("2.5")
        quantity.assert(hasText("2.5"))
        val editableWidthDp = quantity.fetchSemanticsNode().boundsInRoot.width /
            composeRule.activity.resources.displayMetrics.density
        assertTrue("Long units must leave room to edit quantity", editableWidthDp >= 40f)
        composeRule.onNodeWithText("Manual total").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun externalDismissGuardPromptsAndKeepEditingRetainsChanges() {
        val meal = meal(id = "guarded-meal", name = "Original name", total = 12.0)
        val dismissed = AtomicBoolean(false)
        val dismissGuard = AtomicReference<(() -> Unit)?>(null)
        setEditor(
            existing = meal,
            snapshot = AppSnapshot(),
            onDismiss = { dismissed.set(true) },
            onDismissGuardChanged = { dismissGuard.set(it) },
        )

        composeRule.onNodeWithTag("meal_name").performScrollTo().performTextReplacement("Edited name")
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissGuard.get() != null }
        composeRule.runOnIdle { dismissGuard.get()?.invoke() }
        composeRule.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()

        composeRule.onNodeWithTag("meal_name").assert(hasText("Edited name"))
        assertFalse("External dismissal should remain blocked after choosing Keep editing", dismissed.get())
    }

    @Test
    fun existingEditorRestoresNameAndDecimalIngredientQuantity() {
        val component = MealComponent(
            id = "restored-component",
            name = "Oats",
            quantity = 1.0,
            unit = "cup",
            costPerUnit = 8.0,
            useStock = false,
        )
        val meal = meal(id = "restored-meal", name = "Oats", total = 8.0, components = listOf(component))
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            BudgetMealsTheme(darkTheme = true) {
                TemplateFormSheet(meal, AppSnapshot(), viewModel, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("meal_name").performScrollTo().performTextReplacement("Restored oats")
        composeRule.onNodeWithTag("ingredient_quantity_restored-component").performScrollTo().performTextReplacement("0.5")
        composeRule.onNodeWithText("Any day").performScrollTo().assertIsSelected()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("meal_name").assert(hasText("Restored oats"))
        composeRule.onNodeWithTag("ingredient_quantity_restored-component").performScrollTo().assert(hasText("0.5"))
        composeRule.onNodeWithText("Any day").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()
    }

    @Test
    fun multipleIngredientCardsStayCompactAndOnlyExpandedCardShowsDetails() {
        val components = sixIngredients()
        setPhoneSizedEditor(components)

        composeRule.onAllNodesWithText("Use pantry stock", useUnmergedTree = true).assertCountEquals(0)
        components.forEach { component ->
            val card = composeRule.onNodeWithTag("ingredient_card_${component.id}").performScrollTo()
            val heightDp = card.fetchSemanticsNode().boundsInRoot.height /
                composeRule.activity.resources.displayMetrics.density
            assertTrue("Collapsed ${component.name} card should stay compact, was ${heightDp}dp", heightDp <= 136f)
        }

        val selected = components[2]
        composeRule.onNodeWithTag("ingredient_options_${selected.id}").performScrollTo().performClick()

        composeRule.onAllNodesWithText("Use pantry stock", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("${components[0].name} cost per ${components[0].unit} in Egyptian pounds").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("${selected.name} cost per ${selected.unit} in Egyptian pounds").assertExists()
        composeRule.onNodeWithContentDescription("${selected.name} quantity unit").assertExists()
    }

    @Test
    fun compactIngredientCardsKeepQuantityEditableInline() {
        val components = sixIngredients()
        setPhoneSizedEditor(components)

        val firstQuantity = composeRule.onNodeWithTag("ingredient_quantity_${components.first().id}").performScrollTo()
        firstQuantity.performTextReplacement("2.25")
        firstQuantity.assert(hasText("2.25"))
        composeRule.onNodeWithTag("ingredient_quantity_${components[1].id}").performScrollTo().assert(hasText("1"))
        composeRule.onNodeWithTag("save_meal").assertIsEnabled()
    }

    @Test
    fun downwardFlingThatReturnsBodyToTopDoesNotDismissUntilFreshPull() {
        val components = sixIngredients()
        val dismissed = AtomicBoolean(false)
        val sheetState = AtomicReference<SheetState?>(null)
        setModalEditor(components, dismissed, sheetState)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sheetState.get()?.currentValue == SheetValue.Expanded
        }
        val expandedOffset = requireNotNull(sheetState.get()).requireOffset()
        val body = composeRule.onNodeWithTag("meal_editor_scroll")
        body.performTouchInput {
            val middleX = width * 0.5f
            swipe(
                start = androidx.compose.ui.geometry.Offset(middleX, height * 0.82f),
                end = androidx.compose.ui.geometry.Offset(middleX, height * 0.18f),
                durationMillis = 100,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { bodyScrollValue() > 0f }
        assertTrue("The editor body must be scrolled before testing the return fling", bodyScrollValue() > 0f)

        body.performTouchInput {
            val middleX = width * 0.5f
            swipe(
                start = androidx.compose.ui.geometry.Offset(middleX, height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(middleX, height * 0.88f),
                durationMillis = 65,
            )
        }
        composeRule.waitForIdle()
        assertEquals("The fast return gesture should reach the content top", 0f, bodyScrollValue(), 1f)
        assertEquals(SheetValue.Expanded, requireNotNull(sheetState.get()).currentValue)
        assertEquals(SheetValue.Expanded, requireNotNull(sheetState.get()).targetValue)
        assertEquals("The sheet should not move during the content-return gesture", expandedOffset, requireNotNull(sheetState.get()).requireOffset(), 1f)
        assertFalse("A content-return fling must not dismiss the editor", dismissed.get())

        body.performTouchInput {
            val middleX = width * 0.5f
            swipe(
                start = androidx.compose.ui.geometry.Offset(middleX, height * 0.2f),
                end = androidx.compose.ui.geometry.Offset(middleX, height * 0.9f),
                durationMillis = 100,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }
        assertEquals(SheetValue.Hidden, requireNotNull(sheetState.get()).currentValue)
    }

    @Test
    fun headerAndHandleCannotDismissWhileBodyIsScrolledButCloseButtonStillWorks() {
        val components = sixIngredients()
        val dismissed = AtomicBoolean(false)
        val sheetState = AtomicReference<SheetState?>(null)
        setModalEditor(components, dismissed, sheetState)

        val body = composeRule.onNodeWithTag("meal_editor_scroll")
        body.performTouchInput {
            val middleX = width * 0.5f
            swipe(
                start = androidx.compose.ui.geometry.Offset(middleX, height * 0.82f),
                end = androidx.compose.ui.geometry.Offset(middleX, height * 0.18f),
                durationMillis = 100,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { bodyScrollValue() > 0f }
        val scrolledValue = bodyScrollValue()
        val expandedOffset = requireNotNull(sheetState.get()).requireOffset()
        val dragDistance = body.fetchSemanticsNode().boundsInRoot.height * 0.6f

        composeRule.onNodeWithTag("meal_editor_header").performTouchInput {
            val middleX = width * 0.5f
            swipe(
                start = androidx.compose.ui.geometry.Offset(middleX, height * 0.2f),
                end = androidx.compose.ui.geometry.Offset(middleX, height * 0.2f + dragDistance),
                durationMillis = 90,
            )
        }
        composeRule.onNodeWithTag("sheet_drag_handle", useUnmergedTree = true).performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.2f),
                end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.2f + dragDistance),
                durationMillis = 90,
            )
        }
        composeRule.waitForIdle()
        assertTrue("Header and handle pulls must leave the body scrolled", bodyScrollValue() > 0f)
        assertEquals(scrolledValue, bodyScrollValue(), 1f)
        assertEquals(SheetValue.Expanded, requireNotNull(sheetState.get()).currentValue)
        assertEquals(SheetValue.Expanded, requireNotNull(sheetState.get()).targetValue)
        assertEquals("Header and handle pulls must not move the sheet", expandedOffset, requireNotNull(sheetState.get()).requireOffset(), 1f)
        assertFalse("Header and handle pulls must not dismiss the editor", dismissed.get())

        composeRule.onNodeWithContentDescription("Close editor").performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }
    }

    private fun setEditor(
        existing: MealTemplate?,
        snapshot: AppSnapshot,
        onDismiss: () -> Unit = {},
        onDismissGuardChanged: ((() -> Unit)?) -> Unit = {},
    ) {
        composeRule.setContent {
            BudgetMealsTheme(darkTheme = true) {
                TemplateFormSheet(existing, snapshot, viewModel, onDismiss, onDismissGuardChanged)
            }
        }
    }

    private fun setPhoneSizedEditor(components: List<MealComponent>) {
        val meal = meal(
            id = "many-ingredients-meal",
            name = "Many ingredient meal",
            total = components.sumOf { it.estimatedCost },
            components = components,
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                BudgetMealsTheme(darkTheme = true) {
                    Box(Modifier.width(360.dp).height(760.dp)) {
                        TemplateFormSheet(meal, AppSnapshot(), viewModel, onDismiss = {})
                    }
                }
            }
        }
    }

    private fun setModalEditor(
        components: List<MealComponent>,
        dismissed: AtomicBoolean,
        state: AtomicReference<SheetState?>,
    ) {
        val meal = meal(
            id = "gesture-meal",
            name = "Gesture meal",
            total = components.sumOf { it.estimatedCost },
            components = components,
        )
        composeRule.setContent {
            val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            SideEffect { state.set(modalState) }
            BudgetMealsTheme(darkTheme = true) {
                ScrollAwareModalBottomSheet(
                    onDismissRequest = { dismissed.set(true) },
                    sheetState = modalState,
                ) {
                    TemplateFormSheet(meal, AppSnapshot(), viewModel, onDismiss = { dismissed.set(true) })
                }
            }
        }
    }

    private fun bodyScrollValue(): Float = composeRule
        .onNodeWithTag("meal_editor_scroll")
        .fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]
        .value()

    private fun sixIngredients() = (0 until 6).map { index ->
        MealComponent(
            id = "compact-ingredient-$index",
            name = "Ingredient ${index + 1}",
            quantity = 1.0,
            unit = "g",
            costPerUnit = index + 2.0,
            useStock = false,
        )
    }

    private fun meal(
        id: String,
        name: String,
        total: Double,
        components: List<MealComponent> = emptyList(),
    ) = MealTemplate(
        id = id,
        name = name,
        mealType = MealType.LUNCH,
        cost = total,
        components = components,
    )
}

/** Provides an Application for AndroidViewModel while keeping all storage in the test prefix. */
private class IsolatedMealEditorApplication : Application() {
    fun initialize(context: Context) {
        // BudgetStorageRule already provides a uniquely prefixed Context; attach it directly so
        // its after() cleanup removes precisely the database and preferences used by the ViewModel.
        attachBaseContext(context)
    }

    override fun getApplicationContext(): Context = this
}
