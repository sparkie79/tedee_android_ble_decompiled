@file:Suppress("SameParameterValue")

package tedee.mobile.sdk.ble.bluetooth.internal

import android.bluetooth.BluetoothGatt
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.exceptions.BleCharacteristicNotFoundException
import com.polidea.rxandroidble2.exceptions.BleScanException
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.BluetoothConstants.LOCK_STATUS_ERROR_JAMMED
import tedee.mobile.sdk.ble.BluetoothConstants.LOCK_STATUS_ERROR_TIMEOUT
import tedee.mobile.sdk.ble.bluetooth.IBaseLockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.ISignedTimeProvider
import tedee.mobile.sdk.ble.bluetooth.adding.IAddLockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.error.AutoUnlockAlreadyCalledError
import tedee.mobile.sdk.ble.bluetooth.error.ConnectionWasDeadError
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.bluetooth.error.GeneralLockError
import tedee.mobile.sdk.ble.bluetooth.error.LockBusyError
import tedee.mobile.sdk.ble.bluetooth.error.LockInvalidParamError
import tedee.mobile.sdk.ble.bluetooth.error.LockIsDismountedError
import tedee.mobile.sdk.ble.bluetooth.error.LockIsNotCalibratedError
import tedee.mobile.sdk.ble.bluetooth.error.LockIsNotConfiguredError
import tedee.mobile.sdk.ble.bluetooth.error.LockIsNotRespondingError
import tedee.mobile.sdk.ble.bluetooth.error.LockJammedError
import tedee.mobile.sdk.ble.bluetooth.error.RegisterDeviceError
import tedee.mobile.sdk.ble.bluetooth.error.RequestSignatureError
import tedee.mobile.sdk.ble.bluetooth.error.SetSignedTimeError
import tedee.mobile.sdk.ble.bluetooth.error.UnlockAlreadyCalledError
import tedee.mobile.sdk.ble.bluetooth.error.shouldRetryOnError
import tedee.mobile.sdk.ble.bluetooth.secure.SecureConnectionConstants
import tedee.mobile.sdk.ble.bluetooth.secure.SecureConnectionHelper
import tedee.mobile.sdk.ble.bluetooth.secure.SecureSession
import tedee.mobile.sdk.ble.bluetooth.secure.SecurityData
import tedee.mobile.sdk.ble.extentions.getBit
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.extentions.retryOnBusyError
import tedee.mobile.sdk.ble.extentions.toUnsignedInt
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion
import tedee.mobile.sdk.ble.model.RegisterDeviceData
import tedee.mobile.sdk.ble.model.SignedTime
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.InvalidParameterException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.experimental.and

private const val DELAY_FOR_SETTING_CONNECTION_PRIORITY_TO_HIGH = 1L
private const val TIMEOUT_FOR_SETTING_CONNECTION_PRIORITY = 2L
private const val MAX_RETRY_ATTEMPTS_ON_CONNECTION_ERROR = 3

internal class LockConnectionWrapper(
  private val serialNumber: String,
  private var bleClient: RxBleClient,
  private val bleDevice: RxBleDevice,
  private val accessCertificate: DeviceCertificate?,
  private val keepConnection: Boolean,
  private var secureConnectionListener: ILockConnectionListener? = null,
  private var addLockConnectionListener: IAddLockConnectionListener? = null,
  private var lockConnectionListener: IBaseLockConnectionListener? = null,
  private var signedTimeProvider: ISignedTimeProvider? = null,
  private val ioDispatcher: CoroutineDispatcher,
  private val uiDispatcher: CoroutineDispatcher,
) {
  private val handler: Handler = Handler(Looper.getMainLooper())
  private var lockInteractor: LockBluetoothApiInteractor? = null
  private var rxBleConnection: RxBleConnection? = null
  private var secureConnectionHelper: SecureConnectionHelper? = null
  private var session: SecureSession? = null
  private var compositeDisposable: CompositeDisposable = CompositeDisposable()
  private var indicationSubject: PublishSubject<ByteArray> = PublishSubject.create()
  private var notificationSubject: PublishSubject<ByteArray> = PublishSubject.create()
  private lateinit var secureEstablishNotifications: Observable<ByteArray>
  private var lockNotifications: Observable<ByteArray>? = null
  private var isFromAddingLock = false

  fun connect(isFromAddingLock: Boolean) {
    val remaining = AtomicInteger()
    this.isFromAddingLock = isFromAddingLock
    Observable.timer(100, TimeUnit.MILLISECONDS)
      .flatMap {
        bleClient.observeStateChanges()
          .startWith(bleClient.state)
          .distinctUntilChanged()
          .filter { it == RxBleClient.State.READY }
      }
      .flatMap {
        bleDevice.establishConnection(true)
          .doOnSubscribe {
            Timber.d("Connect lock: ${serialNumber}: establish bluetooth connection from wrapper")
            handler.post { onConnectionChanged(isConnecting = true, isConnected = false) }
          }
      }
      .flatMapSingle {
        Timber.d(
          "Connect lock: ${serialNumber}: connected via BT in wrapper, requesting connection priority",
          serialNumber
        )
        requestConnectionPriority(
          it
        )
      }
      .flatMap(
        {
          it.setupNotification(UUID.fromString(BluetoothConstants.SECURE_SESSION_LOCK_READ_NOTIFICATION_CHARACTERISTIC))
        },
        { bleConnection, secureNotifications ->
          ConnectionData(bleConnection, secureNotifications.map { convertHeader(it) })
        })
      .flatMap(
        {
          it.rxBleConnection.setupIndication(UUID.fromString(BluetoothConstants.LOCK_NOTIFICATION_CHARACTERISTIC))
        },
        { connectionData, lockIndications ->
          connectionData.apply { this.lockIndications = lockIndications.map { convertHeader(it) } }
        })
      .flatMap(
        {
          it.rxBleConnection.setupNotification(
            UUID.fromString(BluetoothConstants.LOCK_READ_NOTIFICATION_CHARACTERISTIC)
          )
        },
        { connectionData, lockNotifications ->
          connectionData.apply {
            this.lockNotifications = lockNotifications.map { convertHeader(it) }
          }
        }
      )
      .doOnError {
        handler.post { onConnectionChanged(isConnecting = false, isConnected = false) }
        Timber.e(
          it,
          "Connect lock: Error in connection wrapper to ${serialNumber}, Error: ${it.message}, Cause: ${it.cause}"
        )
      }
      .retryWhen {
        it.flatMap { error ->
          if ((keepConnection || (remaining.getAndIncrement() != MAX_RETRY_ATTEMPTS_ON_CONNECTION_ERROR))
            && shouldRetryOnError(error)
          ) {
            Timber.e(
              "Connect lock: retry connect to $serialNumber from wrapper, ${remaining.get()} time, error: ${error.localizedMessage}",
              serialNumber
            )
            Observable.timer(getRetryTimeOutForException(error), TimeUnit.SECONDS)
          } else {
            Observable.error(ConnectionWasDeadError(error))
          }
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { onConnected(it, isFromAddingLock) },
        { onConnectionError(it) }
      ).addTo(compositeDisposable)
  }

  private fun convertHeader(byteArray: ByteArray): ByteArray {
    Timber.d(
      "Lock $serialNumber current message number = ${
        (byteArray.first()
          .toInt() shr (4)) + 8
      }"
    )
    return byteArray.apply { this[0] = first() and 0xF }
  }

  private fun onConnected(connectionData: ConnectionData, isFromAddingLock: Boolean) {
    rxBleConnection = connectionData.rxBleConnection
    secureEstablishNotifications = connectionData.secureEstablishNotifications
    lockNotifications = connectionData.lockNotifications
    lockInteractor = LockBluetoothApiInteractor(connectionData.rxBleConnection)
    connectionData.lockIndications
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { onIndicationCharacteristic(it) },
        { Timber.e(it, "error in indications") }
      ).addTo(compositeDisposable)
    lockNotifications
      ?.observeOn(AndroidSchedulers.mainThread())
      ?.subscribe(
        { onNotificationCharacteristic(it) },
        { Timber.e(it, "error in notifications") }
      )?.addTo(compositeDisposable)
    if (isFromAddingLock) {
      handler.post {
        addLockConnectionListener?.onUnsecureConnectionChanged(
          isConnecting = false,
          isConnected = true
        )
      }
    } else {
      establishSecureConnection()
    }
  }

  private fun onConnectionError(it: Throwable) {
    Timber.e(it, "on connection error in wrapper")
    handler.post { onConnectionChanged(isConnecting = false, isConnected = false) }
    secureConnectionHelper?.closeConnection()
    secureConnectionHelper = null
    session = null
  }

  private fun establishSecureConnection() {
    lockInteractor?.let { interactor ->
      secureConnectionHelper?.closeConnection(null, false)
      Timber.i("Connect lock: ${serialNumber}: all characteristics discovered, establishing secure connection from wrapper")
      secureConnectionHelper = SecureConnectionHelper(
        accessCertificate,
        interactor,
        secureEstablishNotifications,
        lockNotifications,
        { setSignedTime() },
        { error -> handler.post { lockConnectionListener?.onError(error) } },
        { session ->
          this.session = session
          interactor.setSecureSession(session)
          Timber.i("Connect lock: ${serialNumber}: secure connection is established in wrapper")
          recreateSubjectIfNeeded()
          handler.post {
            secureConnectionListener?.onLockConnectionChanged(
              isConnecting = false,
              isConnected = true
            )
          }
        },
        {
          handler.post {
            secureConnectionListener?.onLockConnectionChanged(
              isConnecting = false,
              isConnected = false
            )
          }
        },
      )
    }
  }

  private fun setSignedTime() {
    signedTimeProvider?.getSignedTime { setSignedTimeInternally(it) }
  }

  private fun onNotificationCharacteristic(incomingMessage: ByteArray) {
    if (incomingMessage.first() == SecureConnectionConstants.DATA_ENCRYPTED) {
      session?.read(
        SecurityData(incomingMessage.copyOfRange(1, incomingMessage.size),
          { byteArray ->
            byteArray?.also { message ->
              Timber.i("NOTIFICATION: ${message.print()}")
              notificationSubject.onNext(message)
              when (message.first()) {
                BluetoothConstants.NOTIFICATION_LOCK_STATUS_CHANGE -> onLockStatusChanged(message)
                BluetoothConstants.NOTIFICATION_NEED_DATE_TIME -> {
                  setSignedTime()
                  lockConnectionListener?.onNotification(message)
                }

                else -> lockConnectionListener?.onNotification(message)
              }
            }
          })
      )
    } else if (incomingMessage.first() == SecureConnectionConstants.DATA_NOT_ENCRYPTED) {
      val message = incomingMessage.copyOfRange(1, incomingMessage.size)
      Timber.i("NOTIFICATION: ${message.print()}")
      lockConnectionListener?.onNotification(message)
      notificationSubject.onNext(message)
    }
  }

  private fun onIndicationCharacteristic(incomingMessage: ByteArray) {
    if (incomingMessage.first() == SecureConnectionConstants.DATA_ENCRYPTED) {
      session?.read(
        SecurityData(incomingMessage.copyOfRange(1, incomingMessage.size),
          { byteArray ->
            byteArray?.also { message ->
              Timber.i("MESSAGE: ${message.print()}")
              indicationSubject.onNext(message)
            }
          })
      )
    } else if (incomingMessage.first() == SecureConnectionConstants.DATA_NOT_ENCRYPTED) {
      val message = incomingMessage.copyOfRange(1, incomingMessage.size)
      Timber.i("MESSAGE: ${message.print()}")
      indicationSubject.onNext(message)
    }
  }

  private fun onLockStatusChanged(message: ByteArray) {
    secureConnectionListener?.onLockStatusChanged(message[1], message[2])
  }

  fun closeConnection(notifyListener: Boolean = false, reason: String? = null) {
    Timber.w("Closing BT lock connection for $serialNumber")
    lockInteractor?.close()
    if (notifyListener) {
      handler.post {
        secureConnectionListener?.onLockConnectionChanged(
          isConnecting = false,
          isConnected = false
        )
        addLockConnectionListener?.onUnsecureConnectionChanged(
          isConnecting = false,
          isConnected = false
        )
      }
    }
    secureConnectionHelper?.closeConnection(reason)
    secureConnectionHelper = null
    session = null
    secureConnectionListener = null
    addLockConnectionListener = null
    lockConnectionListener = null
    signedTimeProvider = null
    compositeDisposable.clear()
  }

  private fun onConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    if (isFromAddingLock) {
      addLockConnectionListener?.onUnsecureConnectionChanged(isConnecting, isConnected)
    } else {
      secureConnectionListener?.onLockConnectionChanged(isConnecting, isConnected)
    }
  }

  private fun requestConnectionPriority(
    rxBleConnection: RxBleConnection,
    @RxBleConnection.ConnectionPriority connectionPriority: Int = BluetoothGatt.CONNECTION_PRIORITY_HIGH,
  ): Single<RxBleConnection> {
    return rxBleConnection.requestConnectionPriority(
      connectionPriority,
      DELAY_FOR_SETTING_CONNECTION_PRIORITY_TO_HIGH,
      TimeUnit.MILLISECONDS
    )
      .timeout(TIMEOUT_FOR_SETTING_CONNECTION_PRIORITY, TimeUnit.SECONDS)
      .doOnSubscribe { Timber.d("Setting connectionPriority to $connectionPriority") }
      .doOnComplete { Timber.d("Successfully set connectionPriority to $connectionPriority") }
      .doOnError { Timber.e(it, "Error setting connectionPriority to $connectionPriority") }
      .onErrorComplete()
      .toSingleDefault(rxBleConnection)
  }

  suspend fun sendCommand(message: Byte, params: ByteArray?): ByteArray? {
    recreateSubjectIfNeeded()
    return withContext(ioDispatcher) {
      getAndMapIndicationSubject(message) { it }
        .doOnSubscribe { lockInteractor?.sendCommand(message, params) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .await()
    }
  }

  suspend fun getLockState(): ByteArray {
    recreateSubjectIfNeeded()
    return withContext(ioDispatcher) {
      getAndMapIndicationSubject(BluetoothConstants.GET_STATE) { it.copyOfRange(1, it.size) }
        .doOnSubscribe { lockInteractor?.getLockState() }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .await()
    }
  }

  suspend fun openLock(param: Byte) {
    recreateSubjectIfNeeded()
    return withContext(ioDispatcher) {
      getAndMapIndicationSubject(BluetoothConstants.OPEN_LOCK) { it[1] }
        .doOnSubscribe { lockInteractor?.openLock(param) }
        .flatMap {
          when (it) {
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            BluetoothConstants.API_RESULT_BUSY -> Single.error(LockBusyError())
            BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(LockInvalidParamError())
            BluetoothConstants.API_RESULT_NOT_CALIBRATED -> Single.error(LockIsNotCalibratedError())
            BluetoothConstants.API_RESULT_NOT_CONFIGURED -> Single.error(LockIsNotConfiguredError())
            BluetoothConstants.API_RESULT_DISMOUNTED -> Single.error(LockIsDismountedError())
            BluetoothConstants.API_RESULT_UNLOCK_ALREADY_CALLED_BY_AUTOUNLOCK ->
              Single.error(AutoUnlockAlreadyCalledError())

            BluetoothConstants.API_RESULT_UNLOCK_ALREADY_CALLED_BY_OTHER_OPERATION ->
              Single.error(UnlockAlreadyCalledError())

            else -> Single.error(GeneralLockError(it))
          }
        }
        .doOnError { if (it is GeneralLockError) lockInteractor?.getLockState() }
        .retryOnBusyError()
        .subscribeOn(Schedulers.io())
        .await()
    }
  }

  suspend fun closeLock(param: Byte) {
    recreateSubjectIfNeeded()
    withContext(ioDispatcher) {
      getAndMapIndicationSubject(BluetoothConstants.CLOSE_LOCK) { it[1] }
        .doOnSubscribe { lockInteractor?.closeLock(param) }
        .flatMap {
          when (it) {
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            BluetoothConstants.API_RESULT_BUSY -> Single.error(LockBusyError())
            BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(LockInvalidParamError())
            BluetoothConstants.API_RESULT_NOT_CALIBRATED -> Single.error(LockIsNotCalibratedError())
            BluetoothConstants.API_RESULT_DISMOUNTED -> Single.error(LockIsDismountedError())
            else -> Single.error(GeneralLockError(it))
          }
        }
        .doOnError { if (it is GeneralLockError) lockInteractor?.getLockState() }
        .retryOnBusyError()
        .ignoreElement()
        .subscribeOn(Schedulers.io())
        .await()
    }
  }

  suspend fun pullSpring() {
    recreateSubjectIfNeeded()
    withContext(ioDispatcher) {
      getAndMapIndicationSubject(BluetoothConstants.PULL_SPRING) { it[1] }
        .doOnSubscribe { lockInteractor?.pullSpring() }
        .flatMap {
          when (it) {
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            BluetoothConstants.API_RESULT_BUSY -> Single.error(LockBusyError())
            BluetoothConstants.API_RESULT_NOT_CALIBRATED -> Single.error(LockIsNotCalibratedError())
            BluetoothConstants.API_RESULT_DISMOUNTED -> Single.error(LockIsDismountedError())
            BluetoothConstants.API_RESULT_NOT_CONFIGURED -> Single.error(LockIsNotConfiguredError())
            else -> Single.error(GeneralLockError(it))
          }
        }
        .doOnError { if (it is GeneralLockError) lockInteractor?.getLockState() }
        .retryOnBusyError()
        .ignoreElement()
        .subscribeOn(Schedulers.io())
        .await()
    }
  }

  private fun getUnencryptedVersion(): Single<ByteArray> {
    return getAndMapIndicationSubject(BluetoothConstants.GET_VERSION) { it }
      .doOnSubscribe { lockInteractor?.getUnencryptedVersion() }
  }

  private fun getUnencryptedSettings(): Single<ByteArray> {
    return getAndMapIndicationSubject(BluetoothConstants.GET_SETTINGS) { it }
      .doOnSubscribe { lockInteractor?.getUnencryptedSettings() }
  }

  private fun getVersion(): Single<ByteArray> {
    return getAndMapIndicationSubject(BluetoothConstants.GET_VERSION) { it }
      .doOnSubscribe { lockInteractor?.getVersion() }
  }

  private fun getSettings(): Single<ByteArray> {
    return getAndMapIndicationSubject(BluetoothConstants.GET_SETTINGS) { it }
      .doOnSubscribe { lockInteractor?.getSettings() }
  }

  private fun recreateSubjectIfNeeded() {
    if (indicationSubject.hasComplete() || indicationSubject.hasThrowable()) {
      indicationSubject = PublishSubject.create()
    }
  }

  suspend fun getSignature(): String = withContext(ioDispatcher) {
    try {
      recreateSubjectIfNeeded()
      val signatureNotification = observeSignatureNotification()
      getAndMapIndicationSubject(BluetoothConstants.REQUEST_SIGNED_SERIAL) { it }
        .doOnSubscribe { lockInteractor?.requestSignedSerial() }
        .flatMap {
          when (it[1]) {
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(InvalidParameterException())
            BluetoothConstants.API_RESULT_ERROR -> Single.error(RequestSignatureError())
            else -> Single.error(GeneralLockError(it[1]))
          }
        }
        .ignoreElement()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .await()
      Base64.encodeToString(signatureNotification.first(), Base64.DEFAULT)
    } catch (error: Throwable) {
      handleError(error)
    }
  }

  private fun setSignedTimeInternally(signedTime: SignedTime) {
    getAndMapIndicationSubject(BluetoothConstants.REQUEST_SIGNED_SERIAL) { it }
      .doOnSubscribe { lockInteractor?.setSignedTime(signedTime) }
      .flatMap {
        when (it[1]) {
          BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
          BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(InvalidParameterException())
          BluetoothConstants.API_RESULT_ERROR -> Single.error(SetSignedTimeError())
          else -> Single.error(GeneralLockError(it[1]))
        }
      }
      .ignoreElement()
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        Timber.d("set signed time success")
      }, {
        Timber.e(it, "set signed time error")
      }).addTo(compositeDisposable)
  }

  suspend fun setSignedTime(signedTime: SignedTime): ByteArray? {
    recreateSubjectIfNeeded()
    return withContext(ioDispatcher) {
      getAndMapIndicationSubject(BluetoothConstants.REQUEST_SIGNED_SERIAL) { it }
        .doOnSubscribe { lockInteractor?.setSignedTime(signedTime) }
        .flatMap {
          when (it[1]) {
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(InvalidParameterException())
            BluetoothConstants.API_RESULT_ERROR -> Single.error(SetSignedTimeError())
            else -> Single.error(GeneralLockError(it[1]))
          }
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .await()
    }
  }

  private fun observeSignatureNotification(): Flow<ByteArray> {
    return getAndMapNotificationSubject(BluetoothConstants.NOTIFICATION_SIGNED_SERIAL) {
      it.copyOfRange(1, it.size)
    }
      .subscribeOn(Schedulers.io())
      .timeout(OBSERVING_NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .observeOn(AndroidSchedulers.mainThread())
      .asFlow()
  }

  suspend fun waitForLockStatusChange(lockState: Byte, timeoutInSeconds: Long = DEFAULT_TIMEOUT) {
    recreateSubjectIfNeeded()
    withContext(ioDispatcher) {
      getAndMapNotificationSubject(BluetoothConstants.NOTIFICATION_LOCK_STATUS_CHANGE) { it }
        .flatMapSingle {
          when (it[2]) {
            LOCK_STATUS_ERROR_JAMMED -> Single.error(LockJammedError())
            LOCK_STATUS_ERROR_TIMEOUT -> Single.error(LockIsNotRespondingError())
            else -> Single.just(it)
          }
        }
        .filter { lockState == it[1] }
        .firstOrError()
        .ignoreElement()
        .timeout(timeoutInSeconds, TimeUnit.SECONDS)
        .await()
    }
  }

  suspend fun getFirmwareVersion(isLockAdded: Boolean): FirmwareVersion {
    recreateSubjectIfNeeded()
    return withContext(ioDispatcher) {
      try {
        val byteArray = (if (isLockAdded) getVersion() else getUnencryptedVersion())
          .subscribeOn(Schedulers.io())
          .await()
        if (byteArray.component2() == BluetoothConstants.API_RESULT_NO_PERMISSION) {
          throw DeviceNeedsResetError(true)
        }
        val processedArray = byteArray.copyOfRange(2, 7)
        getFirmwareVersion(processedArray)
      } catch (error: Throwable) {
        handleError(error)
      }
    }
  }

  suspend fun getDeviceSettings(isLockAdded: Boolean): DeviceSettings {
    return withContext(ioDispatcher) {
      try {
        val byteArray = fetchSettings(isLockAdded)
        val processedArray = byteArray.copyOfRange(2, 13)
        val byteBuffer = ByteBuffer.wrap(processedArray)
        buildLockSettings(byteBuffer)
      } catch (error: Throwable) {
        handleError(error)
      }
    }
  }

  suspend fun getDeviceSettingsWithRevision(): Pair<DeviceSettings, Int> {
    return withContext(ioDispatcher) {
      try {
        val byteArray = fetchSettings(false)
        val processedArray = byteArray.copyOfRange(2, 13)
        val byteBuffer = ByteBuffer.wrap(processedArray)
        val revision = byteBuffer.getShort().toInt()
        buildLockSettings(byteBuffer) to revision
      } catch (error: Throwable) {
        handleError(error)
      }
    }
  }

  private suspend fun fetchSettings(isLockAdded: Boolean): ByteArray {
    recreateSubjectIfNeeded()
    val byteArray = (if (isLockAdded) getSettings() else getUnencryptedSettings())
      .subscribeOn(Schedulers.io())
      .await()
    if (byteArray.component2() == BluetoothConstants.API_RESULT_NO_PERMISSION) {
      throw DeviceNeedsResetError(true)
    }
    return byteArray
  }

  private suspend fun handleError(error: Throwable): Nothing {
    withContext(uiDispatcher) {
      lockConnectionListener?.onError(error)
    }
    throw error
  }

  suspend fun registerDevice(registerDeviceData: RegisterDeviceData) {
    recreateSubjectIfNeeded()
    withContext(ioDispatcher) {
      try {
        getAndMapIndicationSubject(BluetoothConstants.DEVICE_REGISTER) { it }
          .doOnSubscribe { lockInteractor?.registerDevice(registerDeviceData.authPublicKey) }
          .flatMap {
            when (it[1]) {
              BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
              BluetoothConstants.API_RESULT_INVALID_PARAM -> Single.error(InvalidParameterException())
              BluetoothConstants.API_RESULT_ERROR -> Single.error(RegisterDeviceError())
              else -> Single.error(GeneralLockError(it[1]))
            }
          }
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .await()
        observeRegisterDeviceNotification()
      } catch (error: Throwable) {
        handleError(error)
      }
    }
  }

  private suspend fun observeRegisterDeviceNotification() {
    recreateSubjectIfNeeded()
    withContext(ioDispatcher) {
      getAndMapNotificationSubject(BluetoothConstants.NOTIFICATION_REGISTER) { it }
        .subscribeOn(Schedulers.io())
        .timeout(OBSERVING_NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .flatMapSingle {
          when (it[1]) {
            BluetoothConstants.API_RESULT_NO_PERMISSION -> Single.error(DeviceNeedsResetError(true))
            BluetoothConstants.API_RESULT_SUCCESS -> Single.just(it)
            else -> Single.error(GeneralLockError(it[1]))
          }
        }
        .firstOrError()
        .ignoreElement()
        .await()
    }
  }

  private fun buildLockSettings(byteBuffer: ByteBuffer): DeviceSettings {
    val param2 = byteBuffer.get()
    val autoLockEnabled = param2.getBit(7)
    val autoLockImplicitEnabled = param2.getBit(6)
    val pullSpringEnabled = param2.getBit(5)
    val autoPullSpringEnabled = param2.getBit(4)
    val postponedLockEnabled = param2.getBit(3)
    val buttonLockEnabled = param2.getBit(2)
    val buttonUnlockEnabled = param2.getBit(1)
    val autoLockDelay = byteBuffer.short.toUnsignedInt()
    val pullSpringDuration = byteBuffer.short.toUnsignedInt()
    val postponedLockDelay = byteBuffer.short.toUnsignedInt()
    val autoLockImplicitDelay = byteBuffer.short.toUnsignedInt()
    return DeviceSettings(
      autoLockEnabled,
      autoLockDelay,
      autoLockImplicitEnabled,
      autoLockImplicitDelay,
      pullSpringEnabled,
      pullSpringDuration,
      autoPullSpringEnabled,
      postponedLockEnabled,
      postponedLockDelay,
      buttonLockEnabled,
      buttonUnlockEnabled
    )
  }

  private fun getFirmwareVersion(byteArray: ByteArray): FirmwareVersion {
    return FirmwareVersion(
      BluetoothConstants.DEVICE_FIRMWARE.toInt(),
      mapToDeviceVersionString(byteArray)
    )
  }

  private fun mapToDeviceVersionString(message: ByteArray): String {
    try {
      ByteBuffer.wrap(message).let { byteBuffer ->
        val major = byteBuffer.get().toUnsignedInt().toString()
        val minor = byteBuffer.get().toUnsignedInt().toString()
        val buildVersion = byteBuffer.short.toUnsignedInt().toString()
        return "$major.$minor.$buildVersion"
      }
    } catch (error: Exception) {
      Timber.e(error, "error in mapToDeviceVersionString")
      return ""
    }
  }

  private fun <T> getAndMapNotificationSubject(
    commandId: Byte,
    mapFunction: ((ByteArray) -> T),
  ): Observable<T> {
    return notificationSubject
      .filter { it.first() == commandId }
      .map { mapFunction(it) }
  }

  private fun <T> getAndMapIndicationSubject(
    commandId: Byte,
    mapFunction: ((ByteArray) -> T),
  ): Single<T> {
    return indicationSubject
      .filter { it.first() == commandId }
      .map { mapFunction(it) }
      .firstOrErrorWithTimeOut(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
  }

  private fun <T> Observable<T>.firstOrErrorWithTimeOut(
    seconds: Long,
    timeUnit: TimeUnit,
  ): Single<T> = this.firstOrError().timeout(seconds, timeUnit)

  private data class ConnectionData(
    val rxBleConnection: RxBleConnection,
    val secureEstablishNotifications: Observable<ByteArray>,
  ) {
    var lockNotifications: Observable<ByteArray>? = null
    lateinit var lockIndications: Observable<ByteArray>
  }

  private fun getRetryTimeOutForException(it: Throwable): Long {
    return when {
      it is BleScanException && it.reason == BleScanException.UNDOCUMENTED_SCAN_THROTTLE -> 15L
      it is BleCharacteristicNotFoundException -> 15L
      else -> 1L
    }
  }

  private companion object {
    private const val DEFAULT_TIMEOUT = 30L
    private const val OBSERVING_NOTIFICATION_TIMEOUT_SECONDS = 30L
  }
}