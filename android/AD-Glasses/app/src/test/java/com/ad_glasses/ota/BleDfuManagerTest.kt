package com.ad_glasses.ota

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDfuManagerTest {

    @Test
    fun completesOnlyAfterVerifiedTypeFourAndFinalization() {
        val transport = FakeDfuTransport()
        val manager = BleDfuManager { transport }
        val file = tempBinFile()
        var completeCount = 0
        val progress = mutableListOf<Int>()
        val errors = mutableListOf<String>()

        try {
            manager.startDfu(
                binFile = file,
                onProgress = progress::add,
                onComplete = { completeCount++ },
                onError = errors::add,
            )

            transport.callback!!.onProgress(100)
            assertEquals(listOf(100), progress)
            assertEquals(0, completeCount)

            transport.callback!!.onActionResult(1, 0)
            transport.callback!!.onActionResult(2, 0)
            transport.callback!!.onActionResult(3, 0)
            assertEquals(0, completeCount)
            assertEquals(
                listOf("initCallback", "checkFile", "start", "init", "sendPacket", "check"),
                transport.operations,
            )

            transport.callback!!.onActionResult(4, 0)

            assertEquals(1, completeCount)
            assertTrue(errors.isEmpty())
            assertEquals(
                listOf("initCallback", "checkFile", "start", "init", "sendPacket", "check", "endAndRelease"),
                transport.operations,
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun nonzeroStatusIsTerminalAndDoesNotAdvanceTheSequence() {
        val transport = FakeDfuTransport()
        val manager = BleDfuManager { transport }
        val file = tempBinFile()
        val errors = mutableListOf<String>()

        try {
            manager.startDfu(file, {}, {}, errors::add)
            val callback = requireNotNull(transport.callback)
            callback.onActionResult(1, 6)
            callback.onActionResult(1, 0)

            assertEquals(listOf("DFU error at type=1: status=6"), errors)
            assertFalse("init must not run after a terminal error", "init" in transport.operations)
            assertFalse("finalization is only for successful type 4", "endAndRelease" in transport.operations)
            assertTrue("vendor callback must be detached after failure", "clearCallback" in transport.operations)
        } finally {
            file.delete()
        }
    }

    @Test
    fun cancellationDoesNotSendTheSuccessFinalizationCommand() {
        val transport = FakeDfuTransport()
        val manager = BleDfuManager { transport }
        val file = tempBinFile()

        try {
            manager.startDfu(file, {}, {}, {})
            val callback = requireNotNull(transport.callback)
            manager.cancel()
            callback.onActionResult(1, 0)

            assertFalse("endAndRelease is reserved for verified type 4", "endAndRelease" in transport.operations)
            assertFalse("init must not run after cancellation", "init" in transport.operations)
            assertTrue("vendor callback must be detached after cancellation", "clearCallback" in transport.operations)
        } finally {
            file.delete()
        }
    }

    @Test
    fun finalizationFailureDoesNotReportSuccess() {
        val transport = FakeDfuTransport(throwOnEndAndRelease = true)
        val manager = BleDfuManager { transport }
        val file = tempBinFile()
        var completeCount = 0
        val errors = mutableListOf<String>()

        try {
            manager.startDfu(file, {}, { completeCount++ }, errors::add)
            transport.callback!!.onActionResult(1, 0)
            transport.callback!!.onActionResult(2, 0)
            transport.callback!!.onActionResult(3, 0)
            transport.callback!!.onActionResult(4, 0)

            assertEquals(0, completeCount)
            assertEquals(listOf("DFU finalization failed: end failed"), errors)
            assertTrue("vendor callback must be detached after finalization failure", "clearCallback" in transport.operations)
        } finally {
            file.delete()
        }
    }

    @Test
    fun failedFileCheckNeverStartsTheVendorTransfer() {
        val transport = FakeDfuTransport(checkFileResult = false)
        val manager = BleDfuManager { transport }
        val file = tempBinFile()
        val errors = mutableListOf<String>()

        try {
            manager.startDfu(file, {}, {}, errors::add)

            assertEquals(listOf("Invalid DFU file: ${file.name}"), errors)
            assertFalse("start must follow a successful checkFile", "start" in transport.operations)
        } finally {
            file.delete()
        }
    }

    private fun tempBinFile(): File = File.createTempFile("ADGlasses-dfu-", ".bin").apply {
        writeBytes(byteArrayOf(1, 2, 3))
    }

    private class FakeDfuTransport(
        private val checkFileResult: Boolean = true,
        private val throwOnEndAndRelease: Boolean = false,
    ) : DfuTransport {
        val operations = mutableListOf<String>()
        var callback: DfuTransport.Callback? = null

        override fun initCallback() {
            operations += "initCallback"
        }

        override fun checkFile(path: String): Boolean {
            operations += "checkFile"
            return checkFileResult
        }

        override fun start(callback: DfuTransport.Callback) {
            operations += "start"
            this.callback = callback
        }

        override fun init() {
            operations += "init"
        }

        override fun sendPacket() {
            operations += "sendPacket"
        }

        override fun check() {
            operations += "check"
        }

        override fun endAndRelease() {
            operations += "endAndRelease"
            if (throwOnEndAndRelease) error("end failed")
        }

        override fun clearCallback() {
            operations += "clearCallback"
            callback = null
        }
    }
}
