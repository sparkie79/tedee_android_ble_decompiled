package tedee.mobile.sdk.ble.keystore

import timber.log.Timber

/**
 * Gets the public key for the mobile device. If there is no public key, it creates a new one.
 *
 * @return The public key as a String if it exists or can be created, or null if it cannot be generated.
 */
fun getMobilePublicKey(): String? {
  var publicKey = KeyStoreHelper.getMobilePublicKey()
  if (publicKey == null) {
    KeyStoreHelper.generateMobileKeyPair(
      {
        Timber.w("!!! Public key to register mobile:\n ${it.replace("\n", "\\n")}")
        publicKey = it
      },
      { Timber.e("!!! Cannot generate key pair") }
    )
  } else {
    Timber.w("!!! Public key to register mobile and get certificate:\n ${publicKey?.replace("\n", "\\n")}")
  }
  return publicKey
}