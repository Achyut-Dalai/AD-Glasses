package com.sdk.glassessdksample

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.oudmon.ble.base.communication.utils.ByteUtil
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import com.sdk.glassessdksample.databinding.AcitivytMainBinding
import com.sdk.glassessdksample.ui.DeviceBindActivity
import com.sdk.glassessdksample.ui.BluetoothUtils
import com.sdk.glassessdksample.ui.BluetoothEvent
import com.sdk.glassessdksample.ui.bleIpBridge
import com.sdk.glassessdksample.ui.hasBluetooth
import com.sdk.glassessdksample.ui.requestAllPermission
import com.sdk.glassessdksample.ui.requestBluetoothPermission
import com.sdk.glassessdksample.ui.requestLocationPermission
import com.sdk.glassessdksample.ui.requestNearbyWifiDevicesPermission
import com.sdk.glassessdksample.ui.setOnClickListener
import com.sdk.glassessdksample.ui.startKtxActivity
import com.sdk.glassessdksample.ui.wifi.p2p.WifiP2pManagerSingleton
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import androidx.core.content.ContextCompat
import java.net.URL
import androidx.core.content.FileProvider
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

import android.provider.Settings
import android.net.Uri
import android.app.KeyguardManager

import android.speech.tts.TextToSpeech
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    companion object {
        const val ACTION_TASKER_COMMAND = "com.sdk.glassessdksample.ACTION_TASKER_COMMAND"
        const val EXTRA_TASKER_COMMAND = "tasker_command"
        private var loggedLargeDataHandlerMethods = false

        // Edit this URL before using the pull-mode OTA test button.
        // In the official app, the phone runs an HTTP server on its own
        // Wi‑Fi Direct address and the glasses fetch the file from there.
        // For experiments you can point this at a simple `python -m http.server`
        // instance on the phone or on a reachable host.
        private const val TEST_PULL_OTA_URL =
            "http://192.168.49.1:8080/dummy.swu"
    }

    private lateinit var binding: AcitivytMainBinding
    private val deviceNotifyListener by lazy { MyDeviceNotifyListener() }

    // AI Hijack settings
    private var isAiHijackEnabled = true // Default to enabled
    private var isImageAssistantMode = true // Use assistant vs share intent
    private var aiAssistantMode = "Gemini" // "Gemini" or "ChatGPT"

    // State used by the BLE+WiFi P2P data-download flow
    private var downloadP2pConnected = false
    private var downloadBleIp: String? = null
    private var downloadWifiIp: String? = null
    private var downloadInProgress = false
    private var downloadWifiP2pManager: WifiP2pManagerSingleton? = null
    private var downloadWifiP2pCallback: WifiP2pManagerSingleton.WifiP2pCallback? = null
    private var batteryPollJob: Job? = null
    private val batteryPollIntervalMs = 60_000L
    private var pendingBatteryToast = false
    private var batteryCallbackRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivytMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        logLargeDataHandlerMethodsOnce()
        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        // Ensure we always listen for glasses reports (battery, AI, volume, etc.)
        LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
        handleTaskerCommand(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        updateConnectionStatus(BleOperateManager.getInstance().isConnected)
        startBatteryPolling()
    }

    override fun onStop() {
        super.onStop()
        stopBatteryPolling()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
    inner class PermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {
                // Permissions not fully granted; do nothing for now
            } else {
                this@MainActivity.startKtxActivity<DeviceBindActivity>()
            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if(never){
                XXPermissions.startPermissionActivity(this@MainActivity, permissions);
            }
        }

    }


    override fun onResume() {
        super.onResume()
        try {
            if (!BluetoothUtils.isEnabledBluetooth(this)) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                startActivityForResult(intent, 300)
            }
        } catch (e: Exception) {
        }
        if (!hasBluetooth(this)) {
            requestBluetoothPermission(this, BluetoothPermissionCallback())
        }

        requestAllPermission(this, OnPermissionCallback { permissions, all ->  })

        // Check for Overlay permission needed for background launch
        if (isAiHijackEnabled && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
            Toast.makeText(this, "Please enable Overlay permission for background AI", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTaskerCommand(intent)
    }

    inner class BluetoothPermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {

            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if (never) {
                XXPermissions.startPermissionActivity(this@MainActivity, permissions)
            }
        }

    }

    private fun initView() {
        setOnClickListener(
            binding.btnScan,
            binding.btnConnect,
            binding.btnDisconnect,
            binding.btnAddListener,
            binding.btnSetTime,
            binding.btnVersion,
            binding.btnCamera,
            binding.btnVideo,
            binding.btnRecord,
            binding.btnThumbnail,
            binding.btnBt,
            binding.btnBattery,
            binding.btnVolume,
            binding.btnMediaCount,
            binding.btnDataDownload,
            binding.btnOtaInfo,
            binding.btnPullOtaTest,
            binding.btnModeGemini,
            binding.btnModeChatgpt,
            binding.btnModeTasker,
            binding.btnTestHijackVoice,
            binding.btnTestHijackImage
        ) {
            when (this) {
                binding.btnTestHijackVoice -> {
                    triggerAssistantVoiceQuery()
                }

                binding.btnTestHijackImage -> {
                    // Create a dummy image for testing if none exists
                    val testFile = File(getExternalFilesDir("DCIM"), "test_ai.jpg")
                    if (!testFile.exists()) {
                        try {
                            testFile.writeText("dummy image data")
                        } catch (e: Exception) {}
                    }
                    triggerAssistantImageQuery(testFile.absolutePath)
                }

                binding.btnModeGemini -> {
                    aiAssistantMode = "Gemini"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    Toast.makeText(this@MainActivity, "AI Mode: Google Gemini", Toast.LENGTH_SHORT).show()
                }

                binding.btnModeChatgpt -> {
                    aiAssistantMode = "ChatGPT"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    Toast.makeText(this@MainActivity, "AI Mode: ChatGPT", Toast.LENGTH_SHORT).show()
                }

                binding.btnModeTasker -> {
                    aiAssistantMode = "Tasker"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    Toast.makeText(this@MainActivity, "AI Mode: Tasker Broadcast", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnModeGemini.setTextColor(if (aiAssistantMode == "Gemini") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnModeChatgpt.setTextColor(if (aiAssistantMode == "ChatGPT") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnModeTasker.setTextColor(if (aiAssistantMode == "Tasker") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))

        binding.cbHijackEnabled.setOnCheckedChangeListener { _, isChecked ->

            isAiHijackEnabled = isChecked
            Toast.makeText(this, "Hijack ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.cbImageAsAssistant.isChecked = isImageAssistantMode
        binding.cbImageAsAssistant.text = if (isImageAssistantMode) "Direct Assistant" else "App Sharing"
        
        binding.cbImageAsAssistant.setOnCheckedChangeListener { _, isChecked ->
            isImageAssistantMode = isChecked
            val modeName = if (isChecked) "Direct Assistant" else "App Sharing"
            binding.cbImageAsAssistant.text = modeName
            Toast.makeText(this, "Image Hijack: $modeName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dumpOtaServerInfo() {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("OTAProbe", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
            if (response == null) {
                Log.e("OTAProbe", "syncDeviceInfo returned null response")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to read device info for OTA",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@syncDeviceInfo
            }

            val wifiHw = response.wifiHardwareVersion ?: ""
            val wifiFw = response.wifiFirmwareVersion ?: ""
            val btFw = response.firmwareVersion ?: ""
            val hw = response.hardwareVersion ?: ""

            // OTA binary URL used by the official app's debug/down path.
            val otaBinaryUrl =
                "https://qcwxfactory.oss-cn-beijing.aliyuncs.com/bin/glasses/${wifiHw}.swu"

            // Try to download the OTA file directly into the app's files dir
            // so you can pull it with `adb` for inspection.
            val otaDir = File(getExternalFilesDir(null), "ota")
            if (!otaDir.exists()) {
                otaDir.mkdirs()
            }
            val outFile = File(otaDir, "${wifiHw}.swu")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.i(
                        "OTAProbe",
                        "Attempting OTA binary download to: ${outFile.absolutePath}"
                    )
                    val url = URL(otaBinaryUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.use { input ->
                            FileOutputStream(outFile).use { output ->
                                val buffer = ByteArray(8 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        Log.i(
                            "OTAProbe",
                            "OTA binary download completed: ${outFile.absolutePath} (size=${outFile.length()} bytes)"
                        )
                    } else {
                        Log.e(
                            "OTAProbe",
                            "OTA binary download failed, HTTP ${conn.responseCode}"
                        )
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(
                        "OTAProbe",
                        "Exception while downloading OTA binary: ${e.message}",
                        e
                    )
                }
            }

            Log.i("OTAProbe", "==== OTA SERVER INFO START ====")
            Log.i("OTAProbe", "Device hardware version     : $hw")
            Log.i("OTAProbe", "WiFi hardware version       : $wifiHw")
            Log.i("OTAProbe", "WiFi firmware version       : $wifiFw")
            Log.i("OTAProbe", "Bluetooth firmware version  : $btFw")
            Log.i(
                "OTAProbe",
                "OTA metadata API (global)   : https://www.qlifesnap.com/glasses/app-update/last-ota"
            )
            Log.i(
                "OTAProbe",
                "OTA metadata API (China)    : https://www.qlifesnap.com/glasses/app-update/last-ota/china"
            )
            Log.i("OTAProbe", "OTA binary URL candidate    : $otaBinaryUrl")

            val lastOtaJsonTemplate = """
                {
                  "appId": <APP_ID>,
                  "uid": <USER_ID>,
                  "hardwareVersion": "$wifiHw",
                  "romVersion": "$wifiFw",
                  "os": 1,
                  "mac": "<PHONE_OR_BT_MAC>",
                  "country": "<COUNTRY_CODE>",
                  "dev": 2
                }
            """.trimIndent()

            Log.i("OTAProbe", "Sample LastOtaRequest JSON (fill in placeholders):")
            Log.i("OTAProbe", lastOtaJsonTemplate)
            Log.i(
                "OTAProbe",
                "Sample curl (metadata): curl -X POST 'https://www.qlifesnap.com/glasses/app-update/last-ota' -H 'Content-Type: application/json' -d '<JSON_ABOVE>'"
            )
            Log.i(
                "OTAProbe",
                "Sample curl (binary)  : curl -o '${wifiHw}.swu' '$otaBinaryUrl'"
            )
            Log.i("OTAProbe", "==== OTA SERVER INFO END ====")

            runOnUiThread {
                Toast.makeText(
                    this,
                    "OTA server info dumped to logcat (tag: OTAProbe)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Minimal wrapper around LargeDataHandler.writeIpToSoc so we can observe
     * how the glasses behave when asked to fetch an OTA image from an HTTP
     * server under our control.
     *
     * This does not start any HTTP server on the phone; you must run one
     * yourself and point TEST_PULL_OTA_URL at it.
     */
    private fun testPullModeOta() {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("PullOtaTest", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val url = TEST_PULL_OTA_URL
        if (url.isBlank()) {
            Log.e("PullOtaTest", "TEST_PULL_OTA_URL is blank; edit MainActivity to set it.")
            Toast.makeText(
                this,
                "TEST_PULL_OTA_URL is blank. Edit MainActivity first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Log.i("PullOtaTest", "Calling writeIpToSoc with URL: $url")
        LargeDataHandler.getInstance().writeIpToSoc(url) { cmdType, response ->
            Log.i(
                "PullOtaTest",
                "writeIpToSoc callback: cmdType=$cmdType, response=$response"
            )
        }
    }
    
    private fun controlVideoRecording(start: Boolean) {
        val value = if (start) 0x02 else 0x03
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, value.toByte())
        ) { _, it ->
            if (it.dataType == 1) {
                if (it.errorCode == 0) {
                    when (it.workTypeIng) {
                        2 -> {
                            //Glasses are recording video
                        }
                        4 -> {
                            //Glasses are in transfer mode
                        }
                        5 -> {
                            //Glasses are in OTA mode
                        }
                        1, 6 ->{
                            //Glasses are in camera mode
                        }
                        7 -> {
                            //Glasses are in AI conversation
                        }
                        8 ->{
                            //Glasses are in recording mode
                        }
                    }
                } else {
                    //Execute start and end
                }
            }
        }
    }
    
    private fun controlAudioRecording(start: Boolean) {
        val value = if (start) 0x08 else 0x0c
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, value.toByte())
        ) { _, it ->
            if (it.dataType == 1) {
                if (it.errorCode == 0) {
                    when (it.workTypeIng) {
                        2 -> {
                            //Glasses are recording video
                        }
                        4 -> {
                            //Glasses are in transfer mode
                        }
                        5 -> {
                            //Glasses are in OTA mode
                        }
                        1, 6 ->{
                            //Glasses are in camera mode
                        }
                        7 -> {
                            //Glasses are in AI conversation
                        }
                        8 ->{
                            //Glasses are in recording mode
                        }
                    }
                } else {
                    //Execute start and end
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        updateConnectionStatus(event.connect)
        if (event.connect) {
            requestBatteryStatus(showToast = false)
        } else {
            updateBatteryText(null)
        }
    }

    private fun startBatteryPolling() {
        if (batteryPollJob?.isActive == true) {
            return
        }
        batteryPollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (BleOperateManager.getInstance().isConnected) {
                    requestBatteryStatus(showToast = false)
                } else {
                    updateBatteryText(null)
                }
                delay(batteryPollIntervalMs)
            }
        }
    }

    private fun stopBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = null
    }

    private fun sendAiBroadcast(type: String, path: String? = null) {
        val intent = Intent("com.sdk.glassessdksample.AI_EVENT").apply {
            putExtra("type", type)
            path?.let { putExtra("path", it) }
            putExtra("assistant", aiAssistantMode)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendBroadcast(intent)
        Log.i("AIHijack", "Sent Broadcast to Tasker: $type")
    }

    private fun triggerAssistantVoiceQuery() {
        Log.i("AIHijack", "Triggering Voice Query for $aiAssistantMode")
        
        if (aiAssistantMode == "Tasker") {
            speak("Sending voice command to Tasker")
            sendAiBroadcast("voice")
            return
        }

        speak("Opening voice assistant")

        
        // Wake up screen if locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // Tell glasses to stop proprietary AI audio stream
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0b)) { _, _ -> }

        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (aiAssistantMode == "ChatGPT") {
                    // Try to target ChatGPT specifically if possible, 
                    // otherwise default assistant will handle it if user set it to ChatGPT
                    setPackage("com.openai.chatgpt")
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AIHijack", "Failed to trigger assistant: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Assistant not found or failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerAssistantImageQuery(imagePath: String) {
        Log.i("AIHijack", "Triggering Image Query for $aiAssistantMode with $imagePath")
        
        if (aiAssistantMode == "Tasker") {
            speak("Sending image to Tasker")
            sendAiBroadcast("image", imagePath)
            return
        }

        speak("Analyzing what you see")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // Stop glasses AI mode
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0b)) { _, _ -> }

        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e("AIHijack", "Image file does not exist: $imagePath")
                return
            }

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            if (isImageAssistantMode) {
                Log.i("AIHijack", "Using Direct Assistant (Google Lens / Gemini Visual) mode")
                
                // For Google/Gemini, the best "Direct" experience is Google Lens.
                // It specifically handles visual analysis and has a Gemini toggle.
                val lensIntent = Intent(Intent.ACTION_SEND).apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                // Try to find the Lens activity to avoid the "Google vs Tasker" chooser
                val activities = packageManager.queryIntentActivities(lensIntent, 0)
                var lensComponentFound = false
                for (info in activities) {
                    if (info.activityInfo.name.contains("lens", ignoreCase = true)) {
                        lensIntent.setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        lensComponentFound = true
                        break
                    }
                }

                if (lensComponentFound) {
                    startActivity(lensIntent)
                } else {
                    // Fallback to deep link for Google Lens
                    val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("googlelens://v1/open?url=" + android.net.Uri.encode(uri.toString()))
                        setPackage("com.google.android.googlequicksearchbox")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    try {
                        startActivity(deepLinkIntent)
                    } catch (e: Exception) {
                        // If everything fails, use the generic chooser but target Google app
                        startActivity(Intent.createChooser(lensIntent, "Visual Search"))
                    }
                }
            } else {
                Log.i("AIHijack", "Using Share Intent mode")
                // Reverting to the version that shows the app selection chooser
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // Request spoken answer for Gemini
                    val prompt = if (aiAssistantMode == "Gemini") "Tell me what you see out loud" else "Tell me about this"
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                
                startActivity(Intent.createChooser(intent, "Ask Assistant"))
            }
        } catch (e: Exception) {
            Log.e("AIHijack", "Failed to trigger image query: ${e.message}")
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        val deviceName = DeviceManager.getInstance().deviceName
        val status = if (connected) {
            if (!deviceName.isNullOrBlank()) {
                "Connected - $deviceName"
            } else {
                "Connected"
            }
        } else {
            "Disconnected"
        }
        binding.statusText.text = status
        if (!connected) {
            updateBatteryText(null)
        }
    }

    private fun updateBatteryText(battery: Int?) {
        binding.batteryText.text = battery?.let { "$it%" } ?: "--%"
    }

    private fun requestBatteryStatus(showToast: Boolean) {
        if (showToast) {
            pendingBatteryToast = true
            Toast.makeText(this@MainActivity, "Requesting battery level…", Toast.LENGTH_SHORT).show()
        }
        ensureBatteryCallback()
        // Trigger battery sync
        LargeDataHandler.getInstance().syncBattery()
    }

    private fun ensureBatteryCallback() {
        if (batteryCallbackRegistered) {
            return
        }
        batteryCallbackRegistered = true
        // Add battery listener. According to the SDK docs this
        // callback is invoked when syncBattery completes.
        LargeDataHandler.getInstance().addBatteryCallBack("init") { _, response ->
            val result = parseBatteryResponse(response)
            Log.i("BatteryCallback", result.message)
            runOnUiThread {
                updateBatteryText(result.battery)
                if (pendingBatteryToast) {
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    pendingBatteryToast = false
                }
            }
        }
    }

    private data class BatteryResult(
        val battery: Int?,
        val charging: Boolean?,
        val message: String
    )

    private fun parseBatteryResponse(response: Any?): BatteryResult {
        if (response == null) {
            return BatteryResult(null, null, "Battery callback: null response")
        }
        return try {
            val clazz = response.javaClass
            val batteryField = clazz.getDeclaredField("battery").apply {
                isAccessible = true
            }
            val chargingField = clazz.getDeclaredField("charging").apply {
                isAccessible = true
            }

            val battery = batteryField.getInt(response)
            val charging = chargingField.getBoolean(response)
            val message =
                "Battery: $battery% (${if (charging) "charging" else "not charging"})"
            BatteryResult(battery, charging, message)
        } catch (e: Exception) {
            Log.e("BatteryCallback", "Failed to parse BatteryResponse", e)
            BatteryResult(null, null, "Battery: $response")
        }
    }

    private fun handleBatteryReport(battery: Int, charging: Boolean) {
        val message = "Battery: $battery% (${if (charging) "charging" else "not charging"})"
        Log.i("BatteryCallback", message)
        runOnUiThread {
            updateBatteryText(battery)
            if (pendingBatteryToast) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                pendingBatteryToast = false
            }
        }
    }

    private fun handleTaskerCommand(startIntent: Intent?) {
        if (startIntent == null) return

        val isFromTaskerAction = startIntent.action == ACTION_TASKER_COMMAND
        val command = startIntent.getStringExtra(EXTRA_TASKER_COMMAND)

        if (!isFromTaskerAction && command.isNullOrBlank()) {
            return
        }

        val normalizedCommand = command?.lowercase() ?: return

        when (normalizedCommand) {
            "scan" -> binding.btnScan.performClick()
            "connect" -> binding.btnConnect.performClick()
            "disconnect" -> binding.btnDisconnect.performClick()
            "add_listener" -> binding.btnAddListener.performClick()
            "set_time" -> binding.btnSetTime.performClick()
            "version" -> binding.btnVersion.performClick()
            "camera" -> binding.btnCamera.performClick()

            // Video recording controls
            "video" -> binding.btnVideo.performClick()
            "video_start" -> controlVideoRecording(true)
            "video_stop" -> controlVideoRecording(false)

            // Audio recording controls
            "record" -> binding.btnRecord.performClick()
            "record_start" -> controlAudioRecording(true)
            "record_stop" -> controlAudioRecording(false)

            "thumbnail" -> binding.btnThumbnail.performClick()
            "bt_scan" -> binding.btnBt.performClick()
            "battery" -> binding.btnBattery.performClick()
            "volume" -> binding.btnVolume.performClick()
            "media_count" -> binding.btnMediaCount.performClick()
            "data_download" -> binding.btnDataDownload.performClick()
        }
    }

    private fun startDataDownload() {
        Log.i("DataDownload", "Starting BLE+WiFi P2P data download...")

        // Check Bluetooth connection status
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("DataDownload", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Check NEARBY_WIFI_DEVICES on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !XXPermissions.isGranted(this, "android.permission.NEARBY_WIFI_DEVICES")
        ) {
            Log.e("DataDownload", "NEARBY_WIFI_DEVICES permission not granted")
            Toast.makeText(
                this,
                "NEARBY_WIFI_DEVICES permission not granted.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Reset state for a fresh run
        downloadP2pConnected = false
        downloadBleIp = null
        downloadWifiIp = null
        downloadInProgress = false

        val wifiP2pManager = WifiP2pManagerSingleton.getInstance(this)
        downloadWifiP2pManager = wifiP2pManager

        // Register receiver and listen for P2P state/peer changes
        wifiP2pManager.registerReceiver()

        val callback = object : WifiP2pManagerSingleton.WifiP2pCallback {
            override fun onWifiP2pEnabled() {
                Log.i("DataDownload", "WiFi P2P enabled")
            }

            override fun onWifiP2pDisabled() {
                Log.e("DataDownload", "WiFi P2P disabled")
            }

            override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                Log.i("DataDownload", "Found ${peers.size} P2P devices")
                // Connect to the first available peer (the official app
                // filters by name/MAC; here we keep it simple).
                val target = peers.firstOrNull()
                if (target != null) {
                    Log.i(
                        "DataDownload",
                        "Connecting to peer: ${target.deviceName} / ${target.deviceAddress}"
                    )
                    wifiP2pManager.connectToDevice(target)
                }
            }

            override fun onThisDeviceChanged(device: WifiP2pDevice) {
                Log.i(
                    "DataDownload",
                    "This device changed: ${device.deviceName} - ${device.status}"
                )
            }

            override fun onConnected(info: WifiP2pInfo) {
                Log.i(
                    "DataDownload",
                    "P2P connected: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}"
                )
                onDownloadP2pConnected(info)
            }

            override fun onDisconnected() {
                Log.i("DataDownload", "P2P disconnected")
                downloadP2pConnected = false
            }

            override fun onPeerDiscoveryStarted() {
                Log.i("DataDownload", "Peer discovery started")
            }

            override fun onPeerDiscoveryFailed(reason: Int) {
                Log.e("DataDownload", "Peer discovery failed: $reason")
            }

            override fun onConnectRequestSent() {
                Log.i("DataDownload", "Connect request sent")
            }

            override fun onConnectRequestFailed(reason: Int) {
                Log.e("DataDownload", "Connect request failed: $reason")
            }

            override fun connecting() {
                Log.i("DataDownload", "Connecting to P2P device...")
            }

            override fun cancelConnect() {
                Log.i("DataDownload", "P2P connection cancelled")
            }

            override fun cancelConnectFail(reason: Int) {
                Log.e("DataDownload", "Cancel connect failed: $reason")
            }

            override fun retryAlsoFailed() {
                Log.e("DataDownload", "P2P connection retry failed")
            }
        }

        downloadWifiP2pCallback = callback
        wifiP2pManager.addCallback(callback)

        // Start scanning for the glasses over WiFi Direct
        wifiP2pManager.startPeerDiscovery()

        // Ask the glasses (over BLE) to bring up WiFi/P2P and report their IP,
        // mirroring the official app's importAlbum() flow.
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x04)
        ) { _, resp ->
            Log.i(
                "DataDownload",
                "glassesControl[0x02,0x01,0x04] -> dataType=${resp.dataType}, error=${resp.errorCode}"
            )
        }
    }
    
    private fun getDeviceIpFromBLE(): String? {
        // Prefer IP detected from BLE notifications, fall back to the
        // known sample IP if we have not seen one yet.
        val ipFromBle = bleIpBridge.ip.value
        if (!ipFromBle.isNullOrEmpty()) {
            Log.i("DataDownload", "Device IP from BleIpBridge: $ipFromBle")
            return ipFromBle
        }
        // Fallback: last-known IP used by the official app logs
        return "192.168.49.79"
    }
    
    private fun downloadMediaList(deviceIp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "http://$deviceIp/files/media.config"
                Log.i("DataDownload", "Downloading media list from: $url")
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val content = inputStream.bufferedReader().use { it.readText() }
                    
                    // Show downloaded content
                    Log.i("DataDownload", "=== MEDIA CONFIG CONTENT ===")
                    Log.i("DataDownload", content)
                    Log.i("DataDownload", "=== END MEDIA CONFIG ===")
                    
                    // Parse media file list
                    parseMediaList(content)
                    
                    withContext(Dispatchers.Main) {
                        showDownloadSuccess("Media list downloaded successfully")
                    }
                } else {
                    Log.e("DataDownload", "Failed to download media list. Response code: ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        showDownloadError("Failed to download media list. Response code: ${connection.responseCode}")
                    }
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                    Log.e("DataDownload", "Error downloading media list: ${e.message}", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        when (e) {
                            is java.io.IOException -> {
                                if (e.message?.contains("Cleartext HTTP traffic") == true) {
                                    showDownloadError("Network security blocked HTTP connection. Please check app settings.")
                                } else if (e.message?.contains("Failed to connect") == true) {
                                    showDownloadError("Cannot connect to glasses device. Please ensure P2P connection is established.")
                                } else {
                                    showDownloadError("Network error: ${e.message}")
                                }
                            }
                            else -> showDownloadError("Download failed: ${e.message}")
                        }
                    }
                }
            }
        }
    
        private fun parseMediaList(content: String) {
            // Parse the media configuration file content - this is a text file containing JPG file names
            Log.i("DataDownload", "Parsing media list content...")
            
            try {
                // Split by line, each line should be a file name
                val lines = content.trim().lines()
                val jpgFiles = mutableListOf<String>()
                
                lines.forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty()) {
                        // Check if it is a JPG file
                        if (trimmedLine.endsWith(".jpg", ignoreCase = true) ||
                            trimmedLine.endsWith(".jpeg", ignoreCase = true)
                        ) {
                            jpgFiles.add(trimmedLine)
                            Log.i("DataDownload", "Found JPG file: $trimmedLine")
                        } else {
                            Log.i("DataDownload", "Found non-JPG file: $trimmedLine")
                        }
                    }
                }
                
                Log.i("DataDownload", "Total JPG files found: ${jpgFiles.size}")
                
                if (jpgFiles.isNotEmpty()) {
                    // Start downloading all JPG files
                    downloadAllJpgFiles(jpgFiles)
                } else {
                    Log.w("DataDownload", "No JPG files found in media.config")
                    CoroutineScope(Dispatchers.Main).launch {
                        showDownloadError("No JPG files found in media.config")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("DataDownload", "Error parsing media list: ${e.message}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    showDownloadError("Failed to parse media list: ${e.message}")
                }
            }
        }
    
    private fun downloadAllJpgFiles(jpgFiles: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i("DataDownload", "Starting download of ${jpgFiles.size} JPG files...")
            
            var successCount = 0
            var failCount = 0
            
            for ((index, fileName) in jpgFiles.withIndex()) {
                try {
                    Log.i("DataDownload", "Downloading file ${index + 1}/${jpgFiles.size}: $fileName")
                    
                    val success = downloadSingleJpgFile(fileName)
                    if (success) {
                        successCount++
                        Log.i("DataDownload", "✓ Successfully downloaded: $fileName")
                    } else {
                        failCount++
                        Log.e("DataDownload", "✗ Failed to download: $fileName")
                    }
                    
                    // Add a small delay to avoid excessively fast requests
                    delay(500)
                    
                } catch (e: Exception) {
                    failCount++
                    Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
                }
            }
            
            // Show final result
            val message = "Download completed: $successCount successful, $failCount failed"
            Log.i("DataDownload", message)
            
            withContext(Dispatchers.Main) {
                if (failCount == 0) {
                    showDownloadSuccess("All $successCount files downloaded successfully!")
                } else {
                    showDownloadError("Download completed with errors: $successCount successful, $failCount failed")
                }
            }
        }
    }
    
    private suspend fun downloadSingleJpgFile(fileName: String): Boolean {
        return try {
            val deviceIp = getDeviceIpFromBLE() ?: return false
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "Downloading: $url")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val file = File(getExternalFilesDir("DCIM"), fileName)
                val outputStream = FileOutputStream(file)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                
                outputStream.close()
                inputStream.close()
                
                Log.i("DataDownload", "File downloaded: $fileName ($totalBytes bytes)")
                
                // Save to album
                saveToAlbum(file, fileName)
                
                true
            } else {
                Log.e("DataDownload", "Failed to download $fileName. Response code: ${connection.responseCode}")
                false
            }
            
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
            false
        }
    }
    
    private fun saveToAlbum(file: File, fileName: String) {
        try {
            // Save file information to album database
            val albumInfo = mapOf(
                "fileName" to fileName,
                "filePath" to file.absolutePath,
                "fileDate" to "2025-08-18",
                "fileType" to 1,
                "timestamp" to System.currentTimeMillis(),
                "mac" to "71:33:1D:2C:CF:A0"
            )
            
            Log.i("DataDownload", "Album info: $albumInfo")
            // TODO: Implement the logic of saving to the album database
            
        } catch (e: Exception) {
            Log.e("DataDownload", "Error saving to album: ${'$'}{e.message}", e)
        }
    }
    
    private fun cleanupP2pAfterDownload() {
        val manager = downloadWifiP2pManager
        val callback = downloadWifiP2pCallback
        if (manager != null && callback != null) {
            manager.removeCallback(callback)
        }
        manager?.removeGroup { success ->
            Log.i("DataDownload", "P2P group removed: $success")
        }
        manager?.unregisterReceiver()
        downloadWifiP2pManager = null
        downloadWifiP2pCallback = null
        downloadP2pConnected = false
        downloadInProgress = false
    }

    private fun showDownloadSuccess(message: String) {
        cleanupP2pAfterDownload()
        Log.i("DataDownload", "SUCCESS: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showDownloadError(message: String) {
        cleanupP2pAfterDownload()
        Log.e("DataDownload", "ERROR: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Debug helper: log all methods on LargeDataHandler so we can
     * discover additional SDK capabilities (such as WiFi transfer APIs)
     * without needing decompiled sources.
     */
    private fun logLargeDataHandlerMethodsOnce() {
        if (loggedLargeDataHandlerMethods) return
        loggedLargeDataHandlerMethods = true
        try {
            val clazz = LargeDataHandler.getInstance()::class.java
            val methods = clazz.declaredMethods
            for (m in methods) {
                val params = m.parameterTypes.joinToString(",") { it.simpleName ?: it.name }
                val ret = m.returnType.simpleName ?: m.returnType.name
                Log.i("LDHMethods", "method=${m.name}, params=($params), return=$ret")
            }
        } catch (e: Exception) {
            Log.e("LDHMethods", "Failed to introspect LargeDataHandler methods", e)
        }
    }

    private fun testConnection(deviceIp: String): Boolean {
        Log.i("DataDownload", "Testing connection to $deviceIp...")
        try {
            // Try to connect to the actual media configuration file
            val url = URL("http://$deviceIp/files/media.config")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // Connection timeout
            connection.readTimeout = 5000 // Read timeout
            
            val responseCode = connection.responseCode
            Log.i("DataDownload", "Connection test response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Try to read a small amount of content to confirm that the connection is available
                val inputStream = connection.inputStream
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                inputStream.close()
                
                Log.i("DataDownload", "Connection test successful - read $bytesRead bytes")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("DataDownload", "Connection test failed: ${e.message}", e)
            return false
        }
    }

    private fun onDownloadBleIp(ip: String) {
        Log.i("DataDownload", "BLE reported device WiFi IP: $ip")
        downloadBleIp = ip
        maybeStartHttpDownload("BLE")
    }

    private fun onDownloadP2pConnected(info: WifiP2pInfo) {
        downloadP2pConnected = info.groupFormed
        downloadWifiIp = info.groupOwnerAddress?.hostAddress
        Log.i("DataDownload", "onDownloadP2pConnected: p2pConnected=$downloadP2pConnected, wifiIp=$downloadWifiIp")
        maybeStartHttpDownload("P2P")
    }

    private fun maybeStartHttpDownload(source: String) {
        if (downloadInProgress) {
            Log.i("DataDownload", "Download already in progress, ignoring trigger from $source")
            return
        }
        // Prefer an IP we explicitly saw from the device:
        // 1) IP reported in 0x08 notify (downloadBleIp)
        // 2) IP parsed by BleIpBridge from BLE payloads
        // 3) As a last resort, the WiFi P2P group owner address
        val bridgeIp = bleIpBridge.ip.value
        val ip = downloadBleIp ?: bridgeIp ?: downloadWifiIp
        if (!downloadP2pConnected || ip.isNullOrEmpty()) {
            Log.i(
                "DataDownload",
                "Not ready yet from $source. p2p=$downloadP2pConnected, bleIp=$downloadBleIp, wifiIp=$downloadWifiIp, bleBridgeIp=$bridgeIp"
            )
            return
        }

        downloadInProgress = true
        Log.i("DataDownload", "Conditions satisfied from $source, starting HTTP download from $ip")
        downloadMediaList(ip)
    }

    inner class MyDeviceNotifyListener : GlassesDeviceNotifyListener() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            Log.i(
                "DeviceNotify",
                "cmdType=$cmdType, loadData=${response.loadData.joinToString(separator = ",") { it.toInt().toString() }}"
            )
            when (response.loadData[6].toInt()) {
                //Glasses battery report
                0x05 -> {
                    //Current battery
                    val battery = response.loadData[7].toInt()
                    //Is it charging
                    val changing = response.loadData[8].toInt()
                    handleBatteryReport(battery, changing == 1)
                }
                //Glasses pass quick recognition / AI Photo
                0x02 -> {
                    Log.i("DeviceNotify", "AI Photo Button Pressed - Starting Chunked Download")
                    val fileName = "AI_Thumb_${System.currentTimeMillis()}.jpg"
                    val file = File(getExternalFilesDir("DCIM"), fileName)
                    
                    // The SDK sends the image in multiple chunks. 
                    // We must append them to the file and wait for isComplete (success parameter).
                    LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
                        if (data != null) {
                            try {
                                FileOutputStream(file, true).use { it.write(data) }
                                if (isComplete) {
                                    Log.i("DeviceNotify", "Thumbnail transfer complete: ${file.absolutePath} (${file.length()} bytes)")
                                    if (isAiHijackEnabled) {
                                        triggerAssistantImageQuery(file.absolutePath)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("DeviceNotify", "Failed to write thumbnail chunk: ${e.message}")
                            }
                        }
                    }
                }

                //Glasses activate microphone / AI button
                0x03 -> {
                    if (response.loadData[7].toInt() == 1) {
                        Log.i("DeviceNotify", "AI Button Pressed - Hijacking to Phone Assistant")
                        if (isAiHijackEnabled) {
                            triggerAssistantVoiceQuery()
                        } else {
                            //The glasses activate the microphone to start speaking
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Glasses microphone activated (Original Path)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                //ota upgrade
                0x04 -> {
                    try {
                        val download = response.loadData[7].toInt()
                        val soc = response.loadData[8].toInt()
                        val nor = response.loadData[9].toInt()
                        //download firmware download progress soc download progress nor upgrade progress
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                0x0c -> {
                    //The glasses trigger a pause event, voice broadcast
                    if (response.loadData[7].toInt() == 1) {
                        //to do
                    }
                }

                0x0d -> {
                    //Unbind APP event
                    if (response.loadData[7].toInt() == 1) {
                        //to do
                    }
                }
                //Glasses memory low event
                0x0e -> {

                }
                //Translation pause event
                0x10 -> {

                }
                //Glasses volume change event
                0x12 -> {
                    //Music volume
                    //Minimum volume
                    response.loadData[8].toInt()
                    //Maximum volume
                    response.loadData[9].toInt()
                    //Current volume
                    response.loadData[10].toInt()

                    //Incoming call volume
                    //Minimum volume
                    response.loadData[12].toInt()
                    //Maximum volume
                    response.loadData[13].toInt()
                    //Current volume
                    response.loadData[14].toInt()

                    //Glasses system volume
                    //Minimum volume
                    response.loadData[16].toInt()
                    //Maximum volume
                    response.loadData[17].toInt()
                    //Current volume
                    response.loadData[18].toInt()

                    //Current volume mode
                    val mode = response.loadData[19].toInt()

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Volume changed (mode=$mode)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
                // Glasses report WiFi IP for data download
                0x08 -> {
                    if (response.loadData.size >= 11) {
                        val ip = "${ByteUtil.byteToInt(response.loadData[7])}." +
                                "${ByteUtil.byteToInt(response.loadData[8])}." +
                                "${ByteUtil.byteToInt(response.loadData[9])}." +
                                "${ByteUtil.byteToInt(response.loadData[10])}"
                        Log.i("DeviceNotify", "BLE reported WiFi IP: $ip")
                        onDownloadBleIp(ip)
                    } else {
                        Log.w(
                            "DeviceNotify",
                            "0x08 notify with too-short payload, size=${response.loadData.size}"
                        )
                    }
                }
                // Glasses report P2P / WiFi error during data download
                0x09 -> {
                    val raw = response.loadData.getOrNull(7) ?: 0
                    val errorCode = ByteUtil.byteToInt(raw)
                    Log.e("DeviceNotify", "P2P/WiFi error from device: $errorCode (raw=$raw)")
                    if (errorCode == 255) {
                        // Mirror the official app: ask the glasses/phone P2P
                        // layer to reset, but do NOT treat this as a fatal
                        // error for the whole download flow. The official app
                        // still proceeds to receive an IP and download.
                        WifiP2pManagerSingleton.getInstance(this@MainActivity).resetDeviceP2p()
                    }
                }
            }
        }
    }
}
