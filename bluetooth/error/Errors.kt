package tedee.mobile.sdk.ble.bluetooth.error

class BluetoothDisabled : Exception()
class NoPermissionsError(val permissions: List<String>) : Exception()
class InvalidSerialNumberError : Exception()
class InvalidCertificateError : Exception()
class NoSignedTimeError : Exception()
class DeviceNotFoundError : Exception()
class DeviceNotInitializedError : Exception()
class ConnectionWasDeadError(val error: Throwable) : Exception()
class NoWrapperListener : Exception()
class LockBusyError : Exception()
class LockInvalidParamError : Exception()
class LockIsNotCalibratedError : Exception()
class LockIsDismountedError : Exception()
class LockIsNotConfiguredError : Exception()
class AutoUnlockAlreadyCalledError : Exception()
class UnlockAlreadyCalledError : Exception()
class GeneralLockError(val response: Byte) : Exception()
class NotProvidedSignedTime : Exception()
class DeviceNeedsResetError(val isFromOldVersion: Boolean) : Exception()
class SetSignedTimeError : Exception()
class RequestSignatureError : Exception()
class RegisterDeviceError : Exception()
class LockJammedError: Exception()
class LockIsNotRespondingError: Exception()

fun shouldRetryOnError(error: Throwable): Boolean {
  return when (error) {
    is NoPermissionsError, is InvalidCertificateError, is NoSignedTimeError, is DeviceNotInitializedError -> false
    else -> true
  }
}
