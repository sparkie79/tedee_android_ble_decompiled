package tedee.mobile.sdk.ble.bluetooth.secure

internal interface SecureInteractorInterface {
  fun sendHello(message: ByteArray)
  fun sendServerVerify(message: ByteArray)
  fun sendClientVerify(message: ByteArray)
  fun sendClientVerifyEnd(message: ByteArray)
}