package tedee.mobile.sdk.ble

object BluetoothConstants {
  const val LOCK_SERVICE_UUID = "00000002-4899-489F-A301-FBEE544B1DB0"
  const val LOCK_NOTIFICATION_CHARACTERISTIC = "00000501-4899-489F-A301-FBEE544B1DB0"
  const val SECURE_SESSION_LOCK_SEND_CHARACTERISTIC = "00000401-4899-489F-A301-FBEE544B1DB0"
  const val SECURE_SESSION_LOCK_READ_NOTIFICATION_CHARACTERISTIC =
    "00000301-4899-489F-A301-FBEE544B1DB0"
  const val LOCK_READ_NOTIFICATION_CHARACTERISTIC = "00000101-4899-489F-A301-FBEE544B1DB0"

  //Commands
  const val CLOSE_LOCK = 0x50.toByte()
  const val OPEN_LOCK = 0x51.toByte()
  const val PULL_SPRING = 0x52.toByte()
  const val SET_SIGNED_TIME = 0x71.toByte()
  const val REQUEST_SIGNED_SERIAL = 0x74.toByte()
  const val GET_STATE = 0x5A.toByte()
  const val GET_VERSION = 0x11.toByte()
  const val GET_SETTINGS = 0x20.toByte()
  const val DEVICE_REGISTER = 0x72.toByte()

  //States
  const val LOCK_UNCALIBRATED = 0x00.toByte()
  const val LOCK_CALIBRATION = 0x01.toByte()
  const val LOCK_OPENED = 0x02.toByte()
  const val LOCK_PARTIALLY_OPEN = 0x03.toByte()
  const val LOCK_OPENING = 0x04.toByte()
  const val LOCK_CLOSING = 0x05.toByte()
  const val LOCK_CLOSED = 0x06.toByte()
  const val LOCK_SPRING_PULL = 0x07.toByte()
  const val LOCK_OPENING_WITH_PULL = 0x08.toByte()
  const val LOCK_UNKNOWN = 0x09.toByte()
  const val LOCK_CALIBRATION_CANCELLED = 0x10.toByte()
  const val LOCK_IS_BEING_UPDATED = 0x12.toByte()

  //Notifications
  const val NOTIFICATION_LOCK_STATUS_CHANGE = 186.toByte()
  const val NOTIFICATION_SIGNED_SERIAL = 122.toByte()
  const val NOTIFICATION_SIGNED_DATETIME = 123.toByte()
  const val NOTIFICATION_REGISTER = 124.toByte()
  const val NOTIFICATION_NEED_DATE_TIME = 0xA4.toByte()

  //BT API Results
  const val API_RESULT_SUCCESS = 0x00.toByte()
  const val API_RESULT_INVALID_PARAM = 0x01.toByte()
  const val API_RESULT_ERROR = 0x02.toByte()
  const val API_RESULT_BUSY = 0x03.toByte()
  const val API_RESULT_NOT_CALIBRATED = 0x05.toByte()
  const val API_RESULT_UNLOCK_ALREADY_CALLED_BY_AUTOUNLOCK = 0x06.toByte()
  const val API_RESULT_NO_PERMISSION = 0x07.toByte()
  const val API_RESULT_UNLOCK_ALREADY_CALLED_BY_OTHER_OPERATION = 0x0A.toByte()
  const val API_RESULT_NOT_CONFIGURED = 0x08.toByte()
  const val API_RESULT_DISMOUNTED = 0x09.toByte()
  const val API_SET_SIGNED_DATE_SUCCESS = 0x00.toByte()

  //Lock Statuses
  const val LOCK_STATUS_OK = 0x00.toByte()
  const val LOCK_STATUS_ERROR_JAMMED = 0x01.toByte()
  const val LOCK_STATUS_ERROR_TIMEOUT = 0x02.toByte()

  //Parameters
  const val PARAM_NONE = 0x00.toByte()
  const val PARAM_AUTO = 0x01.toByte()
  const val PARAM_FORCE = 0x02.toByte()
  const val PARAM_WITHOUT_PULL = 0x03.toByte()

  //Common parameters
  const val DEVICE_FIRMWARE = 0x00.toByte()

  fun mapHeaderToLockCommandName(header: Byte) = when (header) {
    CLOSE_LOCK -> "CLOSE_LOCK"
    OPEN_LOCK -> "OPEN_LOCK"
    PULL_SPRING -> "PULL_SPRING"
    SET_SIGNED_TIME -> "SET_SIGNED_TIME"
    GET_STATE -> "GET_STATE"
    else -> "UNKNOWN COMMAND $header"
  }
}