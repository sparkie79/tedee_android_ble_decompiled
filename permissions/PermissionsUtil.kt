package tedee.mobile.sdk.ble.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Checks which necessary permissions have not been granted by the user.
 *
 * @param context
 * @return A list containing the names of the permissions that are not granted.
 */
fun checkDeniedPermissions(context: Context): List<String> {
  return getBluetoothPermissions()
    .filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_DENIED }
}

/**
 *  Gets a list of bluetooth and location permissions that are required to connect to the lock
 *  Depending on the version of Android on the device, this list may vary.
 *
 *  @return A list of permissions needed for Bluetooth features.
 */
fun getBluetoothPermissions() =
  when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      mutableListOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
      )
    }

    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }
