package tedee.mobile.sdk.ble.bluetooth

/**
 * Interface definition for a listener to be notified of general lock connection events and messages.
 * Implementations of this interface can be used to receive callbacks when indication messages are received,
 * notifications are received, errors occur during lock communication, or when the lock requires a factory reset.
 */
interface IBaseLockConnectionListener {

  /**
   * Called when a notification is received from the lock.
   * @param message The notification received from the lock as a ByteArray.
   */
  fun onNotification(message: ByteArray)

  /**
   * Called when an error occurs during the lock communication.
   * E.g. when the lock requires a factory reset DeviceNeedsResetError will be thrown.
   * @param throwable The Throwable representing the error that occurred.
   */
  fun onError(throwable: Throwable)
}