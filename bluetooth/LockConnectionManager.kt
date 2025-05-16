package tedee.mobile.sdk.ble.bluetooth

import android.content.Context
import android.os.ParcelUuid
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.Dispatchers
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.bluetooth.error.BluetoothDisabled
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNotFoundError
import tedee.mobile.sdk.ble.bluetooth.error.InvalidSerialNumberError
import tedee.mobile.sdk.ble.bluetooth.error.NoPermissionsError
import tedee.mobile.sdk.ble.bluetooth.error.NotProvidedSignedTime
import tedee.mobile.sdk.ble.bluetooth.internal.LockConnectionWrapper
import tedee.mobile.sdk.ble.bluetooth.internal.extractSerialNumber
import tedee.mobile.sdk.ble.extentions.checkBluetoothState
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.permissions.checkDeniedPermissions
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Lock connection manager. Manages connection with Tedee lock. To
 * establish successful connection with lock you need to provide
 * serial number, device certificate and mobile public key.
 *
 * @constructor Create empty Lock bluetooth server
 * @property context Application context
 */
class LockConnectionManager(private val context: Context) : ILockInteractor {
  private var secureConnectionListener: ILockConnectionListener? = null
  private var lockConnectionWrapper: LockConnectionWrapper? = null

  private val rxBleClient: RxBleClient = RxBleClient.create(context.applicationContext)
  private var scanningDisposable: CompositeDisposable = CompositeDisposable()

  /**
   * Provider for signed date-time, used in securing lock interactions.
   * This property should be set to an instance of an [ISignedTimeProvider] implementation.
   * If this property is not set, [NotProvidedSignedTime] error will be return.
   */
  var signedDateTimeProvider: ISignedTimeProvider? = null

  /**
   * Initiates a secure connection to the lock.
   *
   * @param serialNumber Serial number of the device ex. 12345678-901234
   * @param deviceCertificate A certificate required for secure connection to the device.
   * @param keepConnection If true, maintains the connection after initial setup;
   *                       if false, connection has timeout @see [DEFAULT_TIMEOUT_FOR_SCANNING].
   * @param secureConnectionListener Callback interface for lock connection changes and updates.
   */
  override fun connect(
    serialNumber: String,
    deviceCertificate: DeviceCertificate,
    keepConnection: Boolean,
    secureConnectionListener: ILockConnectionListener,
  ) {
    clear()
    this.secureConnectionListener = secureConnectionListener
    if (hasInvalidSerialNumber(serialNumber) || hasDeniedPermissions(context)) {
      return
    }
    scanForLock(serialNumber, keepConnection)
      .subscribe(
        {
          Timber.d("Connecting to $serialNumber lock, keepConnection $keepConnection")
          scanningDisposable.clear()
          lockConnectionWrapper?.closeConnection()
          lockConnectionWrapper = LockConnectionWrapper(
            serialNumber = serialNumber,
            bleClient = rxBleClient,
            bleDevice = it,
            accessCertificate = deviceCertificate,
            keepConnection,
            lockConnectionListener = secureConnectionListener,
            secureConnectionListener = secureConnectionListener,
            addLockConnectionListener = null,
            signedTimeProvider = signedDateTimeProvider.takeIf { provider -> provider != null }
              ?: throw NotProvidedSignedTime(),
            ioDispatcher = Dispatchers.IO,
            uiDispatcher = Dispatchers.Main,
          ).also { wrapper -> wrapper.connect(false) }
        },
        {
          onError(it)
        }
      ).addTo(scanningDisposable)
  }

  private fun scanForLock(serialNumber: String, keepConnection: Boolean): Single<RxBleDevice> {
    return rxBleClient.observeStateChanges()
      .startWith(rxBleClient.state)
      .distinctUntilChanged()
      .filter { it == RxBleClient.State.READY }
      .flatMapSingle {
        rxBleClient
          .scanBleDevices(
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            ScanFilter.Builder()
              .setServiceUuid(ParcelUuid(UUID.fromString(BluetoothConstants.LOCK_SERVICE_UUID)))
              .build()
          )
          .filter { scanResult ->
            val bleDeviceSerialNumber =
              extractSerialNumber(
                scanResult.scanRecord.serviceUuids?.map { it.toString() },
                BluetoothConstants.LOCK_SERVICE_UUID
              )
            serialNumber.equals(bleDeviceSerialNumber, true)
          }
          .map { it.bleDevice }
          .firstOrError()
      }
      .firstOrError()
      .doOnSubscribe { Timber.d("Scanning") }
      .compose {
        if (!keepConnection) {
          it.timeout(DEFAULT_TIMEOUT_FOR_SCANNING, TimeUnit.SECONDS)
        } else {
          it
        }
      }
      .onErrorResumeNext {
        when (it) {
          is TimeoutException -> Single.error(DeviceNotFoundError())
          else -> Single.error(it)
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  /**
   * Send any command to the Lock.
   *
   * @param message - message in Byte format ex. 0x50
   * @param params - list of parameters (in Byte format), there are optional
   *    ex. 0x02 as "Force unlock" parameter to "Unlock" command
   *
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.sendCommand(<message>, <params>)` will be called.
   */
  override suspend fun sendCommand(message: Byte, params: ByteArray?): ByteArray? {
    rxBleClient.checkBluetoothState()
    return lockConnectionWrapper?.sendCommand(message, params)
  }

  /**
   * Sends a GET_STATE (0x5A) command to the lock.
   * The response is a ByteArray representing the lock's status, structured as follows:
   * `[COMMAND, COMMAND_RESULT_STATUS, ACTUAL_LOCK_STATE, LOCK_STATUS]`.
   *
   * For example, a response might look like [5A, 00, 02, 00] where:
   * - 5A indicates the GET_STATE command.
   * - 00 means the command completed successfully.
   * - 02 represents the lock's current state (e.g., Lock Closed).
   * - 00 indicates the last change in lock status occurred without any problems.
   *      A LOCK_STATUS value of 01 indicates an error (LOCK_STATUS_ERROR_JAMMED),
   *      meaning the lock was jammed during the last attempt to change its state.
   *
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.getLockState()` will be called.
   */
  override suspend fun getLockState(): ByteArray? {
    rxBleClient.checkBluetoothState()
    return lockConnectionWrapper?.getLockState()
  }

  /**
   * Sends an OPEN_LOCK (0x51) command to unlock the lock.
   *
   * @param param The OPEN_LOCK command can use additional parameters:
   * - [BluetoothConstants.PARAM_NONE] (0x00): Unlocks the lock. This is the default setting.
   * - [BluetoothConstants.PARAM_AUTO] (0x01): Unlock from Auto Unlock feature.
   * - [BluetoothConstants.PARAM_FORCE] (0x02): Forces lock to unlock the lock till jam.
   * - [BluetoothConstants.PARAM_WITHOUT_PULL] (0x03): Opens the lock without Pull Spring, if configured.
   * For more information, visit [Tedee Lock BLE API documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).
   *
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.openLock(<param>)` will be called.
   */
  override suspend fun openLock(param: Byte) {
    rxBleClient.checkBluetoothState()
    lockConnectionWrapper?.openLock(param)
  }

  /**
   * Sends a CLOSE_LOCK (0x50) command to lock the lock.
   *
   * @param param The CLOSE_LOCK command can use additional parameters:
   * - [BluetoothConstants.PARAM_NONE] (0x00): Lock the lock. This is the default setting.
   * - [BluetoothConstants.PARAM_FORCE] (0x02): Forces lock to close the lock till jam.
   * For more information, visit [Tedee Lock BLE API documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).
   *
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.closeLock(<param>)` will be called.
   */
  override suspend fun closeLock(param: Byte) {
    rxBleClient.checkBluetoothState()
    lockConnectionWrapper?.closeLock(param)
  }

  /**
   * Sends a PULL_SPRING (0x52) command to pull the spring
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.pullSpring()` will be called.
   */
  override suspend fun pullSpring() {
    rxBleClient.checkBluetoothState()
    lockConnectionWrapper?.pullSpring()
  }

  /**
   * Sends the SET_SIGNED_TIME (0x71) command to the lock with the provided signed time.
   * This command is crucial for ensuring that the lock's operations are synchronized with a trusted time source,
   * enhancing the security and reliability of its operations.
   *
   * @param signedTime The signed time to be set on the lock. The `signedTime` parameter should contain
   * the datetime and signature obtained from a Tedee API, formatted as a SignedTime object.
   *
   * Before sending the command, this function checks the state of Bluetooth and location services:
   * - [RxBleClient.State.BLUETOOTH_NOT_AVAILABLE]: Bluetooth is unavailable on the device.
   * - [RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED]: Required location permissions are not granted.
   * - [RxBleClient.State.BLUETOOTH_NOT_ENABLED]: Bluetooth is not enabled.
   * - [RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED]: Location services are disabled.
   * If any preconditions are not met, [onError] with [BluetoothDisabled] is called to handle the situation.
   * Otherwise (i.e., RxBleClient.State.READY), `lockConnectionWrapper?.setSignedTime(<singedTime>)` will be called.
   */
  override suspend fun setSignedTime(signedTime: SignedTime): ByteArray? {
    rxBleClient.checkBluetoothState()
    return lockConnectionWrapper?.setSignedTime(signedTime)
  }

  /**
   * Retrieves the firmware version from the lock.
   *
   * Sends the GET_VERSION (0x11) command to the lock and processes the response
   * to extract the firmware version details.
   *
   * The response ByteArray example: [11, 00, 02, 04, 5B, E3, 01]
   * - 11: Command code for GET_VERSION.
   * - 00: Command result status (success).
   * - 02 04 5B E3 01: Version details.
   *
   * Parses it to a FirmwareVersion object.
   * Example:
   * - FirmwareVersion(softwareType=0, version="2.4.23523")
   *
   * where:
   * - softwareType = 0 means the type of software.
   * - "2.4.23523" means the firmware version of the lock.
   *
   * * @param isLockAdded If true, indicates that the lock is added to the account and will execute
   *  *                    an encrypted (secure) command; otherwise, it will execute an unencrypted
   *  *                    (unsecure) command.
   * @return FirmwareVersion object containing the firmware version details.
   * @throws tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError if the device needs to be reset.
   */
  override suspend fun getFirmwareVersion(isLockAdded: Boolean): FirmwareVersion? {
    rxBleClient.checkBluetoothState()
    return lockConnectionWrapper?.getFirmwareVersion(isLockAdded)
  }

  /**
   * Retrieves the device settings from the lock.
   *
   * Sends the GET_SETTINGS (0x20) command to the lock and processes the response
   * to extract the device settings details.
   *
   * The response ByteArray example: [20, 00, 00, 01, 0E, 00, 3C, 00, 05, 00, 05, 00, 05]
   * - 20: Command code for GET_SETTINGS.
   * - 00: Command result status (success).
   * - Remaining bytes: Device settings details.
   *
   * Parses it to a DeviceSettings object.
   * Example:
   * - DeviceSettings(autoLockEnabled=false, autoLockDelay=270, autoLockImplicitEnabled=false,
   *                  autoLockImplicitDelay=5, pullSpringEnabled=false, pullSpringDuration=60,
   *                  autoPullSpringEnabled=false, postponedLockEnabled=false, postponedLockDelay=5,
   *                  buttonLockEnabled=false, buttonUnlockEnabled=false, hasUnpairedKeypad=null,
   *                  isCustomPullSpringDuration=false, isCustomPostponedLockDelay=false, isAsync=null,
   *                  deviceId=-1)
   *
   * * @param isLockAdded If true, indicates that the lock is added to the account and will execute
   *  *                    an encrypted (secure) command; otherwise, it will execute an unencrypted
   *  *                    (unsecure) command.
   * @return DeviceSettings object containing the device settings details.
   * @throws tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError if the device needs to be reset.
   */
  override suspend fun getDeviceSettings(isLockAdded: Boolean): DeviceSettings? {
    rxBleClient.checkBluetoothState()
    return lockConnectionWrapper?.getDeviceSettings(isLockAdded)
  }

  override suspend fun waitForLockStatusChange(lockState: Byte, timeoutInSeconds: Long) {
    lockConnectionWrapper?.waitForLockStatusChange(lockState, timeoutInSeconds)
  }

  /** Clear all resources and close active connection */
  override fun clear() {
    lockConnectionWrapper?.closeConnection()
    scanningDisposable.clear()
    lockConnectionWrapper = null
    secureConnectionListener = null
  }

  /** Disconnect from the lock and notify the listener */
  override fun disconnect() {
    scanningDisposable.clear()
    lockConnectionWrapper?.closeConnection()
    secureConnectionListener?.onLockConnectionChanged(isConnecting = false, isConnected = false)
  }

  private fun onError(it: Throwable) {
    Timber.e(it, "Error in lock connection manager")
    secureConnectionListener?.onError(it)
  }

  private fun hasInvalidSerialNumber(serialNumber: String): Boolean {
    return if (serialNumber.isEmpty()) {
      onError(InvalidSerialNumberError())
      true
    } else {
      false
    }
  }

  private fun hasDeniedPermissions(context: Context): Boolean {
    val notGrantedPermissions = checkDeniedPermissions(context)
    if (notGrantedPermissions.isNotEmpty()) {
      secureConnectionListener?.onError(NoPermissionsError(notGrantedPermissions))
      return true
    }
    return false
  }
}

const val DEFAULT_TIMEOUT_FOR_SCANNING = 30L
