package com.achyut.adglasses.bridge.core

/** Shortcut to create a Text command. */
fun DisplayCommand.Companion.text(text: String, priority: DisplayPriority = DisplayPriority.NORMAL) =
    DisplayCommand.Text(text, priority)

/** Shortcut to create a Lines command. */
fun DisplayCommand.Companion.lines(vararg lines: String, page: Int = 0) =
    DisplayCommand.Lines(lines.toList(), page)

/** Shortcut to create a Card command. */
fun DisplayCommand.Companion.card(title: String, body: String) =
    DisplayCommand.Card(title, body)
