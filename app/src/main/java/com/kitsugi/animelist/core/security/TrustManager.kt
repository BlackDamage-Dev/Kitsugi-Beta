package com.kitsugi.animelist.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

object TrustManager {
    private const val TAG = "TrustManager"

    // Keiyoushi and Tachiyomi official signing certificate SHA-256 hashes
    private val trustedSignatures = setOf(
        "55c304f5d95f606028f8bc9d79c8ca7356e9bc6a41b9e5d129994c379a2cf505", // Legacy Tachiyomi Extensions
        "c65538561d2d0b671a5a041f021703cf9203a7a9cc5f6d7be761c566fb5eb4d7", // Keiyoushi Extensions (pre-2026)
        "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2", // Keiyoushi Extensions (2026, extensionLib 1.6+)
        // Kitsugi-Manga-Plugins / builds dalındaki özel Manga-TR 1.6.24 APK'sının
        // Android debug imza sertifikası (CN=Android Debug, 2026-07-03 → 2056-06-25).
        // Bu repo kullanıcı tarafından açıkça eklenir; resmî Keiyoushi anahtarıyla
        // imzalanmadığı için önceden "Signature untrusted" ile kurulum düşüyordu.
        // Anahtar rotasyonunda genel "repo imzasına güven" akışı devreye girer.
        "a53115764ff5e259e0e5acec3c58a85166e88aad110cd8a20674eefea5d6c016",
    )

    fun getApkSignatureHash(context: Context, file: File): String? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            
            val packageInfo = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return null

            val signatureBytes = signatures[0].toByteArray()
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signatureBytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting signature: ${e.message}", e)
            null
        }
    }

    fun isSignatureTrusted(context: Context, signatureHash: String): Boolean {
        if (trustedSignatures.contains(signatureHash.lowercase())) {
            return true
        }

        val prefs = context.getSharedPreferences("security_trust", Context.MODE_PRIVATE)
        val userTrusted = prefs.getStringSet("trusted_signatures", emptySet()) ?: emptySet()
        return userTrusted.contains(signatureHash.lowercase())
    }

    fun trustSignature(context: Context, signatureHash: String) {
        val prefs = context.getSharedPreferences("security_trust", Context.MODE_PRIVATE)
        val userTrusted = (prefs.getStringSet("trusted_signatures", emptySet()) ?: emptySet()).toMutableSet()
        userTrusted.add(signatureHash.lowercase())
        prefs.edit().putStringSet("trusted_signatures", userTrusted).apply()
    }

    /**
     * Keiyoushi repo.json'daki meta.signingKeyFingerprint degerini dinamik olarak guven listesine ekler.
     * Bu sayede Keiyoushi imza anahtarini rotasyona ugratirsa uygulama otomatik uyum saglar.
     *
     * @param repoMeta MangaRepoClient.fetchRepoMeta() ile cekilen JSONObject
     */
    fun trustRepoSigningKey(context: Context, repoMeta: org.json.JSONObject) {
        try {
            val meta = repoMeta.optJSONObject("meta") ?: return
            val fingerprint = meta.optString("signingKeyFingerprint", "").trim()
            if (fingerprint.isBlank()) return
            if (!isSignatureTrusted(context, fingerprint)) {
                Log.i(TAG, "Repo'dan yeni imza anahtari guvenildi: $fingerprint")
                trustSignature(context, fingerprint)
            }
        } catch (e: Exception) {
            Log.w(TAG, "trustRepoSigningKey hata: ${e.message}")
        }
    }
}
