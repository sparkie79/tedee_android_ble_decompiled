package tedee.mobile.sdk.ble.bluetooth

import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion

private const val DEFAULT_TIME_TO_WAIT_FOR_STATE = 10L

/**
 * Interface defining operations for interacting with a lock. This includes connecting to the lock,
 * sending commands, and handling state changes.
 */
interface ILockInteractor {

  /**
   * Initiates a secure connection to the lock.
   *
   * @param serialNumber Serial number of the device ex. 12345678-901234
   * @param deviceCertificate A certificate required for secure connection to the device.
   * @param keepConnection If true, maintains the connection after initial setup;
   *                       if false, connection has timeout @see [DEFAULT_TIMEOUT_FOR_SCANNING].
   * @param secureConnectionListener Callback interface for lock connection changes.
   */
  fun connect(
    serialNumber: String,
    deviceCertificate: DeviceCertificate,
    keepConnection: Boolean = true,
    secureConnectionListener: ILockConnectionListener
  )

  /**
   * Send any command to the Lock.
   * The response is a ByteArray, structured as follows:
   * `[COMMAND_RESULT_STATUS, OTHER_INFORMATION]`.
   *
   * @param message - message in Byte format ex. 0x50
   * @param params - list of parameters (in Byte format), there are optional
   *    ex. 0x02 as "Force unlock" parameter to "Unlock" command
   */
  suspend fun sendCommand(message: Byte, params: ByteArray? = null): ByteArray?

  /**
   * Sends a GET_STATE (0x5A) command to the lock.
   *
   * For example, a response might look like [00, 02, 00] where:
   * - 00 means the command completed successfully.
   * - 02 represents the lock's current state (e.g., Lock Closed).
   * - 00 indicates the last change in lock status occurred without any problems.
   *      A LOCK_STATUS value of 01 indicates an error (LOCK_STATUS_ERROR_JAMMED),
   *      meaning the lock was jammed during the last attempt to change its state.
   */
  suspend fun getLockState(): ByteArray?

  /**
   * Sends an OPEN_LOCK (0x51) command to unlock the lock.
   *
   * @param param The OPEN_LOCK command can use additional parameters:
   * - [BluetoothConstants.PARAM_NONE] (0x00): Unlocks the lock. This is the default setting.
   * - [BluetoothConstants.PARAM_AUTO] (0x01): Unlock from Auto Unlock feature.
   * - [BluetoothConstants.PARAM_FORCE] (0x02): Forces lock to unlock the lock till jam.
   * - [BluetoothConstants.PARAM_WITHOUT_PULL] (0x03): Opens the lock without Pull Spring, if configured.
   * For more information, visit
   * [Tedee Lock BLE API documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).
   */
  suspend fun openLock(param: Byte = BluetoothConstants.PARAM_NONE)

  /**
   * Sends a CLOSE_LOCK (0x50) command to lock the lock.
   *
   * @param param The CLOSE_LOCK command can use additional parameters:
   * - [BluetoothConstants.PARAM_NONE] (0x00): Lock the lock. This is the default setting.
   * - [BluetoothConstants.PARAM_FORCE] (0x02): Forces lock to close the lock till jam.
   * For more information, visit [Tedee Lock BLE API documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).
   */
  suspend fun closeLock(param: Byte = BluetoothConstants.PARAM_NONE)

  /**
   * Sends a PULL_SPRING (0x52) command to pull the spring.
   * The response is a Byte representing the lock's result, 00 means the command completed successfully.
   */
  suspend fun pullSpring()

  /**
   * Sends the SET_SIGNED_TIME (0x71) command to the lock with the provided signed time.
   * This command is crucial for ensuring that the lock's operations are synchronized with a trusted time source,
   * enhancing the security and reliability of its operations.
   *
   * @param signedTime The signed time to be set on the lock. The `signedTime` parameter should contain
   * the datetime and signature obtained from a Tedee API, formatted as a SignedTime object.
   */
  suspend fun setSignedTime(signedTime: SignedTime): ByteArray?

  /** Disconnect from the lock and notify the listener */
  fun disconnect()

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
  suspend fun getFirmwareVersion(isLockAdded: Boolean): FirmwareVersion?

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
  suspend fun getDeviceSettings(isLockAdded: Boolean): DeviceSettings?


  /**
   * Waits for lock state change.
   *
   * This method is used to wait for full lock state change, e.g to be sure the lock was opened or closed.
   *
   * ```
   * try {
   *    closeLock()
   *    waitForLockStatusChange(BluetoothConstants.LOCK_CLOSED)
   *    openLock()
   *    waitForLockStatusChange(BluetoothConstants.LOCK_OPENED)
   * } catch (e: Exception) {
   *    uiSetupHelper.onFailureRequest(e)
   * }
   * ```
   *
   * @param lockState The lock state, e.g. BluetoothConstants.LOCK_OPENED, BluetoothConstants.LOCK_CLOSED.
   * @param timeoutInSeconds Timeout of waiting in seconds.
   * */
  suspend fun waitForLockStatusChange(lockState: Byte, timeoutInSeconds: Long = DEFAULT_TIME_TO_WAIT_FOR_STATE)

  /** Clear all resources and close active connection */
  fun clear()
}