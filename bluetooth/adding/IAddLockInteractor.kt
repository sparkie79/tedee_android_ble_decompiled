package tedee.mobile.sdk.ble.bluetooth.adding

import tedee.mobile.sdk.ble.model.CreateDoorLockData
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion
import tedee.mobile.sdk.ble.model.RegisterDeviceData
import tedee.mobile.sdk.ble.model.SignedTime

/**
 * Interface defining operations for adding lock to account. This includes unsecure connecting to the lock,
 * getting all needed data from the lock (like firmware version, device settings, and signature), and registering the device.
 */
interface IAddLockInteractor {

  /**
   * Initiates an add lock connection to the lock.
   *
   * This method is used to connect to the lock without a secure session, typically
   * for the purpose of adding the lock to an account.
   *
   * @param serialNumber The serial number of the device, e.g., "12345678-901234".
   * @param keepConnection If true, maintains the connection after the initial setup;
   *                       if false, the connection has a timeout (see [DEFAULT_TIMEOUT_FOR_SCANNING]).
   * @param addLockConnectionListener Callback interface for lock connection changes.
   * */
  fun connectForAdding(
    serialNumber: String,
    keepConnection: Boolean = true,
    addLockConnectionListener: IAddLockConnectionListener
  )

  /** Disconnect from the lock and notify the listener */
  fun disconnect()

  /**
   * Sends the SET_SIGNED_TIME (0x71) command to the lock with the provided signed time.
   * This command is crucial for ensuring that the lock's operations are synchronized with a trusted time source,
   * enhancing the security and reliability of its operations.
   *
   * @param signedTime The signed time to be set on the lock. The `signedTime` parameter should contain
   * the datetime and signature obtained from a Tedee API, formatted as a SignedTime object.
   */
  suspend fun setSignedTime(signedTime: SignedTime): ByteArray?
  /**
   * Retrieves the firmware version from the lock (without secure session).
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
  suspend fun getUnsecureFirmwareVersion(isLockAdded: Boolean): FirmwareVersion?

  /**
   * Retrieves the device settings from the lock (without secure session).
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
  suspend fun getUnsecureDeviceSettings(isLockAdded: Boolean): DeviceSettings?

  /**
   * Retrieves the signature from the lock.
   *
   * Sends the REQUEST_SIGNED_SERIAL (0x74) command to the lock and processes the response
   * to extract the signature.
   *
   * The response ByteArray example:
   * [7A, 20, 26, 02, 01, 00, 04, 02, 66, 71, 84, 79, 30, 45, 02, 20, 1E, FC, 8B, 8A, 98, 22, 90, 0A, E4, 03,
   *  1D, C2, 56, 7B, BD, EF, 0A, A8, A2, 1A, B8, AD, 50, 09, C8, C4, 6A, 62, FC, BF, 6F, 37, 02, 21, 00, C7,
   *  CF, 82, C1, BA, 9A, 95, C4, 76, 41, 18, DF, 11, B7, C5, 73, B4, F1, 90, 2B, 1D, E3, E6, 31, 50, 19, 78,
   *  F6, 21, 77, A7, EB]
   *
   * Encodes it to a Base64 string representing the signature.
   * Example:
   * - "ICYCAQAEAmZxhHkwRQIgHvyLipgikArkAx3CVnu97wqoohq4rVAJyMRqYvy/bzcCIQDHz4LBupqVxHZBGN8Rt8VztPGQKx3j5jFQGXj2IXen6w=="
   *
   * @return Base64 encoded string representing the signature.
   * @throws Throwable if an error occurs during the process.
   */
  suspend fun getSignature(): String?

  /**
   * Retrieves all necessary data from the lock required to add it to your account.
   *
   * This function retrieves device settings, firmware version,
   * and the signature from the lock, then combines them to create a `CreateDoorLockData` object.
   *
   * The steps involved are:
   * 1. Get the device settings and revision using `getUnsecureDeviceSettings`.
   * 2. Get the firmware version using `getUnsecureFirmwareVersion`.
   * 4. Get the signature using `getSignature()`.
   * 5. Combine the retrieved data into a `CreateDoorLockData` object.
   *
   * @param activationCode The activation code for the lock.
   * @param serialNumber The serial number of the lock.
   * @return A `CreateDoorLockData` object containing all the necessary data to add the lock to your account.
   * @throws Throwable if an error occurs during the process.
   */
  suspend fun getAddLockData(activationCode: String, serialNumber: String): CreateDoorLockData?

  /**
   * Registers the device with the given registration data.
   *
   * This function uses the `lockConnectionWrapper`
   * to register the device. It sends the registration data and handles the response
   * by observing the registration notification.
   *
   * The steps involved are:
   * 1. Register the device using `lockConnectionWrapper?.registerDevice(registerDeviceData)`.
   * 2. Observe the registration notification to confirm the result.
   *
   * The function uses the following Bluetooth constants:
   * - `BluetoothConstants.DEVICE_REGISTER`: The command code used to initiate the device registration process.
   * - `BluetoothConstants.NOTIFICATION_REGISTER`: The notification code used to observe the registration result.
   *
   * @param registerDeviceData The data required to register the device, encapsulated in a `RegisterDeviceData` object.
   * @throws Throwable if an error occurs during the process.
   */
  suspend fun registerDevice(registerDeviceData: RegisterDeviceData)

  /** Clear all resources and close active connection */
  fun clear()
}