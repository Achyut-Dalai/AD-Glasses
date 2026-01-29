package com.fersaiyan.cyanbridge.ui
import android.view.View

/**
 * @Author: Hzy
 * @CreateDate: 2021/6/25 14:14
 * <p>
 * "Programs should be written for other people to read,
 * and only incidentally for machines to execute"
 *
 */
/**
 * Set click events for controls in batches.
 *
 * @param v The clicked control
 * @param block The code block to handle the click event callback
 */
fun setOnClickListener(vararg v: View?, block: View.() -> Unit) {
    val listener = View.OnClickListener { it.block() }
    v.forEach { it?.setOnClickListener(listener) }
}

