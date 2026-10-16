package com.budgetmeals.app.state

import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionTest {
    @Test
    fun hugeFiniteQuantitiesStayFiniteOrFailWithoutOverflow() {
        val eggs = FoodCatalogItem(name = "Eggs", stockUnit = "carton (30)", portionUnit = "egg", portionsPerStockUnit = 30.0)

        assertEquals(Double.MAX_VALUE, requireNotNull(Units.convert(Double.MAX_VALUE, "carton", "carton (30)", eggs)), 0.0)
        assertEquals(Double.MAX_VALUE / 1000.0, requireNotNull(Units.convert(Double.MAX_VALUE, "g", "kg", null)), 1e290)
        assertNull(Units.convert(Double.MAX_VALUE, "kg", "g", null))
        assertNull(Units.convert(Double.MAX_VALUE, "carton", "egg", eggs))
    }

    @Test
    fun genericPiecesDoNotInventMassOrVolumeConversions() {
        val rice = FoodCatalogItem(name = "Rice", stockUnit = "kg", portionUnit = "cup", portionsPerStockUnit = 5.0)
        val milk = FoodCatalogItem(name = "Milk", stockUnit = "bottle (1l)", portionUnit = "cup", portionsPerStockUnit = 4.0)

        assertNull(Units.convert(1.0, "piece", "g", rice))
        assertNull(Units.convert(1.0, "piece", "ml", milk))
    }

    @Test
    fun differentlySizedPackagesCannotConvertByContainerNameAlone() {
        assertNull(Units.convert(1.0, "jar (380g)", "jar (800g)", null))
        assertNull(Units.convert(1.0, "jar (380g)", "jar", null))
        assertEquals(2.0, requireNotNull(Units.convert(2.0, "jar (380g)", "jar (380g)", null)), 0.0)
    }

    @Test
    fun unsizedPackageUsesItsStatedPhysicalPortionUnit() {
        val jam = FoodCatalogItem(name = "Jam", stockUnit = "jar", portionUnit = "g", portionsPerStockUnit = 680.0)

        assertEquals("g", jam.baseUnit)
        assertEquals(680.0, requireNotNull(Units.convert(1.0, "jar", "g", jam)), 0.001)
        assertEquals(0.5, requireNotNull(Units.convert(340.0, "g", "jar", jam)), 0.001)
    }

    @Test
    fun spoonCatalogUsesTheWholeKilogramAsItsPurchaseUnit() {
        val cheese = FoodCatalogItem(name = "Cheese", stockUnit = "kg", portionUnit = "spoon", portionsPerStockUnit = 50.0)

        assertEquals(1_000.0, cheese.baseUnitsPerStockUnit, 0.001)
        assertEquals(20.0, cheese.baseUnitsPerPortion, 0.001)
    }

    @Test
    fun packageLabelsDoNotMatchThroughText() {
        assertFalse(BudgetMath.areStockUnitsMatching("g", "jar (380g)"))
        assertFalse(BudgetMath.areStockUnitsMatching("g", "tub (500g)"))
        assertFalse(BudgetMath.areStockUnitsMatching("ml", "bottle (700ml)"))
        assertNull(BudgetRepository.convertStockQuantity(650.0, "g", "jar (380g)", null))
        assertNull(BudgetRepository.convertStockQuantity(250.0, "g", "tub (500g)", null))
    }

    @Test
    fun areStockUnitsMatchingHandlesCaseAndContainers() {
        assertTrue(BudgetMath.areStockUnitsMatching("Jar", "jar"))
        assertTrue(BudgetMath.areStockUnitsMatching("jar (380g)", "jar"))
        assertTrue(BudgetMath.areStockUnitsMatching("Tub", "tub"))
        assertTrue(BudgetMath.areStockUnitsMatching("carton (30 eggs)", "carton"))
        assertTrue(BudgetMath.areStockUnitsMatching("carton", "carton (30)"))
        // Same dimension is not the same unit: quantities must still be converted.
        assertFalse(BudgetMath.areStockUnitsMatching("Kg", "g"))
        assertFalse(BudgetMath.areStockUnitsMatching("g", "kg"))
        assertFalse(BudgetMath.areStockUnitsMatching("ml", "l"))
        assertFalse(BudgetMath.areStockUnitsMatching("bottle", "loaf"))
    }

    @Test
    fun stockConversionHandlesEgyptianMarketUnits() {
        val tomatoes = FoodCatalogItem(
            id = "tomatoes",
            name = "Tomatoes",
            stockUnit = "kg",
            portionUnit = "piece",
            portionsPerStockUnit = 6.0,
            defaultCostPerPortion = 3.0,
        )
        // 6 pieces of tomatoes = 1.0 kg
        assertEquals(1.0, requireNotNull(BudgetRepository.convertStockQuantity(6.0, "piece", "kg", tomatoes)), 0.001)
        // 3 pieces of tomatoes = 0.5 kg
        assertEquals(0.5, requireNotNull(BudgetRepository.convertStockQuantity(3.0, "pieces", "kg", tomatoes)), 0.001)
        // 1.5 kg of tomatoes = 9 pieces
        assertEquals(9.0, requireNotNull(BudgetRepository.convertStockQuantity(1.5, "kg", "piece", tomatoes)), 0.001)

        val sugar = FoodCatalogItem(
            id = "sugar",
            name = "Sugar",
            stockUnit = "kg",
            portionUnit = "g",
            portionsPerStockUnit = 1000.0,
            defaultCostPerPortion = 0.035,
        )
        // 500 grams of sugar = 0.5 kg
        assertEquals(0.5, requireNotNull(BudgetRepository.convertStockQuantity(500.0, "g", "kg", sugar)), 0.001)
        assertEquals(500.0, requireNotNull(BudgetRepository.convertStockQuantity(0.5, "kg", "g", sugar)), 0.001)

        val eggs = FoodCatalogItem(
            id = "eggs",
            name = "Eggs",
            stockUnit = "carton",
            portionUnit = "egg",
            portionsPerStockUnit = 30.0,
            defaultCostPerPortion = 5.5,
        )
        // 15 eggs = 0.5 carton
        assertEquals(0.5, requireNotNull(BudgetRepository.convertStockQuantity(15.0, "egg", "carton", eggs)), 0.001)
        assertEquals(30.0, requireNotNull(BudgetRepository.convertStockQuantity(1.0, "carton", "eggs", eggs)), 0.001)

        val bread = FoodCatalogItem(
            id = "bread",
            name = "Baladi Bread",
            stockUnit = "bag",
            portionUnit = "loaf",
            portionsPerStockUnit = 5.0,
            defaultCostPerPortion = 5.0,
        )
        // 10 loaves = 2 bags
        assertEquals(2.0, requireNotNull(BudgetRepository.convertStockQuantity(10.0, "loaves", "bag", bread)), 0.001)
        assertEquals(5.0, requireNotNull(BudgetRepository.convertStockQuantity(1.0, "bag", "loaf", bread)), 0.001)
    }

    @Test
    fun unitMatchingHandlesPluralsAndContainers() {
        assertTrue(BudgetRepository.isUnitEquivalent("piece", "pieces"))
        assertTrue(BudgetRepository.isUnitEquivalent("egg", "eggs"))
        assertTrue(BudgetRepository.isUnitEquivalent("loaf", "loaves"))
        assertTrue(BudgetRepository.isUnitEquivalent("cup", "cups"))
        assertTrue(BudgetRepository.isUnitEquivalent("spoon", "spoons"))
        assertTrue(BudgetRepository.isUnitEquivalent("carton", "carton (30)"))
        assertTrue(BudgetRepository.isUnitEquivalent("bag (5)", "bag"))
        assertTrue(BudgetRepository.isUnitEquivalent("bottle (1L)", "bottle"))
        // Same dimension is not the same unit, so different units never compare equal:
        // amounts must be converted instead, e.g. 500 g is 0.5 kg.
        assertFalse(BudgetRepository.isUnitEquivalent("g", "kg"))
        assertFalse(BudgetRepository.isUnitEquivalent("kg", "g"))
        assertFalse(BudgetRepository.isUnitEquivalent("ml", "l"))
        assertEquals(0.5, requireNotNull(BudgetRepository.convertStockQuantity(500.0, "g", "kg", null)), 0.001)
        assertEquals(500.0, requireNotNull(BudgetRepository.convertStockQuantity(0.5, "kg", "g", null)), 0.001)
    }
}
