package com.achyut.adglasses.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_search_index_state")
data class LocalSearchIndexStateEntity(
    @PrimaryKey
    val stateKey: String,
    val stateValue: String,
    val updatedAt: Long,
)
