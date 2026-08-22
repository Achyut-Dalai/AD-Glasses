package com.ad_glasses.ui

import android.view.View
import com.ad_glasses.databinding.ControlSlot

/** Batch click registration for real Android Views that remain outside the Compose product surface. */
fun setOnClickListener(vararg v: View?, block: View.() -> Unit) {
    val listener = View.OnClickListener { it.block() }
    v.forEach { it?.setOnClickListener(listener) }
}

/**
 * Batch click registration for MainActivity's non-visual controller slots.
 * This keeps inherited hardware handlers callable without creating hidden Views.
 */
fun setOnClickListener(vararg controls: ControlSlot?, block: ControlSlot.() -> Unit) {
    controls.forEach { control ->
        control?.setOnClickListener { clicked -> clicked.block() }
    }
}
