package com.iblu01.portallauncher.ui.model

/**
 * Where a chip renders (design §7). Replaces the `id == "presence_group"` special-case: presence
 * shows as a floating top-left avatar badge, everything else sits in the bottom tray.
 */
enum class ChipPlacement { TRAY, FLOATING }
