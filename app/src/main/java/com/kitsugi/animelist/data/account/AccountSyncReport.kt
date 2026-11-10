package com.kitsugi.animelist.data.account

import kotlinx.coroutines.CancellationException

/** An unselected category is absent, not a successful sync. Failures stay visible. */
enum class AccountSyncSection { SEARCH_HISTORY, NOTIFICATIONS, VAULT, ADDONS }

data class AccountSyncReport(val results: Map<AccountSyncSection, Result<Unit>>) {
    val hasFailures: Boolean get() = results.values.any { it.isFailure }
    val completed: Set<AccountSyncSection> get() = results.filterValues { it.isSuccess }.keys
}

internal suspend fun runAccountSyncSteps(
    steps: Map<AccountSyncSection, suspend () -> Result<Unit>>
): AccountSyncReport {
    val results = linkedMapOf<AccountSyncSection, Result<Unit>>()
    steps.forEach { (section, operation) ->
        results[section] = try {
            operation().also { result ->
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    return AccountSyncReport(results)
}

internal class AccountSessionChangedException : IllegalStateException(
    "Hesap oturumu değişti. İşlem durduruldu; lütfen tekrar dene."
)
