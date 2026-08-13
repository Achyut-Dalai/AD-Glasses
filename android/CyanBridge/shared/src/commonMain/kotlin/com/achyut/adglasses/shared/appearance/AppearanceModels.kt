package com.achyut.adglasses.shared.appearance

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentProfileId: String = AccentProfiles.ADGLASSES_ID,
    val useDynamicColor: Boolean = false,
    val highContrast: Boolean = false,
)

data class AccentProfile(
    val id: String,
    val label: String,
    val lightPrimaryArgb: Long,
    val lightContainerArgb: Long,
    val lightSecondaryArgb: Long,
    val lightSecondaryContainerArgb: Long,
    val lightTertiaryArgb: Long,
    val lightTertiaryContainerArgb: Long,
    val darkPrimaryArgb: Long,
    val darkContainerArgb: Long,
    val darkSecondaryArgb: Long,
    val darkSecondaryContainerArgb: Long,
    val darkTertiaryArgb: Long,
    val darkTertiaryContainerArgb: Long,
)

object AccentProfiles {
    const val CYAN_ID = "cyan"
    const val ADGLASSES_ID = "adglasses"

    val all = listOf(
        AccentProfile(
            id = ADGLASSES_ID,
            label = "AD Glasses",
            lightPrimaryArgb = 0xFF000000, // Black
            lightContainerArgb = 0xFFF0F0F0, // Light Gray
            lightSecondaryArgb = 0xFF404040, // Dark Gray
            lightSecondaryContainerArgb = 0xFFE0E0E0,
            lightTertiaryArgb = 0xFF606060,
            lightTertiaryContainerArgb = 0xFFF5F5F5,
            darkPrimaryArgb = 0xFFFFFFFF, // White
            darkContainerArgb = 0xFF1A1A1A, // Very Dark Gray (Vercel-style)
            darkSecondaryArgb = 0xFFB0B0B0, // Light Gray
            darkSecondaryContainerArgb = 0xFF333333,
            darkTertiaryArgb = 0xFFA0A0A0,
            darkTertiaryContainerArgb = 0xFF262626,
        ),
        AccentProfile(
            id = CYAN_ID,
            label = "Cyan",
            lightPrimaryArgb = 0xFF006875,
            lightContainerArgb = 0xFF9EEFFD,
            lightSecondaryArgb = 0xFF4A6266,
            lightSecondaryContainerArgb = 0xFFCDE7EB,
            lightTertiaryArgb = 0xFF536348,
            lightTertiaryContainerArgb = 0xFFD6E8C5,
            darkPrimaryArgb = 0xFF4FD8EB,
            darkContainerArgb = 0xFF004E58,
            darkSecondaryArgb = 0xFFB1CBD0,
            darkSecondaryContainerArgb = 0xFF324B4F,
            darkTertiaryArgb = 0xFFBAD0A8,
            darkTertiaryContainerArgb = 0xFF3B4B31,
        ),
        AccentProfile(
            id = "rose",
            label = "Rose",
            lightPrimaryArgb = 0xFF8F3D5B,
            lightContainerArgb = 0xFFFFD9E2,
            lightSecondaryArgb = 0xFF74565F,
            lightSecondaryContainerArgb = 0xFFFFD9E2,
            lightTertiaryArgb = 0xFF635B3F,
            lightTertiaryContainerArgb = 0xFFEEE2B9,
            darkPrimaryArgb = 0xFFFFB0C8,
            darkContainerArgb = 0xFF702642,
            darkSecondaryArgb = 0xFFE6BDC8,
            darkSecondaryContainerArgb = 0xFF573A43,
            darkTertiaryArgb = 0xFFD3C58C,
            darkTertiaryContainerArgb = 0xFF4B462E,
        ),
        AccentProfile(
            id = "mint",
            label = "Mint",
            lightPrimaryArgb = 0xFF286847,
            lightContainerArgb = 0xFFAEF2C9,
            lightSecondaryArgb = 0xFF4E6357,
            lightSecondaryContainerArgb = 0xFFD0E8D9,
            lightTertiaryArgb = 0xFF486178,
            lightTertiaryContainerArgb = 0xFFCDE5FF,
            darkPrimaryArgb = 0xFF92D5AD,
            darkContainerArgb = 0xFF0B5132,
            darkSecondaryArgb = 0xFFB4CCBC,
            darkSecondaryContainerArgb = 0xFF354B3D,
            darkTertiaryArgb = 0xFFB5C9E8,
            darkTertiaryContainerArgb = 0xFF334A63,
        ),
        AccentProfile(
            id = "lavender",
            label = "Lavender",
            lightPrimaryArgb = 0xFF6750A4,
            lightContainerArgb = 0xFFE9DDFF,
            lightSecondaryArgb = 0xFF625A70,
            lightSecondaryContainerArgb = 0xFFE8DEF2,
            lightTertiaryArgb = 0xFF7A5263,
            lightTertiaryContainerArgb = 0xFFFFD9E7,
            darkPrimaryArgb = 0xFFD0BCFF,
            darkContainerArgb = 0xFF4F378B,
            darkSecondaryArgb = 0xFFCCC0D8,
            darkSecondaryContainerArgb = 0xFF494153,
            darkTertiaryArgb = 0xFFEDB8CF,
            darkTertiaryContainerArgb = 0xFF603A4B,
        ),
        AccentProfile(
            id = "peach",
            label = "Peach",
            lightPrimaryArgb = 0xFF8B4A20,
            lightContainerArgb = 0xFFFFDBC8,
            lightSecondaryArgb = 0xFF76574A,
            lightSecondaryContainerArgb = 0xFFFFDBCC,
            lightTertiaryArgb = 0xFF5F5D3A,
            lightTertiaryContainerArgb = 0xFFE6E5B8,
            darkPrimaryArgb = 0xFFFFB68F,
            darkContainerArgb = 0xFF6E330B,
            darkSecondaryArgb = 0xFFE6BFAE,
            darkSecondaryContainerArgb = 0xFF573F35,
            darkTertiaryArgb = 0xFFCBD09A,
            darkTertiaryContainerArgb = 0xFF45472A,
        ),
        AccentProfile(
            id = "sky",
            label = "Sky",
            lightPrimaryArgb = 0xFF00639B,
            lightContainerArgb = 0xFFCDE5FF,
            lightSecondaryArgb = 0xFF50606F,
            lightSecondaryContainerArgb = 0xFFD3E5F5,
            lightTertiaryArgb = 0xFF5D5B70,
            lightTertiaryContainerArgb = 0xFFE5DEFF,
            darkPrimaryArgb = 0xFF96CCFF,
            darkContainerArgb = 0xFF004A76,
            darkSecondaryArgb = 0xFFB8C8D8,
            darkSecondaryContainerArgb = 0xFF384955,
            darkTertiaryArgb = 0xFFC7C0DF,
            darkTertiaryContainerArgb = 0xFF47445A,
        ),
    )

    fun find(id: String): AccentProfile = all.firstOrNull { it.id == id } ?: all.first()
}
