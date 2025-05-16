package tedee.mobile.sdk.ble.extentions

import tedee.mobile.sdk.ble.BluetoothConstants

private const val HEX_PREFIX = "0x"
private const val SPACE = "\\s+"

/**
 * Converts a string of hex values into a byte array.
 * Each part of the string should be a hex number, separated by spaces.
 * For example, "0x01 0x02" becomes [1, 2] as bytes.
 *
 * @param hexString A string containing hex numbers separated by spaces.
 * @return A byte array representing the hex numbers or null if there are no valid hex numbers.
 */
fun parseHexStringToByteArray(hexString: String): ByteArray? {
  val hexValues = hexString.trim().split(SPACE.toRegex())
  val byteList = mutableListOf<Byte>()
  hexValues.forEach { value ->
    if (value.startsWith(HEX_PREFIX)) {
      val byteValue = parseHexStringToByte(value)
      byteValue?.let { byteList.add(it) }
    }
  }
  return if (byteList.isNotEmpty()) byteList.toByteArray() else null
}

/**
 * Converts a single hex string into a byte.
 * The string must represent a hex number, like "0x01".
 *
 * @param hexString A string representing a single hex number.
 * @return The corresponding byte or null if the string is not a valid hex number.
 */
fun parseHexStringToByte(hexString: String): Byte? {
  return try {
    hexString.removePrefix(HEX_PREFIX).toIntOrNull(16)?.toByte()
  } catch (e: NumberFormatException) {
    null
  }
}
/**
 * Gets the readable state of a lock based on its byte code.
 * Each byte corresponds to a different state, such as open or closed.
 *
 * @param this The byte representing the lock's state.
 * @return A string describing the lock's state or "UNKNOWN COMMAND" if the byte is unknown.
 */
fun Byte.getReadableLockState(): String {
  return when (this) {
    BluetoothConstants.LOCK_UNCALIBRATED -> "LOCK_UNCALIBRATED"
    BluetoothConstants.LOCK_CALIBRATION -> "LOCK_CALIBRATION"
    BluetoothConstants.LOCK_OPENED -> "LOCK_OPEN"
    BluetoothConstants.LOCK_PARTIALLY_OPEN -> "LOCK_PARTIALLY_OPEN"
    BluetoothConstants.LOCK_OPENING -> "LOCK_OPENING"
    BluetoothConstants.LOCK_CLOSING -> "LOCK_CLOSING"
    BluetoothConstants.LOCK_CLOSED -> "LOCK_CLOSED"
    BluetoothConstants.LOCK_SPRING_PULL -> "LOCK_SPRING_PULL"
    BluetoothConstants.LOCK_OPENING_WITH_PULL -> "LOCK_OPENING_WITH_PULL"
    BluetoothConstants.LOCK_UNKNOWN -> "LOCK_UNKNOWN"
    BluetoothConstants.LOCK_CALIBRATION_CANCELLED -> "LOCK_CALIBRATION_CANCELLED"
    BluetoothConstants.LOCK_IS_BEING_UPDATED -> "LOCK_IS_BEING_UPDATED"
    else -> "UNKNOWN COMMAND $this"
  }
}

/**
 * Converts lock notification bytes into a readable string format.
 * The first byte indicates the type of notification and the rest of the bytes provide the notification details.
 *
 * @param this The byte array containing the lock notification.
 * @return A readable string describing the lock notification or "UNKNOWN NOTIFICATION" if the type is unknown.
 */
fun ByteArray.getReadableLockNotification(): String {
  val header = this.first()
  val result = this.copyOfRange(1, this.size)
  val name = when (header) {
    BluetoothConstants.NOTIFICATION_LOCK_STATUS_CHANGE -> "NOTIFICATION_LOCK_STATUS_CHANGE"
    BluetoothConstants.NOTIFICATION_SIGNED_SERIAL -> "NOTIFICATION_SIGNED_SERIAL"
    BluetoothConstants.NOTIFICATION_SIGNED_DATETIME -> "NOTIFICATION_SIGNED_DATETIME"
    BluetoothConstants.NOTIFICATION_REGISTER -> "NOTIFICATION_REGISTER"
    else -> "UNKNOWN NOTIFICATION $header"
  }
  val payload = result.joinToString(" ") { String.format("%02X", it) }
  return "$name $payload"
}

/**
 * Converts the result of a lock command from bytes into a readable string.
 * The first byte represents the command, and the rest of the bytes provide command result.
 *
 * @param this The byte array containing the lock command result.
 * @return A readable string describing the result of the lock command.
 */
fun ByteArray.getReadableLockCommandResult(): String {
  val header = this.first()
  val result = this.copyOfRange(1, this.size)
  val name = BluetoothConstants.mapHeaderToLockCommandName(header)
  val payload = result[0].getResponse()
  return "$name $payload"
}

/**
 * Gets the lock's current state and status based on bytes.
 * It uses bytes to represent the current state and the last status change of the lock.
 *
 * @param this The byte array containing the lock's current state and status.
 * @return A readable string describing the lock's current state and status.
 */
fun ByteArray.getReadableLockStatusResult(): String {
  val currentState = this[1].getReadableLockState()
  val status = this[2].getReadableStatus()
  return "$currentState $status"
}

/**
 * Converts a response code byte into a readable string.
 * Each byte represents a different response, like "BUSY", "ERROR" or "SUCCESS".
 *
 * @param this The byte representing the response code.
 * @return A readable string corresponding to the response code or "UNKNOWN RESPONSE" if the byte is unknown.
 */
fun Byte.getResponse(): String {
  return when (this) {
    BluetoothConstants.API_RESULT_BUSY -> "BUSY"
    BluetoothConstants.API_RESULT_ERROR -> "ERROR"
    BluetoothConstants.API_RESULT_SUCCESS -> "SUCCESS"
    BluetoothConstants.API_RESULT_INVALID_PARAM -> "INVALID PARAM"
    BluetoothConstants.API_RESULT_NOT_CALIBRATED -> "NOT CALIBRATED"
    BluetoothConstants.API_RESULT_UNLOCK_ALREADY_CALLED_BY_AUTOUNLOCK -> "UNLOCK_ALREADY_CALLED_BY_AUTOUNLOCK"
    BluetoothConstants.API_RESULT_UNLOCK_ALREADY_CALLED_BY_OTHER_OPERATION -> "UNLOCK_ALREADY_CALLED_BY_OTHER_OPERATION"
    BluetoothConstants.API_RESULT_NOT_CONFIGURED -> "NOT_CONFIGURED"
    BluetoothConstants.API_RESULT_DISMOUNTED -> "DISMOUNTED"
    else -> "UNKNOWN RESPONSE"
  }
}

/**
 * Converts a byte representing the status of a lock into a readable string.
 * Each byte can represent different statuses, like "OK" or "JAMMED".
 *
 * @param this The byte representing the lock's status.
 * @return A readable string corresponding to the lock's status or "UNKNOWN STATUS" if the byte is unknown.
 */
fun Byte.getReadableStatus(): String {
  return when (this) {
    BluetoothConstants.LOCK_STATUS_ERROR_TIMEOUT -> "TIMEOUT"
    BluetoothConstants.LOCK_STATUS_ERROR_JAMMED -> "JAMMED"
    BluetoothConstants.LOCK_STATUS_OK -> "OK"
    else -> "UNKNOWN STATUS"
  }
}

fun Byte.toUnsignedInt()= this.toInt() and 0xFF

fun Short.toUnsignedInt() = this.toInt() and 0xFFFF

fun Byte.getBit(position: Int): Boolean = ((this.toInt() shr (position)) and 1) == 1