package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.data.model.ExternalAudioTrack
import com.kitsugi.animelist.data.repository.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Bir akış kaynağının (`StreamSource`) sunduğu tüm indirilebilir ses parçalarını keşfeder.
 *
 * Tek sesli akışlarda 1 öğe döner (kullanıcıya gereksiz seçim ekranı gösterilmeden doğrudan
 * indirme başlar); çok dilli (ör. Türkçe Dublaj + Orijinal Japonca/İngilizce) HLS, DASH,
 * MKV veya `ExtractorLink.audioTracks` kaynaklarında tüm dilleri listeler.
 */
object AudioTrackInspector {

    data class DownloadableAudioTrack(
        val key: String,
        val index: Int,
        val label: String,
        val lang: String,
        val directAudioUrl: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val codecHint: String = "",
        val channels: Int = 0,
        val isDefault: Boolean = false
    ) {
        fun displayTitle(turkishUi: Boolean): String {
            val extTrack = ExternalAudioTrack(
                url = directAudioUrl.orEmpty(),
                label = label,
                lang = lang,
                headers = headers,
                codecs = codecHint,
                channels = channels,
                isDefault = isDefault
            )
            val title = extTrack.displayTitle(turkishUi = turkishUi, fallbackIndex = index)
            val badge = codecBadge()
            return if (badge.isNotBlank()) "$title · $badge" else title
        }

        fun codecBadge(): String {
            val c = codecHint.lowercase(Locale.ROOT)
            return when {
                c.contains("ec-3") || c.contains("eac3") -> "E-AC3 → M4A"
                c.contains("ac-3") || c.contains("ac3") -> "AC3 → M4A"
                c.contains("truehd") || c.contains("true-hd") || c.contains("mlp") -> "TrueHD → M4A"
                c.contains("dts") -> "DTS → M4A"
                c.contains("opus") -> "OPUS → M4A"
                c.contains("flac") -> "FLAC → M4A"
                c.contains("vorbis") -> "VORBIS → M4A"
                c.contains("alac") -> "ALAC → M4A"
                c.contains("raw") || c.contains("pcm") || c.contains("wav") -> "PCM → M4A"
                c.contains("mpeg-l1") || c.contains("mpeg-l2") -> "MP2 → M4A"
                c.contains("mp4a.40.42") || c.contains("usac") -> "xHE-AAC → M4A"
                c.contains("mp4a") || c.contains("aac") -> if (channels > 2) "AAC ${channels}K → M4A" else "AAC"
                c.contains("mp3") || c.contains("mpeg") -> if (channels > 2) "MP3 → M4A" else "MP3"
                else -> ""
            }
        }
    }

    suspend fun inspect(
        resolvedUrl: String,
        source: StreamSource
    ): List<DownloadableAudioTrack> = withContext(Dispatchers.IO) {
        val baseHeaders = source.requestHeaders.orEmpty()
        val http = StreamDownloadHttpClient(
            addonName = source.addonName,
            streamTitle = source.title,
            isCloudStream = source.isCS,
            qualityValue = source.qualityValue,
            baseHeaders = baseHeaders
        )

        val discovered = ArrayList<DownloadableAudioTrack>()
        val seenUrls = HashSet<String>()

        // 1) Kardeş master URL ipucu veya ana URL üzerinden HLS / DASH manifest incelemesi
        val masterHint = baseHeaders[StreamDownloadHttpClient.META_MASTER_URL_HEADER]?.trim()
        val manifestCandidateUrl = masterHint?.takeIf { it.isNotBlank() } ?: resolvedUrl

        withTimeoutOrNull(10_000L) {
            runCatching {
                val peek = http.peekStream(manifestCandidateUrl)
                when (peek.detectedFormat) {
                    StreamFormatSniffer.DetectedFormat.HLS_MANIFEST -> {
                        val (finalManifestUrl, text) = http.fetchText(manifestCandidateUrl)
                        val parsed = HlsManifestResolver.parse(finalManifestUrl, text)
                        if (parsed is HlsManifestResolver.ParsedPlaylist.Master && parsed.audioRenditions.isNotEmpty()) {
                            val bestVariant = HlsManifestResolver.selectBestVariant(parsed)
                            val renditions = HlsManifestResolver.audioRenditionsForVariant(parsed, bestVariant)
                            renditions.forEachIndexed { idx, r ->
                                val u = r.uri
                                if (u == null || seenUrls.add(u)) {
                                    discovered.add(
                                        DownloadableAudioTrack(
                                            key = r.trackKey,
                                            index = idx,
                                            label = r.name,
                                            lang = r.normalizedLang,
                                            directAudioUrl = u,
                                            headers = baseHeaders,
                                            codecHint = bestVariant?.codecs.orEmpty(),
                                            channels = r.channels,
                                            isDefault = r.isDefault
                                        )
                                    )
                                }
                            }
                        }
                    }

                    StreamFormatSniffer.DetectedFormat.DASH_MANIFEST -> {
                        val (finalManifestUrl, xml) = http.fetchText(manifestCandidateUrl)
                        val dash = DashManifestResolver.parse(finalManifestUrl, xml)
                        if (dash != null && dash.audioRepresentations.isNotEmpty()) {
                            val distinct = DashManifestResolver.groupDistinctAudioTracks(dash.audioRepresentations)
                            distinct.forEachIndexed { idx, rep ->
                                discovered.add(
                                    DownloadableAudioTrack(
                                        key = rep.trackKey,
                                        index = idx,
                                        label = rep.label,
                                        lang = rep.normalizedLang,
                                        directAudioUrl = null, // DASH manifest üzerinden indirilecek
                                        headers = baseHeaders,
                                        codecHint = rep.codecs.ifBlank { rep.mimeType },
                                        channels = rep.channels,
                                        isDefault = idx == 0
                                    )
                                )
                            }
                        }
                    }

                    StreamFormatSniffer.DetectedFormat.MKV,
                    StreamFormatSniffer.DetectedFormat.WEBM,
                    StreamFormatSniffer.DetectedFormat.MP4,
                    StreamFormatSniffer.DetectedFormat.M4A,
                    StreamFormatSniffer.DetectedFormat.FMP4_SEGMENT,
                    StreamFormatSniffer.DetectedFormat.MPEG_TS,
                    StreamFormatSniffer.DetectedFormat.FLV,
                    StreamFormatSniffer.DetectedFormat.AVI -> {
                        val probedAudios = MediaTrackRemuxer.probeRemoteContainerTracks(
                            httpClient = http,
                            url = peek.finalUrl
                        ).filter { it.isAudio }

                        probedAudios.forEachIndexed { idx, track ->
                            val normLang = track.normalizedLang
                            discovered.add(
                                DownloadableAudioTrack(
                                    key = "${normLang.ifBlank { "emb" }}_${track.audioOrderIndex}",
                                    index = track.audioOrderIndex,
                                    label = track.label,
                                    lang = normLang,
                                    directAudioUrl = null,
                                    headers = baseHeaders,
                                    codecHint = track.codecs.ifBlank { track.sampleMimeType },
                                    channels = track.channelCount,
                                    isDefault = idx == 0
                                )
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }

        // 2) Eklentinin bildirdiği harici ses parçalarını (`ExtractorLink.audioTracks`) ekle
        source.allExternalAudioTracks.forEachIndexed { idx, ext ->
            if (ext.url.isNotBlank() && seenUrls.add(ext.url)) {
                val normLang = ExternalAudioTrack.normalizeLangCode(
                    ext.safeLang.ifBlank { ExternalAudioTrack.inferLanguageFromText("${ext.safeLabel} ${ext.url}") }
                )
                discovered.add(
                    DownloadableAudioTrack(
                        key = "${normLang.ifBlank { "ext" }}_${discovered.size}",
                        index = discovered.size,
                        label = ext.safeLabel,
                        lang = normLang,
                        directAudioUrl = ext.url,
                        headers = ext.safeHeaders.ifEmpty { baseHeaders },
                        codecHint = ext.safeCodecs,
                        channels = ext.channels,
                        isDefault = idx == 0 && discovered.isEmpty()
                    )
                )
            }
        }

        if (discovered.isNotEmpty()) {
            return@withContext discovered
        }

        // 3) Gömülü (muxed) tekil veya çok dilli akış için varsayılan ses parçası
        val fallbackLang = source.providerLanguages.firstOrNull()
            ?.let { ExternalAudioTrack.normalizeLangCode(it) }
            .orEmpty()
            .ifBlank {
                when (source.providerAudioKind?.lowercase(Locale.ROOT)) {
                    "dub" -> "tr"
                    else -> ExternalAudioTrack.inferLanguageFromText("${source.name} ${source.title}")
                }
            }
        val fallbackLabel = when (source.providerAudioKind?.lowercase(Locale.ROOT)) {
            "dub" -> "Türkçe Dublaj"
            "dual" -> "Varsayılan Ses (Dual)"
            else -> ExternalAudioTrack.languageDisplayName(fallbackLang, turkishUi = true).orEmpty()
        }

        listOf(
            DownloadableAudioTrack(
                key = "${fallbackLang.ifBlank { "default" }}_0",
                index = 0,
                label = fallbackLabel,
                lang = fallbackLang,
                directAudioUrl = null,
                headers = baseHeaders,
                isDefault = true
            )
        )
    }

    suspend fun inspect(
        resolvedUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
        externalAudioTracks: List<ExternalAudioTrack> = emptyList(),
        addonName: String? = null,
        isCloudStream: Boolean = false
    ): List<DownloadableAudioTrack> {
        val syntheticSource = StreamSource(
            addonName = addonName ?: "Stream",
            name = addonName.orEmpty(),
            title = addonName.orEmpty(),
            url = resolvedUrl,
            infoHash = null,
            fileIndex = null,
            requestHeaders = requestHeaders,
            externalAudioUrl = externalAudioTracks.firstOrNull()?.url,
            externalAudioTracks = externalAudioTracks.map {
                com.kitsugi.animelist.core.player.ExternalAudioTrackInput(
                    url = it.url,
                    headers = it.headers,
                    name = it.label,
                    lang = it.lang
                )
            },
            isCS = isCloudStream
        )
        return inspect(resolvedUrl = resolvedUrl, source = syntheticSource)
    }
}
