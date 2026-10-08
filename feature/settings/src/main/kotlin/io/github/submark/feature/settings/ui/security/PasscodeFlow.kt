package io.github.submark.feature.settings.ui.security

import io.github.submark.feature.settings.security.PasscodeHasher

enum class PasscodeFlowMode { SET, CHANGE, REMOVE }

enum class PasscodeStep { VERIFY_CURRENT, ENTER_NEW, CONFIRM_NEW }

enum class PasscodeFlowError { WRONG, MISMATCH, LOCKED_OUT }

/** State of the set / change / remove passcode dialog. */
data class PasscodeFlowState(
    val mode: PasscodeFlowMode,
    val step: PasscodeStep = if (mode == PasscodeFlowMode.SET) PasscodeStep.ENTER_NEW else PasscodeStep.VERIFY_CURRENT,
    val entered: String = "",
    val firstEntry: String? = null,
    val error: PasscodeFlowError? = null,
    /** Attempts left (WRONG) or lockout seconds (LOCKED_OUT). */
    val errorCount: Int = 0,
    val shakeKey: Int = 0,
    val busy: Boolean = false,
)

/** Pure transitions of the passcode dialog that do not need the stored hash. */
internal object PasscodeFlowReducer {

    fun append(state: PasscodeFlowState, digit: Char): PasscodeFlowState =
        if (state.busy || state.entered.length >= PasscodeHasher.LENGTH) state
        else state.copy(entered = state.entered + digit)

    fun backspace(state: PasscodeFlowState): PasscodeFlowState =
        if (state.busy) state else state.copy(entered = state.entered.dropLast(1))

    fun isComplete(state: PasscodeFlowState): Boolean = state.entered.length == PasscodeHasher.LENGTH

    /** After VERIFY_CURRENT succeeded (CHANGE only; REMOVE finishes). */
    fun verified(state: PasscodeFlowState): PasscodeFlowState =
        state.copy(step = PasscodeStep.ENTER_NEW, entered = "", error = null, busy = false)

    fun verifyFailed(state: PasscodeFlowState, error: PasscodeFlowError, count: Int): PasscodeFlowState =
        state.copy(entered = "", error = error, errorCount = count, shakeKey = state.shakeKey + 1, busy = false)

    /** ENTER_NEW → CONFIRM_NEW. */
    fun newEntered(state: PasscodeFlowState): PasscodeFlowState =
        state.copy(step = PasscodeStep.CONFIRM_NEW, firstEntry = state.entered, entered = "", error = null)

    /** CONFIRM_NEW: returns the code to save, or a state restarting at ENTER_NEW with a mismatch error. */
    fun confirm(state: PasscodeFlowState): Pair<PasscodeFlowState, String?> =
        if (state.entered == state.firstEntry) state.copy(busy = true) to state.entered
        else state.copy(
            step = PasscodeStep.ENTER_NEW,
            entered = "",
            firstEntry = null,
            error = PasscodeFlowError.MISMATCH,
            shakeKey = state.shakeKey + 1,
        ) to null
}
