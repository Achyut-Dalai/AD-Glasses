package com.ad_glasses.ui.localagent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ad_glasses.localagent.memory.LocalAgentMemoryStore
import com.ad_glasses.shared.ui.localagent.LocalAgentDocumentScreen
import com.ad_glasses.ui.appearance.AppearancePreferences
import com.ad_glasses.ui.appearance.rememberAppearanceSettings
import com.ad_glasses.ui.theme.ADGlassesTheme

class DailyFactsActivity : AppCompatActivity() {

    private var factsText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalAgentMemoryStore.ensureSeedFiles(this)

        val mode = intent.getStringExtra(EXTRA_MODE)?.trim().orEmpty().ifBlank { MODE_DRAFT }
        val date = intent.getStringExtra(EXTRA_DATE)?.trim().orEmpty().ifBlank {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(System.currentTimeMillis()))
        }

        val file = when (mode) {
            MODE_CONFIRMED -> LocalAgentMemoryStore.confirmedDailyFactsFileForDate(this, date)
            else -> LocalAgentMemoryStore.dailyFactsFileForDate(this, date)
        }

        val title = when (mode) {
            MODE_CONFIRMED -> "Confirmed daily facts ($date)"
            else -> "Daily facts ($date)"
        }

        val hint = when (mode) {
            MODE_CONFIRMED -> "Confirmed facts (used by the agent as true for this day)"
            else -> "Write facts you want to remember / verify"
        }

        factsText = LocalAgentMemoryStore.readText(file)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            ADGlassesTheme(appearance) {
                LocalAgentDocumentScreen(
                    title = title,
                    path = file.absolutePath,
                    text = factsText,
                    hint = hint,
                    editable = true,
                    primaryLabel = "Save",
                    onTextChange = { factsText = it },
                    onPrimary = {
                        LocalAgentMemoryStore.writeText(file, factsText)
                        Toast.makeText(
                            this,
                            if (mode == MODE_CONFIRMED) "Saved confirmed facts" else "Saved daily facts",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DATE = "extra_date"

        const val MODE_DRAFT = "draft"
        const val MODE_CONFIRMED = "confirmed"
    }
}
