package com.achyut.adglasses.memoryvault

import android.content.Context
import com.achyut.adglasses.memoryvault.crypto.VaultKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object MemoryVaultBootstrap {
    @Volatile
    private var initialized: Boolean = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                VaultKeyManager.ensureMasterKeyExists(context)
                MemoryModeManager.persistSnapshotBestEffort(context)
                runBlocking(Dispatchers.IO) {
                    MemoryMigrationService.ensureMigrated(context)
                    MemoryVaultService.enforceScreenOcrRetention(context)
                }
            }
            initialized = true
        }
    }
}
