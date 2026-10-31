package com.kitsugi.animelist.ui.navigation

import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

enum class MainTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Explore(R.string.tab_explore, Icons.Explore),
    MyList(R.string.tab_mylist, Icons.ListAlt),
    Search(R.string.tab_search, Icons.Search),
    Profile(R.string.tab_profile, Icons.Person),
    Settings(R.string.tab_settings, Icons.Settings)
}