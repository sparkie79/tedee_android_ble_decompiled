package tedee.mobile.sdk.ble.model

data class CreateDoorLockData(
  var revision: Int? = 0,
  val serialNumber: String,
  var name: String = "",
  val timeZone: String,
  var softwareVersions: Array<FirmwareVersion>? = null,
  var deviceSettings: DeviceSettings? = null,
  var signature: String = "",
  val activationCode: String,
)  {

  fun setFirmware(arrayOf: Array<FirmwareVersion>) {
    softwareVersions = arrayOf
  }
  fun setDeviceSignature(signature: String) {
    this.signature = signature
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CreateDoorLockData

    if (revision != other.revision) return false
    if (serialNumber != other.serialNumber) return false
    if (name != other.name) return false
    if (timeZone != other.timeZone) return false
    if (softwareVersions != null) {
      if (other.softwareVersions == null) return false
      if (!softwareVersions.contentEquals(other.softwareVersions)) return false
    } else if (other.softwareVersions != null) return false
    if (deviceSettings != other.deviceSettings) return false
    if (signature != other.signature) return false
    if (activationCode != other.activationCode) return false

    return true
  }

  override fun hashCode(): Int {
    var result = revision ?: 0
    result = 31 * result + serialNumber.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + timeZone.hashCode()
    result = 31 * result + (softwareVersions?.contentHashCode() ?: 0)
    result = 31 * result + (deviceSettings?.hashCode() ?: 0)
    result = 31 * result + signature.hashCode()
    result = 31 * result + activationCode.hashCode()
    return result
  }
}