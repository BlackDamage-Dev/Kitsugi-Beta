package com.kitsugi.animelist.ui.screens.stream

/**
 * Akış seçici ekranının kapsam kuralı — tek yerde ve test edilebilir.
 *
 * KURAL: Kullanıcı bir CloudStream eklentisinin KENDİ sayfasından (keşfet / eklenti
 * araması → eklenti detayı → "İzle" ya da bölüm satırı) geldiyse arama yalnızca o
 * eklentide kalır. Stremio addon'ları ve diğer eklentiler ancak kullanıcı açıkça
 * "Tüm kaynaklarda ara" derse devreye girer.
 *
 * Tarihsel hata: eklentinin doğrudan URL'si boş döndüğünde ekran sessizce tüm
 * kaynakların ortak aramasına düşüyordu; kullanıcı "sadece bu eklenti" beklerken
 * listeye başka kaynaklar karışıyordu (bazen çalışıyor, bazen bozuluyor görünen
 * davranışın kaynağı buydu).
 */
internal object StreamSourceScope {

    /** true → yalnızca [pluginName] eklentisi aranmalı. */
    fun useSingleSource(pluginName: String?, allSourcesRequested: Boolean): Boolean =
        !pluginName.isNullOrBlank() && !allSourcesRequested

    /** Eklenti adından ekran gösterimi ("⚡ Ad") üretir; zaten önekliyse dokunmaz. */
    fun displayName(pluginName: String): String =
        if (pluginName.startsWith("⚡")) pluginName.trim()
        else "⚡ ${pluginName.trim()}"
}
