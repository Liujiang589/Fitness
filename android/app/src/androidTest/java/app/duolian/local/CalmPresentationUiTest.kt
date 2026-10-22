package app.duolian.local

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import app.duolian.local.domain.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated presentation checks; never change the user's application database. */
class CalmPresentationUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun nutritionDetailsAreOptionalAndSurviveStateRestoration() {
        val food = Food(date = "2026-10-22", meal = "午餐", name = "测试午餐",
            protein = 20.0, carbs = 30.0, fat = 10.0)
        val actions = ScreenActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {})
        val restoration = StateRestorationTester(ui)
        restoration.setContent { DuolianTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NutritionSummary(listOf(food), emptyList(), food.date, actions)
            }
        } }
        ui.onNodeWithText("20.0 g").assertDoesNotExist()
        ui.onNodeWithText("查看营养明细").performClick()
        ui.onNodeWithText("20.0 g").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("30.0 g").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("20.0 g").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("收起营养明细").performScrollTo().performClick()
        ui.onNodeWithText("20.0 g").assertDoesNotExist()
        assertEquals(290.0, food.calories, .0001)
    }
}
