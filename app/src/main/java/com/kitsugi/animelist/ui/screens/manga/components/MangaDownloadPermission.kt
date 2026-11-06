package com.kitsugi.animelist.ui.screens.manga.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kitsugi.animelist.R

/** API 29+ MediaStore exports need no broad storage/gallery permission. */
@Composable
fun rememberMangaDownloadPermissionGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val preferences = remember { context.getSharedPreferences("manga_download_ui", Context.MODE_PRIVATE) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            action?.invoke() // Notification permission is optional; a denial must not block download.
        } else Toast.makeText(context, R.string.manga_download_storage_permission, Toast.LENGTH_LONG).show()
    }
    return { action ->
        val permissions = buildList {
            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("notification_asked", false) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                preferences.edit().putBoolean("notification_asked", true).apply()
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) action()
        else { pending = action; launcher.launch(permissions.toTypedArray()) }
    }
}
