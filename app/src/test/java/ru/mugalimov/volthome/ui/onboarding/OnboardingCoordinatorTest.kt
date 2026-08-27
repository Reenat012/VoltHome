package ru.mugalimov.volthome.ui.onboarding

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.data.repository.OnboardingRepository
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingDismissReason
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

class OnboardingCoordinatorTest {

    @Test
    fun `cooldown waits and does not lose request`() = runBlocking {
        val repository = FakeOnboardingRepository().apply {
            lastShownAt = System.currentTimeMillis()
        }
        val coordinator = OnboardingCoordinator(repository)
        coordinator.onScreenChanged(OnboardingScreen.ROOMS)

        val accepted = coordinator.tryShow(
            hintId = OnboardingHintId.ROOMS_ADD_FIRST_ROOM,
            screen = OnboardingScreen.ROOMS,
            targetTag = OnboardingTargetTag.ROOMS_ADD_FAB,
            title = "Комната",
            body = "Добавьте комнату",
            cooldownMillis = 5L
        )

        assertTrue(accepted)
    }

    @Test
    fun `screen change cancels stale hint without marking it shown`() = runBlocking {
        val repository = FakeOnboardingRepository()
        val coordinator = OnboardingCoordinator(repository)
        coordinator.onScreenChanged(OnboardingScreen.ROOMS)
        showRoomHint(coordinator)

        coordinator.onScreenChanged(OnboardingScreen.LOADS)

        assertNull(coordinator.activeHint.value)
        assertFalse(repository.isShown(OnboardingHintId.ROOMS_ADD_FIRST_ROOM))
    }

    @Test
    fun `confirmation persists but deferral does not`() = runBlocking {
        val repository = FakeOnboardingRepository()
        val coordinator = OnboardingCoordinator(repository)
        coordinator.onScreenChanged(OnboardingScreen.ROOMS)
        showRoomHint(coordinator)
        coordinator.markPresented(OnboardingHintId.ROOMS_ADD_FIRST_ROOM)
        coordinator.dismiss(OnboardingDismissReason.USER_CONFIRMED)

        assertTrue(repository.isShown(OnboardingHintId.ROOMS_ADD_FIRST_ROOM))

        coordinator.onScreenChanged(OnboardingScreen.LOADS)
        coordinator.onScreenChanged(OnboardingScreen.ROOMS)
        repository.marked[OnboardingHintId.ROOMS_ADD_FIRST_ROOM]?.value = false
        showRoomHint(coordinator)
        coordinator.dismiss(OnboardingDismissReason.USER_DEFERRED)

        assertFalse(repository.isShown(OnboardingHintId.ROOMS_ADD_FIRST_ROOM))
    }

    private suspend fun showRoomHint(coordinator: OnboardingCoordinator): Boolean {
        return coordinator.tryShow(
            hintId = OnboardingHintId.ROOMS_ADD_FIRST_ROOM,
            screen = OnboardingScreen.ROOMS,
            targetTag = OnboardingTargetTag.ROOMS_ADD_FAB,
            title = "Комната",
            body = "Добавьте комнату",
            cooldownMillis = 0L
        )
    }
}

private class FakeOnboardingRepository : OnboardingRepository {
    val marked = OnboardingHintId.entries.associateWith { MutableStateFlow(false) }
    var lastShownAt: Long = 0L
    var enabled: Boolean = true

    override suspend fun isShown(hintId: OnboardingHintId): Boolean =
        marked.getValue(hintId).value

    override fun observeShown(hintId: OnboardingHintId): Flow<Boolean> =
        marked.getValue(hintId)

    override suspend fun markShown(hintId: OnboardingHintId) {
        marked.getValue(hintId).value = true
    }

    override suspend fun getLastAnyHintShownAt(): Long = lastShownAt

    override suspend fun setLastAnyHintShownAt(value: Long) {
        lastShownAt = value
    }

    override suspend fun areHintsEnabled(): Boolean = enabled

    override suspend fun disableHints() {
        enabled = false
    }

    override suspend fun resetAll() {
        marked.values.forEach { it.value = false }
        lastShownAt = 0L
        enabled = true
    }
}
