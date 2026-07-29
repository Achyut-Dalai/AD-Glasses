package com.fersaiyan.cyanbridge.shared.devices

object GlassesManagerGating {

    enum class Action {
        MEETING_CAPTURE,
        STATUS_BATTERY,
        STATUS_STORAGE,
        HEY_CYAN_EXTRAS,
        META_RAYBAN_CONTROLS,
        META_RAYBAN_REGISTRATION,
        MEIZU_MYVU_CONTROLS,
        EYEVUE_CONTROLS,
    }

    data class UiModel(
        val visibleActions: Set<Action>,
    ) {
        fun isVisible(action: Action): Boolean = visibleActions.contains(action)
    }

    fun uiModel(profile: DeviceProfile?): UiModel = UiModel(visibleActions(profile))

    fun visibleActions(profile: DeviceProfile?): Set<Action> {
        val selected = profile?.selectedClass ?: DeviceClass.UNKNOWN
        return visibleActions(selected)
    }

    fun visibleActions(deviceClass: DeviceClass): Set<Action> {
        val base = linkedSetOf(Action.MEETING_CAPTURE)
        when (deviceClass) {
            DeviceClass.HEY_CYAN -> {
                base.add(Action.HEY_CYAN_EXTRAS)
                base.add(Action.STATUS_BATTERY)
                base.add(Action.STATUS_STORAGE)
            }
            DeviceClass.META_RAYBAN -> {
                base.add(Action.META_RAYBAN_CONTROLS)
                base.add(Action.META_RAYBAN_REGISTRATION)
            }
            DeviceClass.MEIZU_MYVU -> {
                base.add(Action.MEIZU_MYVU_CONTROLS)
                base.add(Action.STATUS_BATTERY)
            }
            DeviceClass.EYEVUE -> {
                base.add(Action.EYEVUE_CONTROLS)
                base.add(Action.STATUS_BATTERY)
                base.add(Action.STATUS_STORAGE)
            }
            else -> {}
        }
        return base
    }
}
