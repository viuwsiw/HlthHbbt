package com.tshk.hlthhbbty

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Проходит весь путь пользователя: регистрация → анкета → вкладки,
 * и снимает скриншоты каждого экрана в build/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h880dp-xxhdpi")
class AppFlowScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun waitForText(text: String) {
        rule.mainClock.autoAdvance = true
        rule.waitUntil(20_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun fullFlow() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1500)
        shot("01_login")

        rule.onNodeWithText("Регистрация").performClick()
        rule.mainClock.advanceTimeBy(800)
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Антон")
        fields[1].performTextInput("anton")
        fields[2].performTextInput("secret123")
        fields[3].performTextInput("secret123")
        shot("02_register")

        rule.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        // PBKDF2 + запись в базу идут в фоне
        waitForText("Расскажите о себе")
        shot("03_onboarding")

        rule.onAllNodes(hasSetTextAction())[1].performTextInput("21")
        rule.onNodeWithText("Мужской").performScrollTo().performClick()
        rule.onNodeWithText("Готово, поехали!").performScrollTo().performClick()
        waitForText("+250")
        rule.onNodeWithText("+250").performClick()
        rule.onNodeWithText("+500").performClick()
        repeat(10) { rule.mainClock.advanceTimeBy(200); Thread.sleep(80) }
        shot("04_home")

        rule.onAllNodesWithText("Привычки")[0].performClick()
        repeat(10) { rule.mainClock.advanceTimeBy(200); Thread.sleep(80) }
        shot("05_habits")

        rule.onNodeWithText("Статистика").performClick()
        repeat(10) { rule.mainClock.advanceTimeBy(200); Thread.sleep(80) }
        shot("06_stats")

        rule.onNodeWithText("Профиль").performClick()
        repeat(10) { rule.mainClock.advanceTimeBy(200); Thread.sleep(80) }
        shot("07_profile")

        rule.onNodeWithText("Интеграции").performClick()
        repeat(10) { rule.mainClock.advanceTimeBy(200); Thread.sleep(80) }
        shot("08_integrations")
    }
}
