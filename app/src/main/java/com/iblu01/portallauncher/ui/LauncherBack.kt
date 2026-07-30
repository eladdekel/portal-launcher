package com.iblu01.portallauncher.ui

/** What the back gesture should do, given what is open. */
enum class BackAction {
    CloseItemMenu,
    CloseHiddenList,
    CloseWidgetPicker,
    CloseQuickActions,
    DismissPanel,
    GoToClockPage,
    /** Consume and do nothing: a home activity must never be finished by back. */
    Nothing,
}

/**
 * Resolves back to a single action, innermost surface first.
 *
 * A launcher must always consume back: the default behaviour finishes the activity, and finishing
 * the home activity gives a black flash while the system restarts it. So the last case is
 * [BackAction.Nothing], never "let it through".
 *
 * Only a USER-opened panel is dismissed — an AUTO (media) panel is the resting state while
 * something plays, and dismissing it is read as a user dismissal that suppresses it for the session.
 */
fun backAction(
    itemMenuOpen: Boolean,
    hiddenListOpen: Boolean,
    widgetPickerOpen: Boolean = false,
    quickActionsOpen: Boolean,
    userPanelOpen: Boolean,
    onClockPage: Boolean,
): BackAction = when {
    itemMenuOpen -> BackAction.CloseItemMenu
    hiddenListOpen -> BackAction.CloseHiddenList
    widgetPickerOpen -> BackAction.CloseWidgetPicker
    quickActionsOpen -> BackAction.CloseQuickActions
    userPanelOpen -> BackAction.DismissPanel
    !onClockPage -> BackAction.GoToClockPage
    else -> BackAction.Nothing
}
