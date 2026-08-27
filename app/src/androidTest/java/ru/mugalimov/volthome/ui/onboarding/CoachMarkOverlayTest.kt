package ru.mugalimov.volthome.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.mugalimov.volthome.ui.onboarding.model.ActiveHint
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

class CoachMarkOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationAndDeferralAreSeparateActions() {
        var result = ""
        composeRule.setContent {
            MaterialTheme {
                CoachMarkOverlay(
                    activeHint = ActiveHint(
                        hintId = OnboardingHintId.ROOMS_ADD_FIRST_ROOM,
                        screen = OnboardingScreen.ROOMS,
                        targetTag = OnboardingTargetTag.ROOMS_ADD_FAB,
                        title = "Добавьте комнату",
                        body = "Тестовая подсказка",
                        activatedAtMillis = 1L
                    ),
                    anchorBounds = Rect(80f, 120f, 180f, 200f),
                    onConfirmed = { result = "confirmed" },
                    onDeferred = { result = "deferred" },
                    onSkipAll = { result = "disabled" }
                )
            }
        }

        composeRule.onNodeWithTag("coach_mark_card").assertIsDisplayed()
        composeRule.onNodeWithText("Позже").performClick()
        composeRule.runOnIdle { assertEquals("deferred", result) }
    }
}
