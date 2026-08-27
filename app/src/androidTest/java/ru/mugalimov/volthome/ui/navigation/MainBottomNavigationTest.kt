package ru.mugalimov.volthome.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test

class MainBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomsTabAlwaysOpensRoomsInsteadOfRestoringAnotherSection() {
        composeRule.setContent { TestMainNavigation() }

        composeRule.onNodeWithText("Экран комнат").assertIsDisplayed()
        composeRule.onNodeWithText("Линии").performClick()
        composeRule.onNodeWithText("Экран линий").assertIsDisplayed()
        composeRule.onNodeWithText("Комнаты").performClick()
        composeRule.onNodeWithText("Экран комнат").assertIsDisplayed()
    }
}

@Composable
private fun TestMainNavigation() {
    val navController = rememberNavController()
    MaterialTheme {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) {
                NavHost(navController, startDestination = Screens.RoomsList.route) {
                    composable(Screens.RoomsList.route) { Text("Экран комнат") }
                    composable(Screens.LoadsScreen.route) { Text("Экран нагрузок") }
                    composable(Screens.ExplicationScreen.route) { Text("Экран линий") }
                    composable(Screens.PanelVisualizationScreen.route) { Text("Экран щита") }
                }
            }
            MainBottomNavBar(navController)
        }
    }
}
