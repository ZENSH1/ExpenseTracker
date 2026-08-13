package com.xs.expensetracker.utils.states

/** Which kind of record a finished write touched, so only the matching modal reacts to it. */
enum class ActionTarget { TRACKER, SOURCE, RECEIPT }

/**
 * A write that finished successfully, held in state until the UI reads it.
 *
 * The modals used to infer this from [TransactionsUiState.isLoading] falling back to false.
 * That loses the result whenever a write completes inside a single frame: a local Room insert
 * usually does, the two `isLoading` updates are conflated by the StateFlow, the UI only ever
 * observes false, and so the sheet sits open over a receipt that was in fact saved. A value
 * that survives until something consumes it cannot be missed that way.
 *
 * [id] increments per result, so saving twice in a row still reads as two separate events
 * rather than one unchanged value.
 */
data class ActionResult(
    val id: Long,
    val target: ActionTarget,
    val message: String
)
