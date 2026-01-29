package com.fersaiyan.cyanbridge.ui
import android.content.Context
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.fersaiyan.cyanbridge.R

/**
 * @author hzy ,
 * @date  2021/1/3
 * <p>
 * "Programs should be written for other people to read,
 * and only incidentally for machines to execute"
 **/
class DeviceListAdapter(context: Context,data: MutableList<SmartWatch>):
    BaseQuickAdapter<SmartWatch, BaseViewHolder>(R.layout.recycleview_item_device,data) {

    override fun convert(holder: BaseViewHolder, item: SmartWatch) {
        var deviceName=item.deviceName
        if(deviceName.startsWith("O_")){
            deviceName=deviceName.substring(2,deviceName.length)
        }
        holder.setText(R.id.rcv_device_name,deviceName)
        holder.setText(R.id.rcv_device_address,item.deviceAddress)
    }
}
