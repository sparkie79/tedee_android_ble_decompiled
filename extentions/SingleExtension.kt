package tedee.mobile.sdk.ble.extentions

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.Flowables
import tedee.mobile.sdk.ble.bluetooth.error.LockBusyError
import java.util.concurrent.TimeUnit

private const val MAX_RETRY_ATTEMPTS_ON_BUSY_ERROR = 3
private const val DELAY_BETWEEN_RETRY_ATTEMPTS = 1L

internal fun <T> Single<T>.retryOnBusyError(
  delayBeforeRetry: Long = DELAY_BETWEEN_RETRY_ATTEMPTS,
  maxTimes: Int = MAX_RETRY_ATTEMPTS_ON_BUSY_ERROR
): Single<T> = this.retryIf(
  predicate = { it is LockBusyError },
  maxRetry = maxTimes,
  delayBeforeRetry = delayBeforeRetry
)

private fun <T> Single<T>.retryIf(
  predicate: (Throwable) -> Boolean,
  maxRetry: Int,
  delayBeforeRetry: Long,
  timeUnit: TimeUnit = TimeUnit.SECONDS
): Single<T> =
  retryWhen {
    Flowables.zip(
      it.map { throwable -> if (predicate(throwable)) throwable else throw throwable },
      Flowable.interval(delayBeforeRetry, timeUnit)
    )
      .map { pair ->
        if (pair.second >= maxRetry - 1) {
          throw pair.first
        }
      }
  }