package com.r1control.app.ui.settings

import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RestartStep { Idle, Asking, Restarting, Restarted }

data class AdvancedState(val restart: RestartStep, val problem: Problem)

/** The restart button and its confirmation. The address field is [com.r1control.app.ui.address.AddressViewModel]. */
class AdvancedViewModel(
    private val speaker: SpeakerRepository,
    private val scope: CoroutineScope,
) {
    private val flow = MutableStateFlow(AdvancedState(RestartStep.Idle, Problem.None))
    val state: StateFlow<AdvancedState> = flow.asStateFlow()

    fun askRestart() {
        flow.value = AdvancedState(RestartStep.Asking, Problem.None)
    }

    fun cancelRestart() {
        flow.value = AdvancedState(RestartStep.Idle, Problem.None)
    }

    fun confirmRestart() {
        if (flow.value.restart != RestartStep.Asking) return
        flow.value = AdvancedState(RestartStep.Restarting, Problem.None)
        scope.launch {
            flow.value = try {
                speaker.restart()
                AdvancedState(RestartStep.Restarted, Problem.None)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeakerException) {
                AdvancedState(RestartStep.Idle, Problem.Failed(e))
            }
        }
    }
}
