package com.budgetmeals.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicMealDefaultsTest {
    @Test
    fun unchangedShippedRecipesReceiveAllSevenUnitRepairsWithoutPriceChanges() {
        var repairedCount = 0
        for ((id, original) in originalRecipes) {
            val repaired = requireNotNull(DynamicMealDefaults.repairShippedComponents(id, original))

            assertEquals(DynamicMealDefaults.componentsForTemplate(id, emptyMap()), repaired)
            assertEquals(original.sumOf { it.estimatedCost }, repaired.sumOf { it.estimatedCost }, 0.0001)
            repairedCount += original.zip(repaired).count { (before, after) -> before != after }
            assertTrue(repaired.zip(original).all { (after, before) -> after.id == before.id && after.catalogId == before.catalogId })
        }
        assertEquals(7, repairedCount)
    }

    @Test
    fun anyUserEditedComponentPreventsTheRecipeFromBeingReplaced() {
        val original = originalRecipes.getValue("fuul_combo")
        val first = original.first()
        val editedComponents = listOf(
            first.copy(id = "user-component"),
            first.copy(catalogId = "user-catalog"),
            first.copy(name = "User's ful"),
            first.copy(quantity = 200.0),
            first.copy(unit = "kg"),
            first.copy(costPerUnit = 0.2),
            first.copy(useStock = false),
        )
        for (edited in editedComponents) {
            assertNull(DynamicMealDefaults.repairShippedComponents("fuul_combo", listOf(edited) + original.drop(1)))
        }
        assertNull(DynamicMealDefaults.repairShippedComponents("fuul_combo", original.reversed()))
        assertNull(DynamicMealDefaults.repairShippedComponents("fuul_combo", original.dropLast(1)))
        assertNull(DynamicMealDefaults.repairShippedComponents("fuul_combo", original + first.copy(id = "extra-component")))
    }

    @Test
    fun repairedUnaffectedAndCustomRecipesAreNotRewritten() {
        for (id in originalRecipes.keys + "egg_dinner") {
            assertNull(DynamicMealDefaults.repairShippedComponents(id, DynamicMealDefaults.componentsForTemplate(id, emptyMap())))
        }
        assertNull(DynamicMealDefaults.repairShippedComponents("custom", originalRecipes.getValue("fuul_combo")))
    }

    private fun originalComponent(id: String, catalog: String, name: String, quantity: Double, unit: String, costPerUnit: Double) =
        MealComponent("component_$id", "catalog_$catalog", name, quantity, unit, costPerUnit)

    private val originalRecipes = mapOf(
        "fuul_combo" to listOf(
            originalComponent("fuul", "ful", "Ful medames", 150.0, "g", 0.1),
            originalComponent("falafel", "falafel", "Falafel", 2.0, "piece", 5.0),
            originalComponent("bread", "bread", "Baladi bread", 1.0, "piece", 12.0),
            originalComponent("pickles", "pickles", "Pickles", 1.0, "piece", 3.0),
        ),
        "koshary_day" to listOf(
            originalComponent("rice", "rice", "Rice", 150.0, "g", 0.07),
            originalComponent("koshari", "koshari", "Koshari", 100.0, "g", 0.25),
            originalComponent("fava", "fava", "Split fava", 100.0, "g", 0.15),
            originalComponent("sauce", "tomato_sauce", "Tomato sauce", 50.0, "g", 0.2),
            originalComponent("onions", "onions", "Fried onions", 20.0, "g", 0.4),
        ),
        "falafel_night" to listOf(
            originalComponent("falafel_night", "falafel", "Falafel", 3.0, "piece", 5.0),
            originalComponent("bread_night", "bread", "Baladi bread", 1.0, "piece", 5.0),
            originalComponent("tahina", "tahina", "Tahina", 30.0, "g", 0.17),
            originalComponent("salad_night", "salad", "Salad", 50.0, "g", 0.1),
        ),
        "chips_treat" to listOf(
            originalComponent("chips", "chips", "Chips (Medium)", 1.0, "pack", 15.0),
            originalComponent("soda", "soda", "Soda", 330.0, "ml", 0.021),
        ),
        "ful_sandwich" to listOf(
            originalComponent("ful_sandwich", "ful", "Ful medames", 100.0, "g", 0.1),
            originalComponent("bread_sandwich", "bread", "Baladi bread", 1.0, "piece", 15.0),
            originalComponent("salad_sandwich", "salad", "Salad", 50.0, "g", 0.1),
        ),
    )
}
