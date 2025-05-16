package tedee.mobile.sdk.ble.model

data class DeviceSettings(
  var autoLockEnabled: Boolean? = null,
  var autoLockDelay: Int? = DEFAULT_AUTO_LOCK_FROM_SEMI_LOCKED_DELAY_SECONDS,
  var autoLockImplicitEnabled: Boolean? = null,
  var autoLockImplicitDelay: Int? = DEFAULT_AUTO_LOCK_FROM_SEMI_LOCKED_DELAY_SECONDS,
  var pullSpringEnabled: Boolean? = null,
  var pullSpringDuration: Int? = DEFAULT_PULL_SPRING_DURATION_SECONDS,
  var autoPullSpringEnabled: Boolean? = null,
  var postponedLockEnabled: Boolean? = null,
  var postponedLockDelay: Int? = DEFAULT_POSTPONED_LOCK_DELAY,
  var buttonLockEnabled: Boolean? = null,
  var buttonUnlockEnabled: Boolean? = null,
  var hasUnpairedKeypad: Boolean? = null,
  var isAsync: Boolean? = null,
  var isCustomPullSpringDuration: Boolean = false,
  var isCustomPostponedLockDelay: Boolean = false,
  var deviceId: Int = -1,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DeviceSettings

    if (autoLockEnabled != other.autoLockEnabled) return false
    if (autoLockDelay != other.autoLockDelay) return false
    if (autoLockImplicitDelay != other.autoLockImplicitDelay) return false
    if (autoLockImplicitEnabled != other.autoLockImplicitEnabled) return false
    if (pullSpringEnabled != other.pullSpringEnabled) return false
    if (pullSpringDuration != other.pullSpringDuration) return false
    if (autoPullSpringEnabled != other.autoPullSpringEnabled) return false
    if (postponedLockEnabled != other.postponedLockEnabled) return false
    if (postponedLockDelay != other.postponedLockDelay) return false
    if (buttonLockEnabled != other.buttonLockEnabled) return false
    if (buttonUnlockEnabled != other.buttonUnlockEnabled) return false
    if (hasUnpairedKeypad != other.hasUnpairedKeypad) return false
    if (isAsync != other.isAsync) return false

    return true
  }

  override fun hashCode(): Int {
    var result = autoLockEnabled?.hashCode() ?: 0
    result = 31 * result + (autoLockDelay ?: 0)
    result = 31 * result + (autoLockImplicitDelay ?: 0)
    result = 31 * result + (autoLockImplicitEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (pullSpringEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (pullSpringDuration ?: 0)
    result = 31 * result + (autoPullSpringEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (postponedLockEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (postponedLockDelay ?: 0)
    result = 31 * result + (buttonLockEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (buttonUnlockEnabled?.toString()?.hashCode() ?: 0)
    result = 31 * result + (hasUnpairedKeypad?.toString()?.hashCode() ?: 0)
    result = 31 * result + (isAsync?.toString()?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "DeviceSettings(autoLockEnabled=$autoLockEnabled, " +
        "autoLockDelay=$autoLockDelay," +
        " autoLockImplicitEnabled=$autoLockImplicitEnabled, " +
        "autoLockImplicitDelay=$autoLockImplicitDelay, " +
        "pullSpringEnabled=$pullSpringEnabled, " +
        "pullSpringDuration=$pullSpringDuration, " +
        "autoPullSpringEnabled=$autoPullSpringEnabled, " +
        "postponedLockEnabled=$postponedLockEnabled," +
        " postponedLockDelay=$postponedLockDelay, " +
        "buttonLockEnabled=$buttonLockEnabled," +
        " buttonUnlockEnabled=$buttonUnlockEnabled, " +
        " hasUnpairedKeypad=$hasUnpairedKeypad, " +
        "isCustomPullSpringDuration=$isCustomPullSpringDuration, " +
        "isCustomPostponedLockDelay=$isCustomPostponedLockDelay," +
        "isAsync=$isAsync," +
        " deviceId=$deviceId)"
  }

  companion object {
    const val DEFAULT_PULL_SPRING_DURATION_SECONDS = 2
    const val DEFAULT_AUTO_LOCK_FROM_SEMI_LOCKED_DELAY_SECONDS = 5
    const val DEFAULT_POSTPONED_LOCK_DELAY = 5
  }
}
