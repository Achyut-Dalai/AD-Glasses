package com.adglasses.app.core.notifications

import com.adglasses.app.core.model.CapturedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationHub {
    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    fun upsert(item: CapturedNotification) {
        _items.value = (listOf(item) + _items.value.filterNot { it.key == item.key }).take(50)
    }

    fun remove(key: String) {
        _items.value = _items.value.filterNot { it.key == key }
    }
}
