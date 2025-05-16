package tedee.mobile.sdk.ble.bluetooth

/**
 * Interface definition for a listener to be notified of secure lock connection events.
 * Implementations of this interface can be used to receive callbacks when the secure connection status changes
 * or there is a change in the lock's state.
 */
interface ILockConnectionListener: IBaseLockConnectionListener {

  /**
   * Called when the secure connection status to the lock changes.
   * @param isConnected "true" if connected to the lock, false otherwise.
   * @param isConnecting "true" if sdk is connecting to the lock.
   */
  fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean)

  /**
   * Called when there is a change in the lock's state. It's  linked to the notification NOTIFICATION_LOCK_STATUS_CHANGE (0xBA), indicating a state update.
   * @param currentState A byte value representing the current state of the lock (e.g. locked, unlocked).
   * @param status: A byte indicating the result of the operation. A status of OK (0x00) signifies that the operation was successful and without issues.
   * Example: Notification message after the lock state changed from "Closing" to "Closed":
   * [0xBA, 0x05, 0x00] indicates a [NOTIFICATION] with [ACTUAL_STATE] being "Closing" and [STATUS] as "OK".
   * [0xBA, 0x06, 0x00] indicates a [NOTIFICATION] with [ACTUAL_STATE] being "Closed" and [STATUS] as "OK".
   */
  fun onLockStatusChanged(currentState: Byte, status: Byte)
}