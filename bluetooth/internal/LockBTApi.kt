package tedee.mobile.sdk.ble.bluetooth.internal

import android.util.Base64
import com.polidea.rxandroidble2.RxBleConnection
import io.reactivex.Single
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.bluetooth.secure.SecureConnectionConstants
import tedee.mobile.sdk.ble.bluetooth.secure.SecureException
import tedee.mobile.sdk.ble.bluetooth.secure.SecureSession

internal class LockBTApi {

  var session: SecureSession? = null

  fun sendCommand(
    rxBleConnection: RxBleConnection,
    message: Byte,
    params: ByteArray?,
  ): Single<ByteArray> =
    rxBleConnection.sendEncryptedNotificationCharacteristic(message, params)

  fun setSignedTime(rxBleConnection: RxBleConnection, signedTime: SignedTime): Single<ByteArray> {
    val time = Base64.decode(signedTime.datetime, Base64.DEFAULT)
    val signature = Base64.decode(signedTime.signature, Base64.DEFAULT)
    return rxBleConnection.sendUnencryptedNotificationCharacteristic(
      BluetoothConstants.SET_SIGNED_TIME,
      time + signature
    )
  }

  fun requestSignedSerial(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendUnencryptedNotificationCharacteristic(BluetoothConstants.REQUEST_SIGNED_SERIAL)

  fun getUnencryptedVersion(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendUnencryptedNotificationCharacteristic(BluetoothConstants.GET_VERSION)

  fun getUnencryptedSettings(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendUnencryptedNotificationCharacteristic(BluetoothConstants.GET_SETTINGS)

  fun getSettings(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendEncryptedNotificationCharacteristic(BluetoothConstants.GET_SETTINGS)

  fun getVersion(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendEncryptedNotificationCharacteristic(BluetoothConstants.GET_VERSION)

  fun registerDevice(rxBleConnection: RxBleConnection, authPublicKey: String): Single<ByteArray> =
    rxBleConnection.sendUnencryptedNotificationCharacteristic(
      BluetoothConstants.DEVICE_REGISTER,
      Base64.decode(authPublicKey, Base64.DEFAULT)
    )

  private fun RxBleConnection.sendEncryptedNotificationCharacteristic(
    message: Byte,
    payload: ByteArray? = null,
  ): Single<ByteArray> {
    return Single.create { emitter ->
      session?.let { secureSession ->
        buildEncryptedMessageToSend(secureSession, message, payload) { encryptedMessage ->
          updateCharacteristic(
            BluetoothConstants.LOCK_NOTIFICATION_CHARACTERISTIC,
            encryptedMessage
          ).subscribe({ emitter.onSuccess(it) }, { emitter.onError(it) })
        }
      } ?: emitter.onError(SecureException())
    }
  }

  private fun RxBleConnection.sendUnencryptedNotificationCharacteristic(
    message: Byte,
    payload: ByteArray? = null,
  ): Single<ByteArray> {
    return updateCharacteristic(
      BluetoothConstants.LOCK_NOTIFICATION_CHARACTERISTIC,
      buildUnencryptedMessageToSend(message, payload)
    )
  }

  // secure session / protocol
  fun sendHelloMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.MESSAGE_HELLO, message)
    )

  fun sendServerVerifyMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.SERVER_VERIFY, message)
    )

  fun sendClientVerifyMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.CLIENT_VERIFY, message)
    )

  fun sendClientVerifyEndMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.CLIENT_VERIFY_END, message)
    )

  private fun RxBleConnection.sendSecureNotificationCharacteristic(message: ByteArray) =
    updateCharacteristic(BluetoothConstants.SECURE_SESSION_LOCK_SEND_CHARACTERISTIC, message)
}
