package com.ad_glasses.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ad_glasses.agent.LocalAgentPrefs
import com.ad_glasses.ai.orchestrator.AssistantConversationSession
import com.ad_glasses.devices.DeviceProfileStore
import com.ad_glasses.glasses.runtime.ADActivityRuntimeRegistry
import com.ad_glasses.localagent.daily.DailyFactsReminderScheduler
import com.ad_glasses.media.autocapture.AutoAudioCapturePrefs
import com.ad_glasses.media.autocapture.AutoAudioCaptureService
import com.ad_glasses.memoryvault.MemoryVaultBootstrap
import com.ad_glasses.plugins.localagent.LocalAgentPlugin
import com.ad_glasses.shared.platform.ADGlassesServices
import com.ad_glasses.shared.platform.initPlatformPreferences
import com.ad_glasses.ui.localization.AppLanguagePreferences
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application process owner for native services, device transport and the Compose product UI. */
class MyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var hardwareVersion: String = ""
    var firmwareVersion: String = ""

    override fun onCreate() {
        super.onCreate()
        application = this
        instance = this
        CONTEXT = applicationContext

        ADActivityRuntimeRegistry.install(this)

        AppLanguagePreferences.applyStoredLocale(this)
        initBle()

        // Keep the BLE control channel connected while the app process is alive.
        // This matches user expectations from the official HeyCyan companion app.
        AutoPairManager.start(this)

        // Local Agent: ensure daily reminder schedule matches current prefs.
        DailyFactsReminderScheduler.scheduleIfEnabled(
            context = this,
            enabled = LocalAgentPrefs.isDailyFactsReminderEnabled(this),
        )
        LocalAgentPlugin.syncNativePluginState(this)

        // Auto audio capture (glasses recording loop)
        if (AutoAudioCapturePrefs.isEnabled(this) &&
            !DeviceProfileStore.isEyevueSelected(this) &&
            !AutoAudioCaptureService.isRunning()
        ) {
            AutoAudioCaptureService.start(this)
        }

        runCatching { MemoryVaultBootstrap.ensureInitialized(this) }

        // AD conversations are intentionally ephemeral. Clean expired threads off the main
        // thread at startup; creating the session also installs the periodic retention backstop.
        appScope.launch {
            runCatching {
                AssistantConversationSession.get(this@MyApplication).pruneExpiredConversations()
            }
        }

        // Initialize KMP shared services
        runCatching { initPlatformPreferences(this) }
        runCatching { initSharedServices() }
    }

    /** Initialize KMP shared services with Android implementations. */
    private fun initSharedServices() {
        if (ADGlassesServices.isInitialized()) return

        val androidBleManager = com.ad_glasses.shared.ble.AndroidBleManager(
            bleOperateManager = BleOperateManager.getInstance(),
            largeDataHandler = LargeDataHandler.getInstance(),
            deviceManager = com.oudmon.ble.base.bluetooth.DeviceManager.getInstance(),
        )
        val androidWifiP2pManager = AndroidWifiP2pManagerWrapper()
        val chatRepo = AndroidChatRepositoryWrapper()
        val notesRepo = AndroidNotesRepositoryWrapper()
        val deviceRepo = AndroidDeviceProfileRepositoryWrapper()
        val vaultRepo = AndroidMemoryVaultRepositoryWrapper()
        val mediaRepo = AndroidMediaRecordRepositoryWrapper()
        val chatAi = AndroidChatAiService(this)
        val voiceAi = AndroidVoiceAiService()
        val imageAi = AndroidImageAiService()
        val modelRegistry = AndroidAiModelRegistry()

        ADGlassesServices.initialize(
            bleManager = androidBleManager,
            wifiP2pManager = androidWifiP2pManager,
            chatRepository = chatRepo,
            notesRepository = notesRepo,
            deviceProfileRepository = deviceRepo,
            memoryVaultRepository = vaultRepo,
            mediaRecordRepository = mediaRepo,
            chatAiService = chatAi,
            voiceAiService = voiceAi,
            imageAiService = imageAi,
            aiModelRegistry = modelRegistry,
        )
    }

    private fun initBle() {
        initReceiver()
        val intentFilter = BleAction.getIntentFilter()
        val myBleReceiver = MyBluetoothReceiver()
        LocalBroadcastManager.getInstance(CONTEXT)
            .registerReceiver(myBleReceiver, intentFilter)
        BleBaseControl.getInstance(CONTEXT).setmContext(this)
    }

    private fun initReceiver() {
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()
        val deviceFilter: IntentFilter = BleAction.getDeviceIntentFilter()
        val deviceReceiver = BluetoothReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(deviceReceiver, deviceFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(deviceReceiver, deviceFilter)
        }
    }

    fun getDeviceIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        return intentFilter
    }

    fun getAppRootFile(context: Context): File {
        return if (context.getExternalFilesDir("") != null) {
            context.getExternalFilesDir("")!!
        } else {
            val externalSaveDir = context.externalCacheDir
            externalSaveDir ?: context.cacheDir
        }
    }

    companion object {
        private var application: Application? = null
        private var _context: Context? = null
        var CONTEXT: Context
            get() {
                val ctx = _context
                if (ctx != null) return ctx
                val testCtx = runCatching {
                    val clazz = Class.forName("androidx.test.core.app.ApplicationProvider")
                    val method = clazz.getMethod("getApplicationContext")
                    method.invoke(null) as? Context
                }.getOrNull()
                return testCtx ?: throw IllegalStateException("Property CONTEXT should be initialized before being accessed.")
            }
            set(value) {
                _context = value
            }
        private lateinit var instance: MyApplication

        val database: com.ad_glasses.data.local.AppDatabase by lazy {
            androidx.room.Room.databaseBuilder(
                CONTEXT,
                com.ad_glasses.data.local.AppDatabase::class.java,
                "ADGlasses-db",
            )
                .addMigrations(
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_1_2,
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_2_3,
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_3_4,
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_4_5,
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_5_6,
                    com.ad_glasses.data.local.AppDatabase.MIGRATION_6_7,
                )
                .addCallback(
                    object : androidx.room.RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            runCatching {
                                com.ad_glasses.data.local.AppDatabase.MIGRATION_4_5.migrate(db)
                                com.ad_glasses.data.local.AppDatabase.MIGRATION_5_6.migrate(db)
                                com.ad_glasses.data.local.AppDatabase.MIGRATION_6_7.migrate(db)
                            }
                        }

                        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onOpen(db)
                            com.ad_glasses.data.local.AppDatabase.ensureMemorySearchIndex(db)
                        }
                    },
                )
                .build()
        }

        val repository: com.ad_glasses.data.repository.ADGlassesRepository by lazy {
            com.ad_glasses.data.repository.ADGlassesRepository(database)
        }

        val summarizationService: com.ad_glasses.shared.notes.SummarizationService by lazy {
            com.ad_glasses.ai.summarization.AiSummarizationService(CONTEXT)
        }

        val notesRepository: com.ad_glasses.notes.NotesRepository by lazy {
            com.ad_glasses.notes.RoomNotesRepository(
                noteDao = database.noteDao(),
                summarizationService = summarizationService,
            )
        }

        fun getApplication(): Application {
            return application
                ?: throw RuntimeException("Application not initialized. onCreate not yet called.")
        }

        fun getInstance(): MyApplication = instance
    }
}
