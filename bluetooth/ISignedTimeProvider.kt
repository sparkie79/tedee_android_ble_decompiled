package tedee.mobile.sdk.ble.bluetooth

import tedee.mobile.sdk.ble.model.SignedTime

/**
 * Interface defining a provider for signed date-time value.
 * Implementations of this interface are expected to fetch a signed date-time from the Tedee API whenever
 * the SDK requests it, to ensure the integrity of lock operations.
 */
fun interface ISignedTimeProvider {

  /**
   * Provides signed date time.
   * Signed date time should be fetched from Tedee API every time that sdk request it.
   *
   * @param callback A function to be called with the fetched signed date-time.
   *        it will looks like this: signedTimeProvider?.getSignedTime { setSignedTime(it) }
   *        where 'setSignedTime' is a method that applies the signed date-time to send a command to the lock.
   */
  fun getSignedTime(callback: (SignedTime) -> Unit)
}