package com.kitsugi.animelist.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.account.AccountErrorFormatter
import com.kitsugi.animelist.data.account.AccountFormValidation
import com.kitsugi.animelist.data.account.AccountSyncReport
import com.kitsugi.animelist.data.account.AccountSyncSection
import com.kitsugi.animelist.data.account.AddonVaultBackup
import com.kitsugi.animelist.data.account.KitsugiAccountClient
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.account.LinkedAccountVault
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Button
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Authentication is not the same as vault readiness; each sync category reports its own result. */
@Composable
fun KitsugiAccountContent() {
    val context = LocalContext.current.applicationContext
    val dao = remember { KitsugiDatabase.getDatabase(context).searchHistoryDao() }
    val scope = rememberCoroutineScope()
    val accent = LocalKitsugiAccent.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val accountSettings by settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val authSessionStatus by KitsugiAccountClient.client.auth.sessionStatus.collectAsState()
    val session = (authSessionStatus as? SessionStatus.Authenticated)?.session
    val loggedInEmail = session?.user?.email
    val owner = session?.user?.id
    val initializing = authSessionStatus is SessionStatus.Initializing
    val vaultStatus by LinkedAccountVault.status.collectAsState()
    val vaultConflicts by LinkedAccountVault.conflicts.collectAsState()
    val addonRestoreState by AddonVaultBackup.restoreState.collectAsState()
    val unlockedOwner by LinkedAccountVault.unlockedOwner.collectAsState()
    val vaultReady = owner != null && owner == unlockedOwner

    var email by rememberSaveable { mutableStateOf("") }
    // Passwords intentionally never use rememberSaveable or disk storage.
    var password by remember { mutableStateOf("") }
    var registrationConfirmation by remember { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var awaitingConfirmation by rememberSaveable { mutableStateOf(false) }
    var resendAllowedAt by rememberSaveable { mutableStateOf(0L) }
    var resendSeconds by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var addonRestoreConfirm by remember { mutableStateOf(false) }
    var addonTrustConfirm by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var conflictChoice by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(resendAllowedAt) {
        do {
            resendSeconds = ((resendAllowedAt - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0).toInt()
            if (resendSeconds > 0) delay(1_000)
        } while (resendSeconds > 0)
    }
    LaunchedEffect(owner) {
        AddonVaultBackup.refreshPending(context, owner)
        if (owner != null) {
            password = ""
            registrationConfirmation = ""
            if (awaitingConfirmation) {
                awaitingConfirmation = false
                isError = false
                message = context.getString(R.string.account_confirmation_success)
            }
        }
    }

    fun showError(prefix: String, error: Throwable) {
        isError = true
        message = "$prefix: ${AccountErrorFormatter.userMessage(error)}"
    }

    fun sectionLabel(section: AccountSyncSection): String = context.getString(when (section) {
        AccountSyncSection.SEARCH_HISTORY -> R.string.account_toggle_search_title
        AccountSyncSection.NOTIFICATIONS -> R.string.account_toggle_notif_title
        AccountSyncSection.VAULT -> R.string.account_toggle_backup_title
        AccountSyncSection.ADDONS -> R.string.account_toggle_addons_title
    })

    fun showSyncReport(report: AccountSyncReport, prefix: String = "") {
        isError = report.hasFailures
        val details = buildList {
            if (prefix.isNotBlank()) add(prefix)
            if (report.results.isEmpty()) add(context.getString(R.string.account_sync_none_selected))
            if (report.completed.isNotEmpty()) add(context.getString(
                R.string.account_sync_completed, report.completed.joinToString { sectionLabel(it) }
            ))
            report.results.forEach { (section, result) ->
                result.exceptionOrNull()?.let { add("${sectionLabel(section)}: ${AccountErrorFormatter.userMessage(it)}") }
            }
        }
        message = details.joinToString("\n")
    }

    fun submit() {
        if (busy || initializing) return
        val trimmedEmail = email.trim()
        val enteredPassword = password
        val register = isRegister
        if (!AccountFormValidation.isValidEmail(trimmedEmail) || enteredPassword.length < AccountFormValidation.MIN_SIGN_IN_PASSWORD) {
            isError = true
            message = context.getString(R.string.gecerli_bir_e_posta_ve_en_az_6_karakterli_sifre_gir)
            return
        }
        if (register && !AccountFormValidation.canRegister(trimmedEmail, enteredPassword, registrationConfirmation)) {
            isError = true
            message = context.getString(R.string.account_registration_password_rule)
            return
        }
        busy = true
        message = null
        isError = false
        scope.launch {
            try {
                if (register) {
                    KitsugiAccountRepository.signUp(context, trimmedEmail, enteredPassword)
                        .onSuccess { hasSession ->
                            if (hasSession) {
                                showSyncReport(KitsugiAccountRepository.syncSelected(context, dao), context.getString(R.string.account_created))
                            } else {
                                isRegister = false
                                awaitingConfirmation = true
                                resendAllowedAt = System.currentTimeMillis() + 60_000
                                message = context.getString(R.string.account_confirmation_sent)
                            }
                        }.onFailure { showError(context.getString(R.string.account_signup_failed), it) }
                } else {
                    KitsugiAccountRepository.signIn(context, trimmedEmail, enteredPassword)
                        .onSuccess {
                            showSyncReport(KitsugiAccountRepository.syncSelected(context, dao), context.getString(R.string.account_signed_in))
                        }.onFailure { showError(context.getString(R.string.account_signin_failed), it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(context.getString(R.string.esitleme_basarisiz), e)
            } finally {
                password = ""
                registrationConfirmation = ""
                busy = false
            }
        }
    }

    fun syncNow() {
        if (busy || owner == null) return
        busy = true
        message = null
        scope.launch {
            try {
                showSyncReport(KitsugiAccountRepository.syncSelected(context, dao))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(context.getString(R.string.esitleme_basarisiz), e)
            } finally { busy = false }
        }
    }

    fun trustAddonSources() {
        if (busy || owner == null || !vaultReady || !accountSettings.accountAddonSyncEnabled) return
        addonTrustConfirm = false
        busy = true
        message = null
        scope.launch {
            try {
                KitsugiAccountRepository.trustPendingAddonSources(context)
                    .onSuccess { count ->
                        isError = false
                        message = context.getString(R.string.account_addon_trusted_result, count)
                        AddonVaultBackup.refreshPending(context, owner)
                    }
                    .onFailure { showError(context.getString(R.string.account_addon_trust_failed), it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(context.getString(R.string.account_addon_trust_failed), e)
            } finally { busy = false }
        }
    }

    fun restoreAddons() {
        if (busy || owner == null || !vaultReady || !accountSettings.accountAddonSyncEnabled) return
        addonRestoreConfirm = false
        busy = true
        message = null
        scope.launch {
            try {
                KitsugiAccountRepository.restorePendingAddons(context)
                    .onSuccess { report ->
                        isError = report.remainingCloudstream + report.remainingManga + report.remainingRepositories > 0
                        message = context.getString(
                            R.string.account_addon_restore_result,
                            report.restoredCloudstream,
                            report.restoredManga,
                            report.restoredRepositories,
                            report.remainingCloudstream,
                            report.remainingManga,
                            report.remainingRepositories,
                            report.blockedByTrust
                        )
                        AddonVaultBackup.refreshPending(context, owner)
                    }
                    .onFailure { showError(context.getString(R.string.account_addon_restore_failed), it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(context.getString(R.string.account_addon_restore_failed), e)
            } finally {
                AddonVaultBackup.refreshPending(context, owner)
                busy = false
            }
        }
    }

    fun saveSetting(update: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { update() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(context.getString(R.string.account_settings_failed), e) }
            finally { busy = false }
        }
    }

    fun signOut() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                KitsugiAccountRepository.signOut(context).onSuccess {
                    isError = false
                    password = ""
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    awaitingConfirmation = false
                    message = context.getString(R.string.cikis_yapildi_yerel_veriler_cihazda_kalir)
                }.onFailure { showError(context.getString(R.string.account_signout_failed), it) }
            } finally { busy = false }
        }
    }

    if (addonRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) addonRestoreConfirm = false },
            title = { Text(stringResource(R.string.account_addon_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(
                        R.string.account_addon_restore_confirm,
                        addonRestoreState.pendingCloudstream,
                        addonRestoreState.pendingManga,
                        addonRestoreState.pendingRepositories
                    ))
                    Text(stringResource(R.string.account_addon_restore_safety_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { restoreAddons() }) {
                    Text(stringResource(R.string.account_addon_restore_action))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { addonRestoreConfirm = false }) {
                    Text(stringResource(R.string.vazgec))
                }
            }
        )
    }

    if (addonTrustConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) addonTrustConfirm = false },
            title = { Text(stringResource(R.string.account_addon_trust_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_addon_trust_confirm))
                    Column(
                        modifier = Modifier.heightIn(max = 180.dp).kitsugiVerticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        addonRestoreState.pendingTrustHosts.forEach { host ->
                            Text(host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(stringResource(R.string.account_addon_trust_safety_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { trustAddonSources() }) {
                    Text(stringResource(R.string.account_addon_trust_action))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { addonTrustConfirm = false }) {
                    Text(stringResource(R.string.vazgec))
                }
            }
        )
    }

    if (passwordDialog) {
        fun closePasswordDialog() {
            passwordDialog = false
            currentPassword = ""; newPassword = ""; confirmPassword = ""
        }
        AlertDialog(
            onDismissRequest = { if (!busy) closePasswordDialog() },
            title = { Text(stringResource(R.string.inline_change_your_kitsugi_password_d2623b3)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.inline_linked_accounts_are_preserved_if_9ef2bcf))
                    AccountPasswordField(currentPassword, { currentPassword = it }, !busy, stringResource(R.string.inline_current_password_52929cf))
                    AccountPasswordField(newPassword, { newPassword = it }, !busy, stringResource(R.string.inline_new_password_at_least_8_f9a9801))
                    AccountPasswordField(confirmPassword, { confirmPassword = it }, !busy, stringResource(R.string.inline_re_enter_new_password_5319ba5))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && currentPassword.isNotEmpty() && newPassword.length >= 8 && newPassword == confirmPassword,
                    onClick = {
                        busy = true; isError = false; message = null
                        val old = currentPassword
                        val next = newPassword
                        scope.launch {
                            try {
                                LinkedAccountVault.changePassword(context, old, next)
                                    .onSuccess { message = context.getString(R.string.sifre_degistirildi_bagli_hesap_yedegin_korundu) }
                                    .onFailure {
                                        showError(context.getString(R.string.account_password_change_failed), it)
                                        message += " " + context.getString(R.string.account_password_change_retry)
                                    }
                            } finally { closePasswordDialog(); busy = false }
                        }
                    }) { Text(stringResource(R.string.sifreyi_degistir)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { closePasswordDialog() }) { Text(stringResource(R.string.vazgec)) } }
        )
    }

    conflictChoice?.let { keepLocal ->
        AlertDialog(
            onDismissRequest = { if (!busy) conflictChoice = null },
            title = { Text(stringResource(R.string.inline_account_backup_conflict_d3c74ac)) },
            text = { Text(stringResource(
                if (keepLocal) R.string.account_conflict_keep_device else R.string.account_conflict_keep_cloud,
                vaultConflicts.joinToString()
            )) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; isError = false
                scope.launch {
                    try {
                        LinkedAccountVault.resolveConflict(context, keepLocal)
                            .onSuccess { message = context.getString(R.string.secimin_uygulandi_kasa_durumunu_kontrol_et) }
                            .onFailure { showError(context.getString(R.string.cakisma_cozulemedi), it) }
                    } finally { conflictChoice = null; busy = false }
                }
            }) { Text(stringResource(R.string.onayla)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { conflictChoice = null }) { Text(stringResource(R.string.vazgec)) } }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AccountCircle, contentDescription = null, tint = accent, modifier = Modifier.size(48.dp))
            Column {
                Text(
                    loggedInEmail ?: stringResource(R.string.ui_account_signed_out),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(if (loggedInEmail != null) R.string.ui_account_subtitle_signed_in else R.string.account_signed_out_subtitle),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (initializing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.account_loading_session), style = MaterialTheme.typography.bodySmall)
        } else if (loggedInEmail != null) {
            Text(vaultStatus, style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            Text(stringResource(R.string.account_backup_sync_section), style = MaterialTheme.typography.labelMedium,
                color = KitsugiColors.TextMuted, fontWeight = FontWeight.SemiBold)
            AccountSwitchRow(
                stringResource(R.string.account_toggle_backup_title), stringResource(R.string.account_toggle_backup_desc),
                accountSettings.accountBackupEnabled, !busy
            ) { enabled -> saveSetting { settingsDataStore.setAccountBackupEnabled(enabled) } }
            AccountSwitchRow(
                stringResource(R.string.account_toggle_addons_title), stringResource(R.string.account_toggle_addons_desc),
                accountSettings.accountAddonSyncEnabled, !busy
            ) { enabled -> saveSetting { settingsDataStore.setAccountAddonSyncEnabled(enabled) } }
            AccountSwitchRow(
                stringResource(R.string.account_toggle_search_title), stringResource(R.string.account_toggle_search_desc),
                accountSettings.accountSearchSyncEnabled, !busy
            ) { enabled -> saveSetting { settingsDataStore.setAccountSearchSyncEnabled(enabled) } }
            AccountSwitchRow(
                stringResource(R.string.account_toggle_notif_title), stringResource(R.string.account_toggle_notif_desc),
                accountSettings.accountNotificationSyncEnabled, !busy
            ) { enabled -> saveSetting { settingsDataStore.setAccountNotificationSyncEnabled(enabled) } }
            if (accountSettings.accountAddonSyncEnabled && addonRestoreState.ownerId == owner &&
                addonRestoreState.pendingTotal == 0 && addonRestoreState.message != null) {
                Text(addonRestoreState.message!!, style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            }
            if (accountSettings.accountAddonSyncEnabled && addonRestoreState.ownerId == owner && addonRestoreState.pendingTotal > 0) {
                Text(
                    stringResource(
                        R.string.account_addon_pending_summary,
                        addonRestoreState.pendingCloudstream,
                        addonRestoreState.pendingManga,
                        addonRestoreState.pendingRepositories
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary
                )
                addonRestoreState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary) }
                if (addonRestoreState.pendingTrustHosts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.account_addon_untrusted_hosts, addonRestoreState.pendingTrustHosts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextSecondary
                    )
                    OutlinedButton(
                        enabled = !busy && vaultReady && !addonRestoreState.isRestoring,
                        onClick = { addonTrustConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.account_addon_trust_action)) }
                }
                OutlinedButton(
                    enabled = !busy && vaultReady && !addonRestoreState.isRestoring,
                    onClick = { addonRestoreConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.account_addon_restore_action)) }
            }

            if (!vaultReady) {
                Text(stringResource(R.string.account_vault_locked_hint), style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
                AccountPasswordField(password, { password = it }, !busy, stringResource(R.string.kitsugi_sifresi))
                Button(
                    onClick = {
                        if (!busy) {
                            val enteredPassword = password
                            busy = true; isError = false; message = null
                            scope.launch {
                                try {
                                    KitsugiAccountRepository.signIn(context, loggedInEmail, enteredPassword)
                                        .onSuccess {
                                            if (LinkedAccountVault.unlockedOwner.value == owner) {
                                                message = context.getString(R.string.sifre_dogrulandi_guvenli_hesap_yedegin_acildi)
                                            } else {
                                                isError = true
                                                message = context.getString(R.string.giris_dogrulandi_ancak_kasa_acilamadi_x, LinkedAccountVault.status.value)
                                            }
                                        }.onFailure { showError(context.getString(R.string.account_password_verify_failed), it) }
                                } finally { password = ""; busy = false }
                            }
                        }
                    },
                    enabled = !busy && password.length >= AccountFormValidation.MIN_SIGN_IN_PASSWORD,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.sifreyi_dogrula_ve_kasayi_ac)) }
            }
            Text(stringResource(R.string.account_sync_selected_hint), style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            Button(
                onClick = { syncNow() },
                enabled = !busy && (accountSettings.accountBackupEnabled || accountSettings.accountAddonSyncEnabled || accountSettings.accountSearchSyncEnabled || accountSettings.accountNotificationSyncEnabled),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.inline_sync_now_2f993db)) }
            if (vaultConflicts.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.inline_conflicting_accounts_1_s_0c4c670,
                        vaultConflicts.map { if (it.startsWith("addons:")) context.getString(R.string.account_addon_conflict_label) else it.removePrefix("service:").removePrefix("settings:") }.distinct().joinToString()
                    ),
                    color = KitsugiColors.AccentRed
                )
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = true }) { Text(stringResource(R.string.inline_keep_accounts_on_this_device_fd2ebcf)) }
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = false }) { Text(stringResource(R.string.inline_keep_cloud_accounts_47e2ee5)) }
            }
            Text(stringResource(R.string.inline_if_your_password_is_reset_a9614b3), style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            OutlinedButton(enabled = !busy && vaultReady, onClick = { passwordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sifreyi_degistir))
            }
            OutlinedButton(enabled = !busy, onClick = { signOut() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.inline_sign_out_8d1ab12))
            }
        } else {
            if (authSessionStatus is SessionStatus.RefreshFailure) {
                Text(stringResource(R.string.account_refresh_failed), color = KitsugiColors.AccentRed, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, enabled = !busy,
                label = { Text(stringResource(R.string.account_email)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
            )
            AccountPasswordField(password, { password = it }, !busy, stringResource(R.string.sifre))
            if (isRegister) {
                AccountPasswordField(registrationConfirmation, { registrationConfirmation = it }, !busy, stringResource(R.string.account_password_confirmation))
                Text(stringResource(R.string.account_registration_password_rule), style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            }
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isRegister) R.string.ui_account_sign_up else R.string.ui_action_sign_in))
            }
            TextButton(enabled = !busy, onClick = {
                isRegister = !isRegister; password = ""; registrationConfirmation = ""; message = null; isError = false
            }) {
                Text(stringResource(if (isRegister) R.string.ui_account_prompt_sign_in else R.string.account_prompt_sign_up))
            }
            if (!isRegister) {
                TextButton(enabled = !busy && resendSeconds == 0 && AccountFormValidation.isValidEmail(email), onClick = {
                    if (!busy && resendSeconds == 0) {
                        val targetEmail = email.trim()
                        busy = true; message = null; isError = false
                        resendAllowedAt = System.currentTimeMillis() + 60_000
                        scope.launch {
                            try {
                                KitsugiAccountRepository.resendConfirmation(targetEmail)
                                    .onSuccess { awaitingConfirmation = true; message = context.getString(R.string.account_confirmation_sent) }
                                    .onFailure { showError(context.getString(R.string.account_confirmation_send_failed), it) }
                            } finally { busy = false }
                        }
                    }
                }) {
                    Text(if (resendSeconds > 0) stringResource(R.string.account_resend_countdown, resendSeconds) else stringResource(R.string.account_resend_confirmation))
                }
            }
        }

        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        message?.let { text ->
            Text(text, color = if (isError) KitsugiColors.AccentRed else KitsugiColors.AccentGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().background(KitsugiColors.SurfaceSoft, RoundedCornerShape(10.dp)).padding(12.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AccountPasswordField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AccountSwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
