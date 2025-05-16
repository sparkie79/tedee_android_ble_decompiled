package tedee.mobile.sdk.ble.extentions

import com.polidea.rxandroidble2.RxBleClient
import tedee.mobile.sdk.ble.bluetooth.error.BluetoothDisabled

fun RxBleClient.checkBluetoothState() {
  if (state !== RxBleClient.State.READY) {
    throw (BluetoothDisabled())
  }
}