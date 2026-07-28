package com.fersaiyan.cyanbridge.ui

/**
 * @author hzy ,
 * @date  2021/1/3
 * <p>
 * "Programs should be written for other people to read,
 * and only incidentally for machines to execute"
 **/
data class SmartWatch (
    val deviceName:String,
    val deviceAddress:String?,
    val rssi:Int,
        ){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmartWatch

        if (deviceName != other.deviceName) return false
        if (deviceAddress != other.deviceAddress) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceName.hashCode()
        result = 31 * result + (deviceAddress?.hashCode() ?: 0)
        return result
    }
}
