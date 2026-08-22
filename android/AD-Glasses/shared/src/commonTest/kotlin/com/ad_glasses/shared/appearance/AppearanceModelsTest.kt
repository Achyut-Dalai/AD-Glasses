package com.ad_glasses.shared.appearance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ad_glasses.shared.navigation.AppDestination
import com.ad_glasses.shared.chat.ChatThreadSummary
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import com.ad_glasses.shared.chat.ChatThread
import com.ad_glasses.shared.chat.ChatThreadEvent
import com.ad_glasses.shared.chat.ChatThreadStateReducer
import com.ad_glasses.shared.chat.ChatThreadUiState
import com.ad_glasses.bridge.core.DisplayCommand
import com.ad_glasses.bridge.core.DisplayPriority
import com.ad_glasses.shared.platform.ADGlassesSharedBootstrap

class AppearanceModelsTest {
    @Test
    fun defaultsRemainStableAcrossPlatforms() {
        assertEquals(ThemeMode.SYSTEM, AppearanceSettings().themeMode)
        assertEquals(AccentProfiles.CYAN_ID, AppearanceSettings().accentProfileId)
    }

    @Test
    fun accentIdsAreStableAndUnique() {
        val ids = AccentProfiles.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(AccentProfiles.all.all { it.label.isNotBlank() })
    }

    @Test
    fun unknownAccentFallsBackToCyan() {
        assertEquals(AccentProfiles.CYAN_ID, AccentProfiles.find("missing").id)
    }

    @Test
    fun navigationDestinationsRemainStable() {
        assertEquals(
            listOf("GLASSES", "CHATS", "MEDIA", "PLUGINS", "SETTINGS"),
            AppDestination.entries.map { it.name },
        )
    }

    @Test
    fun chatHistorySummaryIsImmutablePlatformState() {
        val summary = ChatThreadSummary("chat-1", "First chat", 1234L)

        assertEquals("chat-1", summary.id)
        assertEquals(1234L, summary.updatedAtEpochMillis)
    }

    @Test
    fun chatMessagesArePortableValueObjects() {
        val message = ChatMessage("message-1", "chat-1", ChatRole.USER, "Hello", 1234L)

        assertEquals(ChatRole.USER, message.role)
        assertEquals("Hello", message.content)
    }

    @Test
    fun chatThreadReducerKeepsStreamingRepliesOutOfPersistence() {
        val message = ChatMessage("message-1", "chat-1", ChatRole.USER, "Hello", 1234L)
        val thread = ChatThread("chat-1", "Hello", 1000L, 1234L)
        val loaded = ChatThreadStateReducer.reduce(
            ChatThreadUiState(),
            ChatThreadEvent.Loaded(thread, listOf(message)),
        )
        val streaming = ChatThreadStateReducer.reduce(
            loaded,
            ChatThreadEvent.GenerationStarted("Loading"),
        )
        val updated = ChatThreadStateReducer.reduce(
            streaming,
            ChatThreadEvent.StreamUpdated("Partial reply"),
        )
        val renamed = ChatThreadStateReducer.reduce(
            updated,
            ChatThreadEvent.ThreadChanged(thread.copy(title = "Renamed chat")),
        )

        assertEquals(1, renamed.messages.size)
        assertEquals("Renamed chat", renamed.thread?.title)
        assertEquals("Partial reply", renamed.streamingAssistantText)
        assertEquals(2, ChatThreadStateReducer.visibleMessages(renamed.messages, "chat-1", renamed.streamingAssistantText, 1500L).size)
    }

    @Test
    fun bridgeCommandsAndBootstrapArePortable() {
        val command = DisplayCommand.Text("Ready", DisplayPriority.HIGH)

        assertEquals("Ready", command.text)
        assertEquals("ADGlasses", ADGlassesSharedBootstrap.applicationName())
        assertEquals("cyan", ADGlassesSharedBootstrap.defaultAccentProfileId())
        assertEquals("CHATS", ADGlassesSharedBootstrap.defaultDestinationId())
    }
}
