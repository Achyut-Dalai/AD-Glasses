package com.meta.wearable.dat.core

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

/**
 * Compile-only shell used when the private Meta DAT dependency is not configured.
 * The unavailable MetaRaybanManager prevents this contract from being launched in normal flow.
 */
object Wearables {
    class RequestPermissionContract : ActivityResultContract<Permission, Result<PermissionStatus>>() {
        override fun createIntent(context: Context, input: Permission): Intent = Intent()

        override fun parseResult(resultCode: Int, intent: Intent?): Result<PermissionStatus> =
            Result.success(PermissionStatus.Denied)
    }
}
