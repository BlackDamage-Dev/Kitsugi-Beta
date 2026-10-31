package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiAccountNotConnectedState
import com.kitsugi.animelist.ui.components.KitsugiEmptyState

/** Empty state shown when the user hasn't connected their account. */
@Composable
internal fun MyListNotConnectedState(
    selectedTabIndex: Int,
    isSimklSessionExpired: Boolean,
    onLogin: () -> Unit
) {
    val platformName = when (selectedTabIndex) {
        MY_LIST_ANILIST_TAB_INDEX -> "AniList"
        MY_LIST_MAL_TAB_INDEX -> "MyAnimeList"
        MY_LIST_SIMKL_TAB_INDEX -> "Simkl"
        MY_LIST_KITSU_TAB_INDEX -> "Kitsu"
        MY_LIST_SHIKIMORI_TAB_INDEX -> "Shikimori"
        MY_LIST_BANGUMI_TAB_INDEX -> "Bangumi"
        else -> "Kitsugi"
    }
    val simklSessionExpired = selectedTabIndex == MY_LIST_SIMKL_TAB_INDEX && isSimklSessionExpired

    KitsugiAccountNotConnectedState(
        platformName = platformName,
        onConnectClick = onLogin,
        modifier = Modifier,
        titleOverride = if (simklSessionExpired) "Simkl Oturum Süresi Doldu" else null,
        subtitleOverride = if (simklSessionExpired) {
            "Simkl oturum süresi doldu. Senkronizasyonu sürdürmek için tekrar bağlayın."
        } else {
            null
        },
        actionTextOverride = if (simklSessionExpired) stringResource(R.string.yeniden_baglan) else null
    )
}

/** Empty state shown when the connected list has no entries yet. */
@Composable
internal fun MyListSyncPromptState() {
    KitsugiEmptyState(
        title = stringResource(R.string.listeniz_bos),
        subtitle = stringResource(R.string.hesabinizdaki_verileri_cekmek_icin_senkronize_et_butonunu_kullan),
        icon = Icons.FormatListBulleted
    )
    Spacer(modifier = Modifier.height(90.dp))
}
