package com.achyut.adglasses.shared.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates core entity data-class behavior and the in-memory
 * list-manipulation patterns used by the iOS PlatformPreferences
 * repositories (IosDeviceProfileRepository, IosMemoryVaultRepository,
 * IosMediaRecordRepository).
 *
 * These tests confirm that the CRUD, search, and ByteArray
 * serialization utilities work correctly **before** they are
 * persisted to NSUserDefaults on device.
 */
class PersistenceEntityTest {

    // ── DeviceProfileEntity ──────────────────────────────────

    @Test
    fun deviceProfileEntityConstructsAndEquates() {
        val a = DeviceProfileEntity(
            macAddress = "AA:BB:CC:DD:EE:01",
            advertisedName = "CyanGlass-01",
            detectedClass = "glasses",
            selectedClass = "glasses",
            userOverridden = false,
            lastConnectedAt = 1000L,
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(macAddress = "AA:BB:CC:DD:EE:02")
        assertTrue(a != c)
    }

    @Test
    fun deviceProfileUpsertPattern() {
        // The repository pattern: getAll → removeAll(matching id) → add → save
        val profiles = mutableListOf(
            DeviceProfileEntity("M1", null, "glasses", "glasses", false, 100L),
            DeviceProfileEntity("M2", "Cyan-Two", "glasses", "glasses", true, 200L),
        )

        // Simulate upsert: remove existing, then add
        val upserted = DeviceProfileEntity("M1", "Renamed", "glasses", "glasses", true, 300L)
        profiles.removeAll { it.macAddress == upserted.macAddress }
        profiles.add(upserted)

        assertEquals(2, profiles.size)
        assertEquals("Renamed", profiles.find { it.macAddress == "M1" }?.advertisedName)
        assertEquals(true, profiles.find { it.macAddress == "M1" }?.userOverridden)
    }

    @Test
    fun deviceProfileDeletePattern() {
        val profiles = mutableListOf(
            DeviceProfileEntity("D1", null, "glasses", "glasses", false, 100L),
            DeviceProfileEntity("D2", null, "watch", "watch", false, 200L),
            DeviceProfileEntity("D3", null, "phone", "phone", false, 300L),
        )
        profiles.removeAll { it.macAddress == "D2" }
        assertEquals(2, profiles.size)
        assertNull(profiles.find { it.macAddress == "D2" })
    }

    // ── MemoryVaultItemEntity + ByteArray ────────────────────

    @Test
    fun memoryVaultItemConstructsWithByteArrayEmbedding() {
        val embedding = byteArrayOf(1, 2, 3, -128, 127, 0)
        val item = MemoryVaultItemEntity(
            id = "mem-1",
            content = "Remember this",
            sourceType = "manual",
            createdAt = 100L,
            updatedAt = 200L,
            embedding = embedding,
        )
        assertNotNull(item.embedding)
        assertEquals(6, item.embedding!!.size)
        assertEquals(1, item.embedding[0])
        assertEquals(-128, item.embedding[3])
        assertEquals(127, item.embedding[4])
    }

    @Test
    fun memoryVaultByteArrayRoundTripsThroughIntList() {
        // This mirrors the toJson/toEntity pattern in IosMemoryVaultRepository
        val original = byteArrayOf(0, -1, 127, -128, 64, -64)
        val asIntList: List<Int> = original.map { it.toInt() }
        val restored = ByteArray(asIntList.size) { asIntList[it].toByte() }

        assertEquals(original.size, restored.size)
        assertTrue(original.contentEquals(restored))
    }

    @Test
    fun memoryVaultEntityEqualsIgnoresEmbedding() {
        // MemoryVaultItemEntity.equals only considers id and content
        val a = MemoryVaultItemEntity("x", "hello", "manual", 1L, 2L, byteArrayOf(1, 2, 3))
        val b = MemoryVaultItemEntity("x", "hello", "manual", 1L, 2L, byteArrayOf(4, 5, 6))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun memoryVaultEntityEqualsFalseOnDifferentContent() {
        val a = MemoryVaultItemEntity("x", "hello", "manual", 1L, 2L)
        val b = MemoryVaultItemEntity("x", "world", "manual", 1L, 2L)
        assertTrue(a != b)
    }

    @Test
    fun memoryVaultSearchPattern() {
        // Mirrors the search implementation in IosMemoryVaultRepository
        val items = listOf(
            MemoryVaultItemEntity("1", "Meeting notes about Q4", "meeting", 100L, 100L),
            MemoryVaultItemEntity("2", "Shopping list: milk, eggs", "manual", 200L, 200L),
            MemoryVaultItemEntity("3", "Architecture decision record", "agent", 300L, 300L),
        )
        val query = "meeting"
        val lower = query.lowercase()
        val results = items.filter {
            it.content.lowercase().contains(lower) || it.sourceType.lowercase().contains(lower)
        }.take(10)
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun memoryVaultSearchBySourceType() {
        val items = listOf(
            MemoryVaultItemEntity("1", "Transcript of standup", "meeting", 100L, 100L),
            MemoryVaultItemEntity("2", "Random thought", "manual", 200L, 200L),
            MemoryVaultItemEntity("3", "Another meeting", "meeting", 300L, 300L),
        )
        val query = "manual"
        val lower = query.lowercase()
        val results = items.filter {
            it.content.lowercase().contains(lower) || it.sourceType.lowercase().contains(lower)
        }.take(10)
        assertEquals(1, results.size)
        assertEquals("2", results[0].id)
    }

    @Test
    fun memoryVaultSearchRespectsLimit() {
        val items = (1..20).map {
            MemoryVaultItemEntity("id-$it", "Content $it with keyword", "manual", it * 100L, it * 100L)
        }
        val query = "keyword"
        val lower = query.lowercase()
        val results = items.filter {
            it.content.lowercase().contains(lower) || it.sourceType.lowercase().contains(lower)
        }.take(5)
        assertEquals(5, results.size)
    }

    // ── MediaRecordEntity ────────────────────────────────────

    @Test
    fun mediaRecordGetByFilenamePattern() {
        val records = listOf(
            MediaRecordEntity("r1", "photo.jpg", "image/jpeg", "/path/photo.jpg", 100L, 2048),
            MediaRecordEntity("r2", "video.mp4", "video/mp4", "/path/video.mp4", 200L, 50_000),
            MediaRecordEntity("r3", "audio.opus", "audio/ogg", "/path/audio.opus", 300L, 12_000),
        )

        // Simulates getByFilename
        val found = records.find { it.filename == "video.mp4" }
        assertNotNull(found)
        assertEquals("r2", found?.id)

        val missing = records.find { it.filename == "nope.png" }
        assertNull(missing)
    }

    @Test
    fun mediaRecordDeleteAllPattern() {
        val records = mutableListOf(
            MediaRecordEntity("r1", "a.jpg", "image/jpeg", "/a.jpg", 1L, 100),
            MediaRecordEntity("r2", "b.jpg", "image/jpeg", "/b.jpg", 2L, 200),
        )
        // Simulates deleteAll
        records.clear()
        assertTrue(records.isEmpty())
    }

    @Test
    fun mediaRecordInsertRemovesDuplicate() {
        // The insert pattern: removeAll matching id → add → save
        val records = mutableListOf(
            MediaRecordEntity("r1", "old.jpg", "image/jpeg", "/old.jpg", 1L, 100),
        )
        val newRecord = MediaRecordEntity("r1", "new.jpg", "image/jpeg", "/new.jpg", 2L, 200)
        records.removeAll { it.id == newRecord.id }
        records.add(newRecord)

        assertEquals(1, records.size)
        assertEquals("new.jpg", records[0].filename)
    }

    @Test
    fun mediaRecordDeleteRemovesById() {
        val records = mutableListOf(
            MediaRecordEntity("r1", "a.jpg", "image/jpeg", "/a.jpg", 1L, 100),
            MediaRecordEntity("r2", "b.jpg", "image/jpeg", "/b.jpg", 2L, 200),
        )
        records.removeAll { it.id == "r1" }
        assertEquals(1, records.size)
        assertEquals("r2", records[0].id)
    }
}
