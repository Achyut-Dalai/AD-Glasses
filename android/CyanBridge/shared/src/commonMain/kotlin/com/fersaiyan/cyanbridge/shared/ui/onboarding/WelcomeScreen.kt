package com.fersaiyan.cyanbridge.shared.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.icons.AppIcon
import com.fersaiyan.cyanbridge.shared.icons.imageVector
import com.fersaiyan.cyanbridge.shared.generated.resources.Res
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_language_description
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_language_title
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_start_setup
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_welcome_body
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

data class OnboardingLanguageOption(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalResourceApi::class)
@Composable
fun WelcomeScreen(
    languageOptions: List<OnboardingLanguageOption>,
    selectedLanguageId: String,
    languageSelectionComplete: Boolean,
    onLanguageSelected: (OnboardingLanguageOption) -> Unit,
    onStartSetup: () -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = AppIcon.Glasses.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(28.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_language_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_language_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        languageOptions.forEach { option ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = option.id == selectedLanguageId,
                                    onClick = { onLanguageSelected(option) },
                                )
                                Text(option.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(Res.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.onboarding_welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = onStartSetup,
                    enabled = languageSelectionComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(Res.string.onboarding_start_setup))
                }
            }
        }
    }
}
