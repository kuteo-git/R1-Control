package com.r1control.app.ui.settings

import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.snapshotOrNull
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.afterGoodRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A forget is the one irreversible tap on this page, so it is asked first. */
sealed interface ForgetAsk {
    data object None : ForgetAsk
    data class Ask(val device: BluetoothDevice) : ForgetAsk
}

data class BluetoothScreenState(
    val bluetooth: Loadable<BluetoothState>,
    val problem: Problem,
    val forget: ForgetAsk,
)

/**
 * The Bluetooth page: polls while open, as the panel does, and every action is
 * a command followed by a read so the page shows what the R1 now says.
 *
 * A banner says something true or goes. A failed read is shown until a read
 * works. A command that could not reach the speaker is shown until a later
 * poll reaches it; a refusal is the speaker's answer to that tap and stays
 * until the next one.
 */
class BluetoothViewModel(
    private val speaker: SpeakerRepository,
    private val scope: CoroutineScope,
    private val pollMillis: Long = 1_000,
    /** The feed's reading now: the page opens on its values, with no Loading frame, and the first read refreshes them. */
    start: SpeakerView = SpeakerView.Connecting,
    /** A command the speaker took: the shared reading is stale until it reads again. */
    private val onChanged: () -> Unit = {},
) {
    private val flow = MutableStateFlow(
        BluetoothScreenState(
            start.snapshotOrNull()?.let { Loadable.Loaded(it.bluetooth) } ?: Loadable.Loading,
            Problem.None, ForgetAsk.None,
        ),
    )
    val state: StateFlow<BluetoothScreenState> = flow.asStateFlow()
    private var commandProblem: Problem = Problem.None
    private var readProblem: Problem = Problem.None

    init {
        scope.launch {
            while (true) {
                read(afterCommand = false)
                delay(pollMillis)
            }
        }
    }

    /** Reads now, for the Try again under a failed first read. */
    fun retry() {
        scope.launch { read(afterCommand = false) }
    }

    fun onEnabled(on: Boolean) = act { speaker.setBluetoothEnabled(on) }
    fun onAutoReconnect(on: Boolean) = act { speaker.setAutoReconnect(on) }
    fun scan() = act { speaker.startBluetoothScan() }
    fun receive() = act { speaker.receiveFromPhone() }
    fun pair(device: BluetoothDevice) = act { speaker.pair(device.address) }
    fun connect(device: BluetoothDevice) = act { speaker.connect(device.address) }
    fun disconnect(device: BluetoothDevice) = act { speaker.disconnect(device.address) }

    fun askForget(device: BluetoothDevice) {
        flow.value = flow.value.copy(forget = ForgetAsk.Ask(device))
    }

    fun cancelForget() {
        flow.value = flow.value.copy(forget = ForgetAsk.None)
    }

    fun confirmForget() {
        val ask = flow.value.forget as? ForgetAsk.Ask ?: return
        flow.value = flow.value.copy(forget = ForgetAsk.None)
        act { speaker.forget(ask.device.address) }
    }

    private fun act(block: suspend () -> Unit) {
        scope.launch {
            commandProblem = try {
                block()
                Problem.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeakerException) {
                Problem.Failed(e)
            }
            showProblem()
            if (commandProblem is Problem.None) onChanged()
            read(afterCommand = true)
        }
    }

    /** [afterCommand]: the read that shows a command's result does not count as the speaker answering again. */
    private suspend fun read(afterCommand: Boolean) {
        try {
            val bluetooth = speaker.bluetooth()
            readProblem = Problem.None
            if (!afterCommand) commandProblem = commandProblem.afterGoodRead()
            flow.value = flow.value.copy(bluetooth = Loadable.Loaded(bluetooth))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakerException) {
            if (flow.value.bluetooth is Loadable.Loaded) {
                readProblem = Problem.Failed(e)
            } else {
                flow.value = flow.value.copy(bluetooth = Loadable.Failed(e))
            }
        }
        showProblem()
    }

    private fun showProblem() {
        flow.value = flow.value.copy(problem = if (commandProblem is Problem.Failed) commandProblem else readProblem)
    }
}
