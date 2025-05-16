package tedee.mobile.sdk.ble.extentions

internal fun ByteArray.copyFromFirstByte(): ByteArray = this.copyOfRange(1, this.size)

/**
 * Creates a string from a byte array where each byte is converted to its hexadecimal representation.
 * The bytes are separated by spaces for easy reading.
 *
 * @return A string containing the hexadecimal values of the bytes separated by spaces.
 * For example, if the byte array contains [81, 0], the function returns "51 00".
 */
fun ByteArray.print(): String = this.joinToString(" ") { String.format("%02X", it) }