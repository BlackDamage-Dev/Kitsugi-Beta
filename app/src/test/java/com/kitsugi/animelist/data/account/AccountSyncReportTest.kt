package com.kitsugi.animelist.data.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountSyncReportTest {
    @Test fun allDisabledIsNotReportedAsACompletedSync() = runBlocking {
        val report = runAccountSyncSteps(emptyMap())
        assertTrue(report.results.isEmpty())
        assertTrue(report.completed.isEmpty())
        assertFalse(report.hasFailures)
    }

    @Test fun notificationFailureDoesNotDisappearBehindOtherSuccesses() = runBlocking {
        val failure = IllegalStateException("test notification failure")
        val report = runAccountSyncSteps(linkedMapOf(
            AccountSyncSection.SEARCH_HISTORY to { Result.success(Unit) },
            AccountSyncSection.NOTIFICATIONS to { Result.failure(failure) },
            AccountSyncSection.VAULT to { Result.success(Unit) }
        ))
        assertTrue(report.hasFailures)
        assertEquals(setOf(AccountSyncSection.SEARCH_HISTORY, AccountSyncSection.VAULT), report.completed)
        assertSame(failure, report.results[AccountSyncSection.NOTIFICATIONS]?.exceptionOrNull())
    }

    @Test fun addonFailureIsReportedIndependentlyOfTheLinkedAccountVault() = runBlocking {
        val report = runAccountSyncSteps(linkedMapOf(
            AccountSyncSection.VAULT to { Result.success(Unit) },
            AccountSyncSection.ADDONS to { Result.failure(IllegalStateException("addon failure")) }
        ))
        assertTrue(report.hasFailures)
        assertEquals(setOf(AccountSyncSection.VAULT), report.completed)
        assertTrue(report.results[AccountSyncSection.ADDONS]?.isFailure == true)
    }

    @Test fun lockedVaultDoesNotPreventTheOtherCategoriesFromRunning() = runBlocking {
        val report = runAccountSyncSteps(linkedMapOf(
            AccountSyncSection.VAULT to { throw VaultLockedException() },
            AccountSyncSection.SEARCH_HISTORY to { Result.success(Unit) },
            AccountSyncSection.NOTIFICATIONS to { Result.success(Unit) }
        ))
        assertTrue(report.hasFailures)
        assertEquals(setOf(AccountSyncSection.SEARCH_HISTORY, AccountSyncSection.NOTIFICATIONS), report.completed)
    }

    @Test fun cancellationIsNotSwallowedOrReportedAsSuccess() = runBlocking {
        var secondRan = false
        try {
            runAccountSyncSteps(linkedMapOf(
                AccountSyncSection.SEARCH_HISTORY to { Result.failure(CancellationException("test")) },
                AccountSyncSection.NOTIFICATIONS to { secondRan = true; Result.success(Unit) }
            ))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(secondRan)
        }
    }

    @Test fun missingSdkClassBecomesAVisibleFailure() = runBlocking {
        val report = runAccountSyncSteps(mapOf(AccountSyncSection.VAULT to { throw NoClassDefFoundError("test") }))
        assertTrue(report.hasFailures)
        assertTrue(report.results[AccountSyncSection.VAULT]?.exceptionOrNull() is LinkageError)
    }
}
