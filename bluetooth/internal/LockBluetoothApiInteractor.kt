package tedee.mobile.sdk.ble.bluetooth.internal

import com.polidea.rxandroidble2.RxBleConnection
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.bluetooth.secure.SecureInteractorInterface
import tedee.mobile.sdk.ble.bluetooth.secure.SecureSession
import timber.log.Timber

internal class LockBluetoothApiInteractor(
  private val rxBleConnection: RxBleConnection,
) : SecureInteractorInterface {

  private val doorLockBTApi = LockBTApi()
  private val disposables: CompositeDisposable = CompositeDisposable()

  override fun sendHello(message: ByteArray) =
    doorLockBTApi
      .sendHelloMessage(rxBleConnection, message)
      .setupSubscriber("sendHello")

  override fun sendServerVerify(message: ByteArray) =
    doorLockBTApi
      .sendServerVerifyMessage(rxBleConnection, message)
      .setupSubscriber("sendServerVerify")

  override fun sendClientVerify(message: ByteArray) =
    doorLockBTApi
      .sendClientVerifyMessage(rxBleConnection, message)
      .setupSubscriber("sendClientVerify")

  override fun sendClientVerifyEnd(message: ByteArray) =
    doorLockBTApi
      .sendClientVerifyEndMessage(rxBleConnection, message)
      .setupSubscriber("sendClientVerifyEnd")

  fun setSecureSession(session: SecureSession) {
    doorLockBTApi.session = session
  }

  fun sendCommand(message: Byte, params: ByteArray?) =
    doorLockBTApi
      .sendCommand(rxBleConnection, message, params)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(message))

  fun getLockState() =
    doorLockBTApi
      .sendCommand(rxBleConnection, BluetoothConstants.GET_STATE, null)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.GET_STATE))

  fun openLock(param: Byte) =
    doorLockBTApi
      .sendCommand(rxBleConnection, BluetoothConstants.OPEN_LOCK, byteArrayOf(param))
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.OPEN_LOCK))

  fun closeLock(param: Byte) =
    doorLockBTApi
      .sendCommand(rxBleConnection, BluetoothConstants.CLOSE_LOCK, byteArrayOf(param))
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.CLOSE_LOCK))

  fun pullSpring() =
    doorLockBTApi
      .sendCommand(rxBleConnection, BluetoothConstants.PULL_SPRING, params = null)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.PULL_SPRING))

  fun setSignedTime(signedTime: SignedTime) =
    doorLockBTApi
      .setSignedTime(rxBleConnection, signedTime)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.SET_SIGNED_TIME))

  fun requestSignedSerial() =
    doorLockBTApi
      .requestSignedSerial(rxBleConnection)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.REQUEST_SIGNED_SERIAL))

  fun getUnencryptedVersion() =
    doorLockBTApi
      .getUnencryptedVersion(rxBleConnection)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.GET_VERSION))

  fun getUnencryptedSettings() =
    doorLockBTApi
      .getUnencryptedSettings(rxBleConnection)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.GET_SETTINGS))

  fun getVersion() =
    doorLockBTApi
      .getVersion(rxBleConnection)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.GET_VERSION))

  fun getSettings() =
    doorLockBTApi
      .getSettings(rxBleConnection)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.GET_SETTINGS))

  fun registerDevice(authPublicKey: String) =
    doorLockBTApi
      .registerDevice(rxBleConnection, authPublicKey)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .setupSubscriber(BluetoothConstants.mapHeaderToLockCommandName(BluetoothConstants.DEVICE_REGISTER))

  fun close() = disposables.clear()

  private fun <T> Single<T>.setupSubscriber(functionName: String) {
    subscribe(
      { Timber.d("$functionName onSuccess") },
      { Timber.e(it, "$functionName onError") }
    ).addTo(disposables)
  }
}
