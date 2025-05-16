package tedee.mobile.sdk.ble.bluetooth.adding

import tedee.mobile.sdk.ble.bluetooth.IBaseLockConnectionListener

/**
 * Interface definition for a listener to be notified of add lock connection events.
 * Implementations of this interface can be used to receive callbacks when the add lock connection status changes,
 * which is typically used when adding a new lock to an account.
 */
interface IAddLockConnectionListener: IBaseLockConnectionListener {

  /**
   * Called when the add lock connection status to the lock changes.
   * @param isConnected "true" if connected to the lock, false otherwise.
   * @param isConnecting "true" if sdk is connecting to the lock.
   */
  fun onUnsecureConnectionChanged(isConnecting: Boolean, isConnected: Boolean)
}