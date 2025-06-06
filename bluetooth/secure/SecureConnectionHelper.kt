package tedee.mobile.sdk.ble.bluetooth.secure

import android.util.Base64
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNotInitializedError
import tedee.mobile.sdk.ble.bluetooth.error.InvalidCertificateError
import tedee.mobile.sdk.ble.bluetooth.error.NoSignedTimeError
import tedee.mobile.sdk.ble.extentions.copyFromFirstByte
import tedee.mobile.sdk.ble.keystore.KeyStoreHelper
import tedee.mobile.sdk.ble.model.DeviceCertificate
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.KeyPair
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class SecureConnectionHelper(
  private val accessCertificate: DeviceCertificate?,
  private val secureInteractor: SecureInteractorInterface,
  private val sessionEstablishingNotifications: Observable<ByteArray>,
  private var notifications: Observable<ByteArray>? = null,
  private val updateSignedTime: () -> Unit,
  private val errorListener: (error: Exception) -> Unit,
  private val sessionReadyListener: (session: SecureSession) -> Unit,
  private val sessionTerminatedListener: (reason: String?) -> Unit,
) {

  private var clientVerifyMessage: List<List<Byte>>? = null
  private var currentClientVerifyMessageChunk = 0
  private lateinit var session: SecureSession
  private var establishSecureNotificationsDisposable: Disposable? = null
  private var lockNotificationsDisposable: Disposable? = null
  private var helloTimerDisposable: Disposable? = null
  private var compositeDisposable: CompositeDisposable = CompositeDisposable()
  private var isEstablishingConnection = false
  private var isConnectionClosed = false

  init {
    createSession()
  }

  private fun createSession() {
    val clientKeyPair = KeyStoreHelper.getMobileKeyPair()
    Timber.d("Create session using $this $accessCertificate")
    if (clientKeyPair != null) {
      session = createSession(clientKeyPair)
      establishSecureConnection()
    } else {
      Timber.e("ClientKeyPair is null")
    }
  }

  private fun createSession(clientKeyPair: KeyPair): SecureSession {
    return SecureSession(
      Base64.decode(accessCertificate?.certificate, Base64.DEFAULT),
      clientKeyPair.private
    ) {
      sessionTerminatedListener(null)
      reestablishSecureConnection()
    }
  }

  private fun reestablishSecureConnection() {
    isEstablishingConnection = false
    establishSecureNotificationsDisposable?.dispose()
    establishSecureNotificationsDisposable = null
    if (!isConnectionClosed) {
      Timber.w("Reestablishing secure connection after error")
      establishSecureConnection()
    }
  }

  private fun establishSecureConnection() {
    if (isEstablishingConnection) return
    isEstablishingConnection = true
    observeEstablishingNotifications()
    sendHello()
  }

  fun closeConnection(reason: String? = null, notifyListener: Boolean = true) {
    Timber.i("Close secure connection reason $reason")
    if (this::session.isInitialized) session.resetSession()
    isEstablishingConnection = false
    if (notifyListener) {
      sessionTerminatedListener(reason)
    }
    establishSecureNotificationsDisposable?.dispose()
    lockNotificationsDisposable?.dispose()
    helloTimerDisposable?.dispose()
    compositeDisposable.clear()
    isConnectionClosed = true
  }

  private fun sendHello() {
    Single.fromCallable { session.buildHello() }// CLIENT HELLO
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        startHelloTimer()
        secureInteractor.sendHello(it)
      }, {
        Timber.e(it, "Error in sendHello - reestablish secure session")
        reestablishSecureConnection()
      })
      .addTo(compositeDisposable)
  }

  private fun startHelloTimer() {
    helloTimerDisposable?.dispose()
    helloTimerDisposable = Single.timer(START_HELLO_TIMER_DELAY, TimeUnit.SECONDS)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        Timber.i("Reestablish secure session from hello timer")
        reestablishSecureConnection()
      }, { Timber.e(it, "error in startHelloTimer") })
      .addTo(compositeDisposable)
  }

  private fun handleServerHello(message: ByteArray) {
    Timber.i("Establishing NEW session - handle hello")
    helloTimerDisposable?.dispose()
    Completable.fromAction { session.parseHello(message) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        val serverVerifyMessage = ByteBuffer.allocate(SERVER_VERIFY_LENGTH)
        serverVerifyMessage.putLong(System.currentTimeMillis())
        secureInteractor.sendServerVerify(serverVerifyMessage.array())
      }, {
        Timber.e(it, "Error in handleServerHello - reestablish secure session")
        reestablishSecureConnection()
      })
      .addTo(compositeDisposable)
  }

  private fun handleServerVerify(message: ByteArray) {
    Single.fromCallable {
      session.parseVerify(message)
      val serverPublicKey = Base64.decode(accessCertificate?.devicePublicKey, Base64.DEFAULT)
      // verify server verification record signature (checked on client side)
      if (!session.peerVerify(
          PCrypto.decodePublicKey(
            serverPublicKey,
            session.getECParameterSpec()
          )
        )
      ) {
        val error = ParseException("Server verification record is invalid")
        Timber.e(error, "Got error in handleServerVerify")
      }
      session.verify()
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        clientVerifyMessage = it
        secureInteractor.sendClientVerify(it[0].toByteArray())
        currentClientVerifyMessageChunk = 1
        sendNextClientVerify()
      }, {
        Timber.e(it, "Error in handleServerVerify - reestablish secure session")
        reestablishSecureConnection()
      })
      .addTo(compositeDisposable)
  }

  private fun sendNextClientVerify() {
    clientVerifyMessage?.let { message ->
      if (currentClientVerifyMessageChunk == message.lastIndex) {
        secureInteractor.sendClientVerifyEnd(message[currentClientVerifyMessageChunk].toByteArray())
      } else {
        secureInteractor.sendClientVerify(message[currentClientVerifyMessageChunk].toByteArray())
        currentClientVerifyMessageChunk.inc()
      }
    }
  }

  private fun observeEstablishingNotifications() {
    if (establishSecureNotificationsDisposable != null && establishSecureNotificationsDisposable?.isDisposed == false) return
    establishSecureNotificationsDisposable = sessionEstablishingNotifications
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          Timber.d("Secure notification: ${parseNotificationName(it)}")
          when (it.first()) {
            SecureConnectionConstants.MESSAGE_HELLO -> handleServerHello(it.copyFromFirstByte())
            SecureConnectionConstants.SERVER_VERIFY -> handleServerVerify(it.copyFromFirstByte())
            SecureConnectionConstants.ALERT -> handleError(it.copyFromFirstByte())
            SecureConnectionConstants.SESSION_INITIALIZED -> {
              isEstablishingConnection = false
              helloTimerDisposable?.dispose()
              establishSecureNotificationsDisposable?.dispose()
              session.ready(it.copyFromFirstByte())
              sessionReadyListener(session)
            }
          }
        },
        { Timber.e(it, "Error in observing secure session notifications") }
      )
  }

  private fun observeLockNotifications() {
    if (lockNotificationsDisposable != null && lockNotificationsDisposable?.isDisposed == false) return
    val remaining = AtomicInteger()
    lockNotificationsDisposable = notifications
      ?.filter { it.first() == SecureConnectionConstants.DATA_NOT_ENCRYPTED && it.component2() == BluetoothConstants.NOTIFICATION_SIGNED_DATETIME }
      ?.observeOn(AndroidSchedulers.mainThread())
      ?.subscribe(
        {
          when {
            it.component3() == BluetoothConstants.API_SET_SIGNED_DATE_SUCCESS -> {
              lockNotificationsDisposable?.dispose()
              establishSecureConnection()
            }

            else -> {
              Timber.e("Error in setting signed date time")
              if (remaining.getAndIncrement() != MAX_RETRY_ATTEMPTS_ON_UPDATE_SIGNED_TIME_ERROR) {
                Timber.e("retry ${remaining.get()} time")
                Observable.timer(RETRY_UPDATE_SIGNED_TIME_DELAY, TimeUnit.SECONDS)
                  .observeOn(AndroidSchedulers.mainThread())
                  .subscribe { updateSignedTime() }
              } else {
                lockNotificationsDisposable?.dispose()
              }
            }
          }
        },
        { Timber.e(it, "error in observeLockNotifications") }
      )?.addTo(compositeDisposable)
  }

  private fun handleError(message: ByteArray) {
    val alertCode = mapSecureSessionAlert(message.first())
    Timber.w("ALERT = $alertCode")
    isEstablishingConnection = false
    helloTimerDisposable?.dispose()
    closeConnection(alertCode)
    when (message.first()) {
      ALERT_CODE_ERROR -> {
        errorListener.invoke(InvalidCertificateError())
        reestablishSecureConnection()
      }

      ALERT_CODE_NO_TRUSTED_TIME -> {
        Timber.e("No trusted time")
        observeLockNotifications()
        updateSignedTime()
        errorListener.invoke(NoSignedTimeError())
      }

      ALERT_CODE_TIMEOUT -> {
        reestablishSecureConnection()
      }

      ALERT_CODE_INVALID_CERT -> {
        Timber.e("Certificate is invalid")
        errorListener.invoke(InvalidCertificateError())
        closeConnection()
      }

      ALERT_CODE_NOT_REGISTERED -> {
        Timber.e("Device is no initialized")
        errorListener.invoke(DeviceNotInitializedError())
      }
    }
  }


  private fun mapSecureSessionAlert(byte: Byte): String {
    return when (byte) {
      ALERT_CODE_ERROR -> "ALERT_CODE_ERROR"
      ALERT_CODE_NO_TRUSTED_TIME -> "ALERT_CODE_NO_TRUSTED_TIME"
      ALERT_CODE_TIMEOUT -> "ALERT_CODE_TIMEOUT"
      ALERT_CODE_INVALID_CERT -> "ALERT_CODE_INVALID_CERT"
      ALERT_CODE_NOT_REGISTERED -> "ALERT_CODE_NOT_REGISTERED"
      else -> "Unknown"
    }
  }

  companion object {
    private const val ALERT_CODE_ERROR = 1.toByte()
    private const val ALERT_CODE_NO_TRUSTED_TIME = 2.toByte()
    private const val ALERT_CODE_TIMEOUT = 3.toByte()
    private const val ALERT_CODE_INVALID_CERT = 5.toByte()
    private const val ALERT_CODE_NOT_REGISTERED = 6.toByte()
    private const val SERVER_VERIFY_LENGTH = Long.SIZE_BYTES
    private const val START_HELLO_TIMER_DELAY = 5L //in seconds
    private const val MAX_RETRY_ATTEMPTS_ON_UPDATE_SIGNED_TIME_ERROR = 4
    private const val RETRY_UPDATE_SIGNED_TIME_DELAY = 5L

    private fun parseNotificationName(it: ByteArray): String {
      return when (it.first()) {
        SecureConnectionConstants.MESSAGE_HELLO -> "SECURE_SESSION_MESSAGE_HELLO"
        SecureConnectionConstants.SERVER_VERIFY -> "SECURE_SESSION_SERVER_VERIFY"
        SecureConnectionConstants.ALERT -> "SECURE_SESSION_ALERT"
        SecureConnectionConstants.CLIENT_VERIFY -> "SECURE_SESSION_CLIENT_VERIFY"
        SecureConnectionConstants.SESSION_INITIALIZED -> "SECURE_SESSION_INITIALIZED"
        else -> "UNKNOWN"
      }
    }
  }

  class ParseException(errorMessage: String) : Exception(errorMessage)
}