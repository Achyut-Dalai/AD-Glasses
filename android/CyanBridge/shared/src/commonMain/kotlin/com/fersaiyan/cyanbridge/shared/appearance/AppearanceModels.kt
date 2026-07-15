package com.fersaiyan.cyanbridge.shared.appearance

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentProfileId: String = AccentProfiles.CYAN_ID,
    val useDynamicColor: Boolean = false,
    val highContrast: Boolean = false,
)

data class AccentProfile(
    val id: String,
    val label: String,
    val lightPrimaryArgb: Long,
    val lightContainerArgb: Long,
    val darkPrimaryArgb: Long,
    val darkContainerArgb: Long,
)

object AccentProfiles {
    const val CYAN_ID = "cyan"

    val all = listOf(
        AccentProfile(
            id = CYAN_ID,
            label = "Cyan",
            lightPrimaryArgb = 0xFF006875,
            lightContainerArgb = 0xFF9EEFFD,
            darkPrimaryArgb = 0xFF4FD8EB,
            darkContainerArgb = 0xFF004E58,
        ),
        AccentProfile(
            id = "rose",
            label = "Rose",
            lightPrimaryArgb = 0xFF8F3D5B,
            lightContainerArgb = 0xFFFFD9E2,
            darkPrimaryArgb = 0xFFFFB0C8,
            darkContainerArgb = 0xFF702642,
        ),
        AccentProfile(
            id = "mint",
            label = "Mint",
            lightPrimaryArgb = 0xFF286847,
            lightContainerArgb = 0xFFAEF2C9,
            darkPrimaryArgb = 0xFF92D5AD,
            darkContainerArgb = 0xFF0B5132,
        ),
        AccentProfile(
            id = "lavender",
            label = "Lavender",
            lightPrimaryArgb = 0xFF6750A4,
            lightContainerArgb = 0xFFE9DDFF,
            darkPrimaryArgb = 0xFFD0BCFF,
            darkContainerArgb = 0xFF4F378B,
        ),
        AccentProfile(
            id = "peach",
            label = "Peach",
            lightPrimaryArgb = 0xFF8B4A20,
            lightContainerArgb = 0xFFFFDBC8,
            darkPrimaryArgb = 0xFFFFB68F,
            darkContainerArgb = 0xFF6E330B,
        ),
        AccentProfile(
            id = "sky",
            label = "Sky",
            lightPrimaryArgb = 0xFF00639B,
            lightContainerArgb = 0xFFCDE5FF,
            darkPrimaryArgb = 0xFF96CCFF,
            darkContainerArgb = 0xFF004A76,
        ),
    )

    fun find(id: String): AccentProfile = all.firstOrNull { it.id == id } ?: all.first()
}
