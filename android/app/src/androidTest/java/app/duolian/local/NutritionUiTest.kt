package app.duolian.local

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Standalone UI fixtures: these tests never replace the application database. */
class NutritionUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun selectFoodAndWeightSavesCalculatedMacros() {
        var saved: Food? = null
        ui.setContent { DuolianTheme {
            FoodEntryDialog(null, "2026-10-22", "午餐", emptyList(), false, {}, { saved = it })
        } }
        ui.onNodeWithText("米饭", useUnmergedTree = true).performScrollTo().performClick()
        ui.onAllNodes(hasSetTextAction()).onLast().performScrollTo().performTextReplacement("150")
        ui.onNodeWithText("保存饮食").performClick()
        ui.runOnIdle {
            assertNotNull(saved)
            assertEquals(150.0, saved!!.grams!!, .0001)
            assertEquals(42.255, saved!!.carbs, .0001)
            assertEquals("午餐", saved!!.meal)
        }
    }

    @Test fun emptySelectionCannotSaveAndShowsActionableError() {
        var saved: Food? = null
        ui.setContent { DuolianTheme {
            FoodEntryDialog(null, "2026-10-22", "午餐", emptyList(), false, {}, { saved = it })
        } }
        ui.onNodeWithText("保存饮食").performClick()
        ui.onNodeWithText("请先选择食物，并核对生熟状态").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { assertNull(saved) }
    }

    @Test fun legacyFoodEditPreservesTotalsAndDoesNotInventWeight() {
        val existing = Food(meal = "早餐", name = "旧早餐", protein = 20.0, carbs = 30.0, fat = 10.0)
        var saved: Food? = null
        ui.setContent { DuolianTheme {
            FoodEntryDialog(existing, existing.date, existing.meal, emptyList(), false, {}, { saved = it })
        } }
        ui.onNodeWithText("保存饮食").performClick()
        ui.runOnIdle {
            assertEquals(existing, saved)
            assertNull(saved!!.grams)
            assertEquals(290.0, saved!!.calories, .0001)
        }
    }
}
