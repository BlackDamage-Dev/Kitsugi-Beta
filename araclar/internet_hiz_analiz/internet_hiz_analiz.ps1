# =====================================================================
#  KITSUGI - INTERNET HIZ VE DONANIM ANALIZ SISTEMI   (v2.0 / Windows 11)
# ---------------------------------------------------------------------
#  NE YAPAR?
#   1) Anakart modelini, ethernet yongasini ve kartin GERCEK link hizini
#      (100 Mbps / 1 Gbps / 2.5 Gbps ...) gosterir.
#   2) Kullanilan ag kablosunun (Cat5 / Cat5e / Cat6 ...) sinifini,
#      destekledigi maksimum hizi ve kablo sagligini gosterir.
#   3) Modem modelini otomatik tespit eder (etiketten yazarsan da olur),
#      portlarin ve Wi-Fi'nin SON hizlarini yazar.
#      Ornek: Huawei OptiXstar HG8245X6-10 icin kayit gomulu.
#   4) ISP paketi + modem + anakart + kablo zincirini cikarir, en zayif
#      halkayi (dar bogaz) soyler.
#   5) Istersen 10 saniyelik gercek hiz olcumu yapar.
#   6) Tum sonucu masaustune .txt raporu olarak kaydeder.
#
#  NOT: Windows PowerShell 5.1 ve PowerShell 7 ile calisir.
#       Dosya UTF-8 (BOM'lu) + CRLF olarak kayitlidir; turkce karakter
#       bozulmasi bu sayede onlenir.
# =====================================================================

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { $OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { (Get-Host).UI.RawUI.WindowTitle = 'KITSUGI - Internet Hiz ve Donanim Analizi' } catch {}

$script:Rapor = New-Object System.Collections.Generic.List[string]
$script:Uyari = 0
$script:Ozet  = New-Object System.Collections.Generic.List[string]

# ------------------------------------------------------------- yardimcilar
function Yaz([string]$Metin, [string]$Renk = 'Gray') {
    Write-Host $Metin -ForegroundColor $Renk
    $script:Rapor.Add($Metin)
}
function Baslik([string]$Metin) {
    Yaz ''
    Yaz ('=' * 78) 'Cyan'
    Yaz ('  ' + $Metin) 'Cyan'
    Yaz ('=' * 78) 'Cyan'
}
function AltBaslik([string]$Metin) {
    Yaz ''
    Yaz ('  -- ' + $Metin + ' ' + ('-' * [Math]::Max(0, 66 - $Metin.Length))) 'Yellow'
}
function Alan([string]$Etiket, [string]$Deger, [string]$Renk = 'White') {
    Yaz ('   {0,-27}: {1}' -f $Etiket, $Deger) $Renk
}
function Uyari([string]$Metin) { $script:Uyari++; Yaz ('   !! ' + $Metin) 'Red' }
function OzetEkle([string]$Metin) { $script:Ozet.Add($Metin); Yaz ('   ' + $Metin) 'Cyan' }

# Mbps -> okunabilir metin
function HizMetni([double]$Mbps) {
    if ($Mbps -le 0) { return 'bilinmiyor' }
    if ($Mbps -ge 1000) {
        $g = ($Mbps / 1000).ToString('0.##', [Globalization.CultureInfo]::InvariantCulture)
        return ('{0} Gbps ({1} Mbps)' -f $g, $Mbps)
    }
    return ('{0} Mbps' -f $Mbps)
}
function GercekHizNotu([double]$Mbps) {
    if ($Mbps -le 0) { return '' }
    $alt = $Mbps * 0.85; $ust = $Mbps * 0.95
    if ($alt -ge 1000) {
        return ('{0} - {1} Gbps gercek hiz beklenir' -f `
            ($alt / 1000).ToString('0.0#', [Globalization.CultureInfo]::InvariantCulture), `
            ($ust / 1000).ToString('0.0#', [Globalization.CultureInfo]::InvariantCulture))
    }
    return ('{0:0} - {1:0} Mbps gercek hiz beklenir' -f $alt, $ust)
}
function LinkMbps($Adaptor) {
    if (-not $Adaptor) { return 0 }
    $ham = $Adaptor.LinkSpeed
    if (-not $ham) { return 0 }
    $s = $ham.ToString().Trim()
    # "1 Gbps", "100 Mbps", "2.5 Gbps" gibi metinleri Mbps'e cevir
    if ($s -match '([0-9]+(?:[\.,][0-9]+)?)\s*(Gbps|Mbps|Kbps|bps)') {
        $sayi = [double]($matches[1].Replace(',', '.'))
        switch ($matches[2]) {
            'Gbps' { $sayi = $sayi * 1000 }
            'Mbps' { }
            'Kbps' { $sayi = $sayi / 1000 }
            'bps'  { $sayi = $sayi / 1000000 }
        }
        return [math]::Round($sayi, 2)
    }
    # sayisal deger gelirse: bit/sn kabul et
    $sadece = $s.Replace(',', '.')
    if ($sadece -match '^[0-9]+$') {
        $n = [double]$sadece
        if ($n -gt 100000) { return [math]::Round($n / 1000000, 2) }
        return $n
    }
    return 0
}

# Kartin destekledigi maksimum hiz (surucunun hiz seceneklerinden)
function KartKapasite($Adaptor) {
    if (-not $Adaptor) { return 0 }
    if ($Adaptor.MediaType -eq '802.11') { return 0 }
    $kap = 0
    try {
        $yet = Get-NetAdapterAdvancedProperty -Name $Adaptor.Name -ErrorAction Stop |
               Where-Object { $_.RegistryKeyword -eq '*SpeedDuplex' }
        if ($yet) {
            $d = ($yet.ValidDisplayValues -join '|')
            if     ($d -match '100 Gbps|100G') { $kap = 100000 }
            elseif ($d -match '40 Gbps|40G')   { $kap = 40000 }
            elseif ($d -match '25 Gbps|25G')   { $kap = 25000 }
            elseif ($d -match '10 Gbps|10G')   { $kap = 10000 }
            elseif ($d -match '5 Gbps|5G')     { $kap = 5000 }
            elseif ($d -match '2\.5 Gbps|2\.5G|2500') { $kap = 2500 }
            elseif ($d -match '1 Gbps|1G|1000') { $kap = 1000 }
            elseif ($d -match '100 Mbps')      { $kap = 100 }
            else                               { $kap = 10 }
        }
    } catch {}
    if (-not $kap) {
        $ad = $Adaptor.InterfaceDescription.ToUpperInvariant()
        if     ($ad -match 'I225|I226|RTL8125|RTL8156|AQC|2\.5G') { $kap = 2500 }
        elseif ($ad -match 'I210|I211|I219|I218|RTL8168|RTL8111|RTL8169|GBE|GIGABIT') { $kap = 1000 }
        elseif ($ad -match 'FAST|10/100|RTL810') { $kap = 100 }
    }
    return $kap
}

# ---------------------------------------------------- MODEM VERITABANI
# Anahtar = modelin normalize edilmis hali (harf/rakam disi karakterler atilir)
$ModemDb = @{
    'HG8245X610' = @{
        Marka = 'Huawei'; Model = 'OptiXstar HG8245X6-10 (GPON ONT + Router)'
        PonTipi = 'GPON (ITU-T G.984, Class B+)'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x LAN (RJ45) - hepsi 10/100/1000 Mbps (Gigabit, auto-sensing)'
        LanMax = 1000; WanMax = 1000; Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant; 2x2 MIMO 2.4 GHz + 2x2 MIMO 5 GHz, WPA3, EasyMesh'
        Diger = '2 x POTS (VoIP), 1 x USB 2.0, 1 x SC/APC fiber girisi, WPS'
        Not = 'Tum LAN portlari gigabit; port basina ~940 Mbps gercek hiz alinir.'
    }
    'HG8245X6' = @{
        Marka = 'Huawei'; Model = 'OptiXstar HG8245X6 (GPON ONT + Router)'
        PonTipi = 'GPON (Class B+)'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x LAN (RJ45) - Gigabit'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant'
        Diger = '2 x POTS, 1 x USB, SC/APC fiber girisi'
        Not = 'Tum LAN portlari gigabit.'
    }
    'HG8245H' = @{
        Marka = 'Huawei'; Model = 'EchoLife HG8245H (GPON ONT)'
        PonTipi = 'GPON (Class B+)'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x LAN (RJ45) - Gigabit'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 300; Wifi5Max = 0
        WifiStd = 'Wi-Fi 4 (802.11 b/g/n) - SADECE 2.4 GHz, 2x2 MIMO'
        Diger = '2 x POTS, 1 x USB 2.0'
        Not = '5 GHz yok. Wi-Fi ile 1000 Mbps kullanilamaz, kablo sart.'
    }
    'HG8245' = @{
        Marka = 'Huawei'; Model = 'EchoLife HG8245 (GPON ONT)'
        PonTipi = 'GPON (Class B+)'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x LAN (RJ45) - 10/100/1000 Mbps'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 300; Wifi5Max = 0
        WifiStd = 'Wi-Fi 4 (802.11 b/g/n) - 2.4 GHz'
        Diger = '2 x POTS, 1 x USB'
        Not = 'Yonlendirme kapasitesi ~900 Mbps.'
    }
    'EG8145X6' = @{
        Marka = 'Huawei'; Model = 'OptiXstar EG8145X6 (GPON + Wi-Fi 6 ONT)'
        PonTipi = 'GPON'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x GE LAN (Gigabit)'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant, 2x2 MIMO'
        Diger = '1 x POTS, 1 x USB 2.0'
        Not = 'Gigabit LAN + Wi-Fi 6.'
    }
    'HG8145X6' = @{
        Marka = 'Huawei'; Model = 'OptiXstar HG8145X6 (GPON + Wi-Fi 6 ONT)'
        PonTipi = 'GPON'; PonDown = 2488; PonUp = 1244
        LanPortlar = '4 x GE LAN (Gigabit)'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant, 2x2 MIMO'
        Diger = '1 x POTS, 1 x USB 2.0'
        Not = 'Gigabit LAN + Wi-Fi 6.'
    }
    'HN8145XR' = @{
        Marka = 'Huawei'; Model = 'OptiXstar HN8145XR (10G PON + Wi-Fi 6)'
        PonTipi = '10G PON (XG-PON / XGS-PON)'; PonDown = 10000; PonUp = 2500
        LanPortlar = '1 x 2.5GE + 4 x GE LAN'; LanMax = 2500; WanMax = 2500
        Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant, 2x2 MIMO'
        Diger = '1 x POTS, 2 x USB'
        Not = '2.5 Gbps portu var; 1000 Mbps ustu paketler icin uygundur.'
    }
    'V864' = @{
        Marka = 'Huawei'; Model = 'OptiXstar V864 (10G PON + Wi-Fi 6)'
        PonTipi = '10G PON'; PonDown = 10000; PonUp = 10000
        LanPortlar = '1 x 2.5GE + 4 x GE LAN'; LanMax = 2500; WanMax = 2500
        Wifi2Max = 1147; Wifi5Max = 6000
        WifiStd = 'Wi-Fi 6 (4x4 + 4x4 MIMO)'
        Diger = '2 x POTS'
        Not = 'Ust seviye cihaz, 2.5 Gbps port destekli.'
    }
    'DN8245X610' = @{
        Marka = 'Huawei'; Model = 'OptiXstar DN8245X6-10 (SuperVector / VDSL Router)'
        PonTipi = 'Yok - bakir (DSL) hat cihazi'; PonDown = 0; PonUp = 0
        LanPortlar = '4 x GE LAN (10/100/1000 Mbps)'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 574; Wifi5Max = 2402
        WifiStd = 'Wi-Fi 6 (802.11ax) cift bant, 2x2 MIMO'
        Diger = '2 x FXS, 1 x USB 2.0, DSL/GE WAN'
        Not = 'Fiber degil; VDSL hattin verebildigi hiz gecerlidir.'
    }
    'HG658V2' = @{
        Marka = 'Huawei'; Model = 'EchoLife HG658 V2 (VDSL/ADSL Router)'
        PonTipi = 'Yok - bakir (DSL) hat cihazi'; PonDown = 0; PonUp = 0
        LanPortlar = '4 x LAN (RJ45) - 10/100 Mbps'; LanMax = 100; WanMax = 100
        Wifi2Max = 300; Wifi5Max = 0
        WifiStd = 'Wi-Fi 4 (802.11 b/g/n) - 2.4 GHz'
        Diger = '1 x USB 2.0'
        Not = 'LAN portlari 100 Mbps! 100 Mbps ustu paketlerde ciddi dar bogaz.'
    }
    'VR600V3' = @{
        Marka = 'TP-Link'; Model = 'Archer VR600 v3 (VDSL/ADSL Router)'
        PonTipi = 'Yok - bakir (DSL) hat cihazi'; PonDown = 0; PonUp = 0
        LanPortlar = '4 x GE LAN (Gigabit)'; LanMax = 1000; WanMax = 1000
        Wifi2Max = 300; Wifi5Max = 1300
        WifiStd = 'Wi-Fi 5 (802.11ac): 2.4 GHz 300 + 5 GHz 1300 Mbps'
        Diger = '2 x USB'
        Not = 'Wi-Fi 5; Wi-Fi 6 cihazlarda daha yuksek hiz alinabilir.'
    }
}

function ModelNormalize([string]$S) {
    if (-not $S) { return '' }
    return ($S.ToUpperInvariant() -replace '[^A-Z0-9]', '')
}
function ModemBul([string]$Metin) {
    if (-not $Metin) { return $null }
    $norm = ModelNormalize $Metin
    foreach ($k in ($ModemDb.Keys | Sort-Object Length -Descending)) {
        if ($norm.Contains($k)) { return @{ Anahtar = $k; Veri = $ModemDb[$k] } }
    }
    return $null
}

# ------------------------------------------------------- KABLO SINIFI
function KabloSinifi([double]$Mbps) {
    if ($Mbps -le 0)      { return 'Bilinmiyor' }
    if ($Mbps -ge 40000)  { return 'Cat8 (40 Gbps)' }
    if ($Mbps -ge 10000)  { return 'Cat6a / Cat7 / Cat8 (10 Gbps)' }
    if ($Mbps -ge 2500)   { return 'Cat5e kisa mesafe / Cat6 / Cat6a (2.5 Gbps)' }
    if ($Mbps -ge 1000)   { return 'Cat5e / Cat6 (1 Gbps)' }
    if ($Mbps -ge 100)    { return 'Cat5 (100 Mbps)' }
    return 'Eski veya hasarli kablo (<100 Mbps)'
}

# =====================================================================
Baslik 'KITSUGI - INTERNET HIZ VE DONANIM ANALIZI'
Yaz ('   Tarih : ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) 'DarkGray'
Yaz ('   PC    : ' + $env:COMPUTERNAME + ' / ' + $env:USERNAME) 'DarkGray'
Yaz '   AMAC  : Anakart + kablo + modem + internet paketinin SON hizlarini gormek.' 'DarkGray'

# =====================================================================
# 1) ANAKART
# =====================================================================
Baslik '1) ANAKART / ISLEMCI (PC TARAFI)'
$cs  = Get-CimInstance Win32_ComputerSystem
$bb  = Get-CimInstance Win32_BaseBoard
$cpu = Get-CimInstance Win32_Processor
$anakartAdi = (($bb.Manufacturer) + ' ' + ($bb.Product)).Trim()
Alan 'Bilgisayar' ($cs.Manufacturer + ' ' + $cs.Model)
Alan 'Anakart Ureticisi' $bb.Manufacturer
Alan 'Anakart Modeli' $bb.Product 'Green'
Alan 'Anakart Versiyon / Seri' (([string]$bb.Version) + ' / ' + ([string]$bb.SerialNumber))
Alan 'Islemci' ($cpu.Name -replace '\s+', ' ')
Alan 'RAM' (('{0:0.#} GB' -f ($cs.TotalPhysicalMemory / 1GB)))
Yaz '   Not: Anakartin hiz sinirini uzerindeki ethernet yongasi belirler (bolum 2).' 'DarkGray'

# =====================================================================
# 2) ETHERNET KARTI
# =====================================================================
Baslik '2) ETHERNET KARTI (ADAPTOR) DESTEKLERI'
$tumAdaptor = @(Get-NetAdapter -Physical)
if ($tumAdaptor.Count -eq 0) {
    Uyari 'Ag adaptoru bulunamadi. (Get-NetAdapter bos dondu)'
}
foreach ($a in $tumAdaptor) {
    AltBaslik ('Adaptor: ' + $a.Name + '   [' + $a.InterfaceDescription + ']')
    Alan 'Durum' ($a.Status + '  /  ' + $a.MediaType) $(if ($a.Status -eq 'Up') { 'Green' } else { 'DarkGray' })
    Alan 'MAC Adresi' $a.MacAddress 'DarkGray'

    $link = LinkMbps $a
    if ($a.MediaType -eq '802.11') {
        Alan 'Kart Tipi' 'Wi-Fi (kablosuz)'
        Alan 'Anlik Wi-Fi Link Hizi' (HizMetni $link) 'Green'
        Alan 'Not' 'Wi-Fi hizi sinyal/mesafeye gore degisir; kablo testi daha guvenilir.' 'DarkGray'
        continue
    }

    $kap = KartKapasite $a
    if ($kap -gt 0) { Alan 'Kartin Destekledigi MAX' (HizMetni $kap) 'Green' }
    else            { Alan 'Kartin Destekledigi MAX' 'Tespit edilemedi (surucu?)' 'Yellow' }

    if ($a.Status -eq 'Up') {
        $renk = 'Green'
        if ($link -le 100 -and $link -gt 0) {
            $renk = 'Red'
            Uyari ('Link hizi sadece ' + (HizMetni $link) + ' - gigabit bekleniyorsa kablo veya port sorunlu.')
        }
        Alan 'GERCEK BAGLANTI HIZI' ((HizMetni $link) + '   (kart <-> modem anlasmasi)') $renk
        if ($kap -gt 0 -and $link -gt 0 -and $link -lt $kap) {
            Yaz ('   >> Kart ' + (HizMetni $kap) + ' yapabiliyor ama ' + (HizMetni $link) + ' hizda anlasmis: kablo, port ya da modem sinirliyor.') 'Yellow'
        }
        $script:Ozet.Add(('Ethernet karti : ' + $a.Name + ' -> kart max ' + (HizMetni $kap) + ' / gercek link ' + (HizMetni $link)))
    } else {
        Alan 'GERCEK BAGLANTI HIZI' 'Bagli degil (kablo takili degil)' 'DarkGray'
    }
}

# =====================================================================
# 3) AG KABLOSU
# =====================================================================
Baslik '3) AG KABLOSU (LAN KABLO KALITESI VE SAGLIGI)'
$fiziksel = @(Get-NetAdapter -Physical | Where-Object { $_.Status -eq 'Up' -and $_.MediaType -ne '802.11' })
$kabloLink = 0
if ($fiziksel.Count -eq 0) {
    Uyari 'Aktif kablolu baglanti yok. Kablo analizi icin PC yi modem/router a CAT kablo ile bagla.'
} else {
    foreach ($a in $fiziksel) {
        AltBaslik ('Kablo uzerinden bagli: ' + $a.Name)
        $link = LinkMbps $a
        $kabloLink = $link
        Alan 'Kabloyla Gelen Anlasma Hizi' (HizMetni $link) 'Green'
        Alan 'Bu Hiza Uygun Kablo Sinifi' (KabloSinifi $link) 'Green'
        $kh = Get-NetAdapterHardwareInfo -Name $a.Name
        if ($kh -and $kh.CableLength -and [int]$kh.CableLength -gt 0) {
            Alan 'Kablo Uzunlugu (surucu tahmini)' (([int]$kh.CableLength).ToString() + ' metre civari')
        } elseif ($kh) {
            Alan 'Kablo Uzunlugu' 'Surucu bildirmiyor (cogu kartta normal)' 'DarkGray'
        }
        Alan 'Kritik Kural' '1 Gbps icin 8 cekirdegin TUMU saglam olmali (4 cekirdek = max 100 Mbps)' 'DarkGray'
        Alan 'Kablonun Kendi Kapasitesi' 'Cat5e: 1 Gbps | Cat6: 1 Gbps (10G 55m) | Cat6a: 10 Gbps | Cat7/8: 10G+' 'DarkGray'

        AltBaslik 'Kablo ve Baglanti Saglik Testi'
        $ipcfg = Get-NetIPConfiguration -InterfaceIndex $a.ifIndex
        $pcIp = $null; $gw = $null
        if ($ipcfg) {
            if ($ipcfg.IPv4Address) { $pcIp = $ipcfg.IPv4Address[0].IPAddress }
            if ($ipcfg.IPv4DefaultGateway) { $gw = $ipcfg.IPv4DefaultGateway.NextHop }
        }
        Alan 'PC IP' $pcIp
        Alan 'Ag Gecidi (Modem)' $gw

        $ist = Get-NetAdapterStatistics -Name $a.Name
        if ($ist) {
            Alan 'Gonderilen / Alinan Paket' (('{0:N0} / {1:N0}' -f $ist.SentUnicastPackets, $ist.ReceivedUnicastPackets))
            $hatali = 0
            foreach ($p in @('OutboundPacketsWithErrors', 'ReceivedPacketErrors', 'ReceivedDiscardedPackets')) {
                if ($ist.$p) { $hatali += $ist.$p }
            }
            if ($hatali -gt 0) {
                Uyari ('Hatali/atilan paket sayisi: ' + $hatali + ' -> kablo veya portta temassizlik olabilir.')
            } else {
                Alan 'Paket Hatasi' '0 (temiz - kablo saglikli gorunuyor)' 'Green'
            }
        }
        if ($pcIp -and $pcIp -match '^169\.254\.') {
            Uyari 'PC kendine otomatik IP vermis (169.254.x.x): modem DHCP vermiyor ya da kablo/port bozuk.'
        }
        if ($gw) {
            $ok80  = Test-NetConnection -ComputerName $gw -Port 80  -InformationLevel Quiet -WarningAction SilentlyContinue
            $ok443 = $false
            if (-not $ok80) { $ok443 = Test-NetConnection -ComputerName $gw -Port 443 -InformationLevel Quiet -WarningAction SilentlyContinue }
            if ($ok80 -or $ok443) { Alan 'Modem Arayuzu (80/443)' 'Erisildi - kablo ve port calisiyor' 'Green' }
            else { Alan 'Modem Arayuzu (80/443)' 'Yanit yok (modem kapali / farkli port / filtre)' 'Yellow' }
        }
    }
    Yaz ''
    Yaz '   Kablo ipuclari:' 'DarkGray'
    Yaz '     - En yaygin sebep: duvar prizi, ek yeri, ezilmis veya eski (Cat5) kablo.' 'DarkGray'
    Yaz '     - Kabloyu LAN1..LAN4 arasinda baska porta takip tekrar dene.' 'DarkGray'
    Yaz '     - Wi-Fi kapatip sadece kablo ile test et.' 'DarkGray'
    Yaz '     - 100 Mbps takili kaliyorsa kabloyu yenile (Cat6 onerilir).' 'DarkGray'
    if ($kabloLink -gt 0) { $script:Ozet.Add(('Ag kablosu    : ' + (HizMetni $kabloLink) + ' anlasma -> ' + (KabloSinifi $kabloLink))) }
}

# =====================================================================
# 4) MODEM
# =====================================================================
Baslik '4) MODEM / FIBER TERMINALI (ONT)'
$gw = $null
try {
    $cfg = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null } | Select-Object -First 1
    if ($cfg) { $gw = $cfg.IPv4DefaultGateway.NextHop }
} catch {}
$mac = $null
if ($gw) {
    Alan 'Modem IP (Ag Gecidi)' $gw 'Green'
    $sureler = @()
    try {
        $pinger = New-Object System.Net.NetworkInformation.Ping
        for ($i = 0; $i -lt 3; $i++) {
            try {
                $rep = $pinger.Send($gw, 1500)
                if ($rep -and $rep.Status -eq 'Success') { $sureler += $rep.RoundtripTime }
            } catch {}
        }
    } catch {}
    if ($sureler.Count -gt 0) {
        $ort = ($sureler | Measure-Object -Average).Average
        Alan 'Ping (modem)' ('Basarili - ortalama ' + [math]::Round($ort, 1) + ' ms (LAN tarafi iyi)') 'Green'
    } else {
        Alan 'Ping (modem)' 'Cevap yok (modem ping engelli olabilir - sorun degil)' 'Yellow'
    }
    try { $mac = (Get-NetNeighbor -IPAddress $gw -ErrorAction Stop).LinkLayerAddress } catch {}
    if (-not $mac) {
        $n = arp -a | Select-String ([regex]::Escape($gw))
        if ($n -and $n.ToString() -match '([0-9a-fA-F]{2}[-:]){5}[0-9a-fA-F]{2}') { $mac = $matches[0] }
    }
    Alan 'Modem MAC' $mac
    if ($mac) {
        $hex = ($mac -replace '[^0-9A-Fa-f]', '').ToUpperInvariant()
        if ($hex.Length -ge 6) {
            $oui = $hex.Substring(0, 6)
            $markaTahmin = switch -Regex ($oui) {
                '^(00259E|002598|00E0FC|5C7D5E|48435A|20F3A3|04BD70|0022A1|F4C714|884CEE|9C28F7)' { 'Huawei'; break }
                '^(F8D111|50C7BF|001D0F|1C61B4|34E894|001349|AC84C6)' { 'TP-Link'; break }
                '^(001CF0|0026F2|E0B9A5|001E58|F0615C|F07D68)' { 'D-Link'; break }
                '^(B0487A|0026B8|8C3BAD|001CB3|58EF68|EC1D7F)' { 'ZTE'; break }
                '^(9C7514|001A2B|000E5C|6C5AB0|889471)' { 'Nokia / Alcatel'; break }
                default { 'OUI tablosunda yok' }
            }
            Alan 'Uretici (MAC/OUI tahmini)' $markaTahmin 'Green'
        }
    }
    Yaz '   Kesin model bilgisi cihazin ALTINDAKI/ARKASINDAKI etikette yazar.' 'DarkGray'
} else {
    Uyari 'Modem IP (ag gecidi) bulunamadi. VPN kapali mi ve kablo takili mi kontrol et.'
}

# --- model tespiti: yerel kayitlar -> modem web arayuzu -> kullaniciya sor
$bulunan = $null
try {
    $pnp = @(Get-PnpDevice -Class Net -ErrorAction Stop | Where-Object { $_.FriendlyName -match 'Huawei|HG8|EG8|HG6|OptiXstar|VDSL|GPON|ONT' })
    foreach ($p in $pnp) {
        $b = ModemBul $p.FriendlyName
        if ($b) { $bulunan = $b; break }
    }
} catch {}
if (-not $bulunan -and $gw) {
    try {
        $r = Invoke-WebRequest -Uri ("http://$gw/") -UseBasicParsing -TimeoutSec 4 -ErrorAction Stop
        $html = [string]$r.Content
        $bulunan = ModemBul $html
        if (-not $bulunan -and $html -match '(?i)(HG\d{4}[A-Z0-9\-]*|EG\d{4}[A-Z0-9\-]*|DN\d{4}[A-Z0-9\-]*|HN\d{4}[A-Z0-9\-]*|OptiXstar[^<\s"]*)') {
            $bulunan = ModemBul $matches[1]
        }
        if ($r.Headers['Server']) { Alan 'Modem Web Sunucusu' ([string]$r.Headers['Server']) 'DarkGray' }
    } catch {}
}
if (-not $bulunan) {
    Yaz ''
    Yaz '   Modem modelini otomatik tespit edemedim.' 'Yellow'
    Write-Host '   Etiketteki modeli yaz (ornek: HG8245X6-10) veya bos gec: ' -NoNewline -ForegroundColor Yellow
    $cevap = Read-Host
    if ($cevap) { $bulunan = ModemBul $cevap }
}

if ($bulunan) {
    $d = $bulunan.Veri
    AltBaslik 'TANINAN MODEL'
    Alan 'Marka' $d.Marka 'Green'
    Alan 'Model' $d.Model 'Green'
    Alan 'PON / Hat Tipi' $d.PonTipi

    AltBaslik 'KABLOLU (LAN) SON HIZ DESTEGI'
    Alan 'LAN Portlari' $d.LanPortlar
    Alan 'Port Basina MAX Hiz' (HizMetni $d.LanMax) 'Green'
    if ($d.LanMax -ge 1000) {
        Alan 'Gercek (kullanilabilir)' (GercekHizNotu $d.LanMax) 'DarkGray'
    } elseif ($d.LanMax -gt 0) {
        Uyari ($d.LanMax.ToString() + ' Mbps port: bu modemle 100 Mbps ustu paket kullanilamaz.')
    }
    if ($d.PonDown -gt 0) {
        Alan 'Fiber (PON) Hatti' ((HizMetni $d.PonDown) + ' indirme / ' + (HizMetni $d.PonUp) + ' yukleme (teorik)')
    }

    AltBaslik 'KABLOSUZ (Wi-Fi) SON HIZ DESTEGI'
    Alan 'Wi-Fi Teknolojisi' $d.WifiStd
    if ($d.Wifi2Max -gt 0) { Alan '2.4 GHz MAX' (HizMetni $d.Wifi2Max) 'Green' }
    if ($d.Wifi5Max -gt 0) { Alan '5 GHz MAX' (HizMetni $d.Wifi5Max) 'Green' }
    else                   { Alan '5 GHz' 'YOK - sadece 2.4 GHz' 'Red' }
    Alan 'Wi-Fi Gercek Notu' 'Kablosuzda teorik hizin ~%60-75 i alinir; 1000 Mbps icin 5 GHz + Wi-Fi 6 sart.' 'DarkGray'

    if ($d.Diger) { Alan 'Diger Portlar' $d.Diger }
    if ($d.Not)   { Yaz ('   >> ' + $d.Not) 'Cyan' }

    AltBaslik '1000 Mbps (1 Gbps) DESTEGI'
    if ($d.LanMax -ge 1000) {
        Alan 'Kablolu 1000 Mbps' 'DESTEKLER (LAN portu gigabit)' 'Green'
    } else {
        Alan 'Kablolu 1000 Mbps' ('DESTEKLEMEZ - LAN siniri ' + (HizMetni $d.LanMax)) 'Red'
    }
    if ($d.Wifi5Max -ge 1000) {
        Alan 'Wi-Fi ile 1000 Mbps' ('Kismen - 5 GHz teorik ' + (HizMetni $d.Wifi5Max) + ', gercekte 600-950 Mbps') 'Green'
    } else {
        Alan 'Wi-Fi ile 1000 Mbps' ('DESTEKLEMEZ - Wi-Fi siniri ' + (HizMetni ([Math]::Max($d.Wifi2Max, $d.Wifi5Max)))) 'Red'
    }
    $script:Ozet.Add(('Modem         : ' + $d.Model + ' -> LAN max ' + (HizMetni $d.LanMax) + ', Wi-Fi 5 GHz max ' + (HizMetni $d.Wifi5Max)))
} else {
    AltBaslik 'MODEL BILINMIYOR'
    Yaz '   Etikette su satirlara bak:' 'Yellow'
    Yaz '     - Marka ve model (ornek: Huawei OptiXstar HG8245X6-10)' 'DarkGray'
    Yaz '     - LAN1..LAN4 (hepsi gigabit mi? 100 Mbps portlar buyuk dar bogaz)' 'DarkGray'
    Yaz '     - Wi-Fi 5 mi Wi-Fi 6 mi yaziyor' 'DarkGray'
    Yaz '   Genel kural: 2020 sonrasi fiber modemlerin cogu 4 x Gigabit LAN + Wi-Fi 6 dir.' 'DarkGray'
}

# =====================================================================
# 5) ISP PAKETI ve ZINCIR ANALIZI
# =====================================================================
Baslik '5) INTERNET PAKETI ve DAR BOGAZ (ZINCIR) ANALIZI'
Yaz '   ISP paket hizini gir (ornek: 100, 200, 500, 1000 veya 1 Gbps).' 'DarkGray'
Yaz '   Bos birakirsan sadece donanim sinirlari gosterilir.' 'DarkGray'
Write-Host '   Paket hizi (Mbps): ' -NoNewline -ForegroundColor Yellow
$paketGiris = Read-Host
$paket = 0
if ($paketGiris) {
    $temiz = $paketGiris -replace '[^0-9\.,]', ''
    $temiz = $temiz.Replace(',', '.')
    if ($temiz) {
        $paket = [double]$temiz
        if ($paketGiris -match '(?i)gb') { $paket = $paket * 1000 }
        elseif ($paket -gt 0 -and $paket -le 2 -and $paketGiris -notmatch '(?i)mb') { $paket = $paket * 1000 }
    }
}

$kartMax = 0
$ad = $null
if ($fiziksel.Count -gt 0) { $ad = $fiziksel[0] }
if ($ad) { $kartMax = KartKapasite $ad }
$modemLanMax = 0; $modemWifiMax = 0
if ($bulunan) {
    $modemLanMax  = $bulunan.Veri.LanMax
    $modemWifiMax = [Math]::Max($bulunan.Veri.Wifi2Max, $bulunan.Veri.Wifi5Max)
}

AltBaslik 'ZINCIR TABLOSU (en zayif halka = alacagin gercek hiz)'
Yaz ('   {0,-32} {1,18}   {2}' -f 'Halka', 'Son Hiz', 'Durum') 'Cyan'
Yaz ('   ' + ('-' * 70)) 'DarkGray'
$satirlar = @()
if ($paket -gt 0)       { $satirlar += @{ Ad = '1. ISP paketi (fiber hat)';   Mbps = $paket } }
if ($modemLanMax -gt 0) { $satirlar += @{ Ad = '2. Modem LAN portu';          Mbps = $modemLanMax } }
if ($kartMax -gt 0)     { $satirlar += @{ Ad = '3. Anakart ethernet karti';   Mbps = $kartMax } }
if ($kabloLink -gt 0)   { $satirlar += @{ Ad = '4. Kablo / gercek link';      Mbps = $kabloLink } }

$enZayif = $null
foreach ($s in $satirlar) {
    $durum = 'OK'; $renk = 'Green'
    if ($paket -gt 0 -and $s.Mbps -lt $paket) { $durum = 'DAR BOGAZ'; $renk = 'Red' }
    elseif ($paket -gt 0 -and $s.Mbps -le ($paket * 1.05)) { $durum = 'sinirda'; $renk = 'Yellow' }
    if (-not $enZayif -or $s.Mbps -lt $enZayif.Mbps) { $enZayif = $s }
    Yaz ('   {0,-32} {1,18}   {2}' -f $s.Ad, (HizMetni $s.Mbps), $durum) $renk
}

if ($enZayif) {
    Yaz ''
    if ($paket -gt 0 -and $enZayif.Mbps -lt $paket) {
        Uyari ('Zincirin en zayif halkasi: ' + $enZayif.Ad + ' -> ' + (HizMetni $enZayif.Mbps))
        Yaz ('   ' + (HizMetni $paket) + ' paketten pratikte ' + (HizMetni $enZayif.Mbps) + ' alabilirsin.') 'Yellow'
    } elseif ($paket -gt 0) {
        Yaz ('   Zincir ' + (HizMetni $paket) + ' paketi tasiyor. Kablo ile ' + (GercekHizNotu $paket) + '.') 'Green'
    }
    $script:Ozet.Add(('Zincir siniri : ' + (HizMetni $enZayif.Mbps) + '  (' + $enZayif.Ad + ')'))
}

if ($modemWifiMax -gt 0) {
    AltBaslik 'WI-FI TARAFI (kablo yerine kablosuz kullanirsan)'
    if ($bulunan) {
        if ($bulunan.Veri.Wifi2Max -gt 0) { Alan '2.4 GHz MAX' (HizMetni $bulunan.Veri.Wifi2Max) 'Green' }
        if ($bulunan.Veri.Wifi5Max -gt 0) { Alan '5 GHz MAX' (HizMetni $bulunan.Veri.Wifi5Max) 'Green' }
        Yaz ('   >> Wi-Fi ile beklenen gercek hiz: ' + (GercekHizNotu $bulunan.Veri.Wifi5Max)) 'Cyan'
        if ($paket -ge 500 -and $bulunan.Veri.Wifi5Max -lt $paket) {
            Uyari 'Paket hizin Wi-Fi kapasitesinden yuksek: tam hiz icin kablo kullan.'
        }
    }
}

if ($paket -ge 1000) {
    AltBaslik '1000 Mbps (1 Gbps) KONTROL LISTESI'
    Yaz '   [ ] Kablo en az Cat5e/Cat6 ve 8 cekirdegi saglam' 'White'
    Yaz '   [ ] Modem portu gigabit (HG8245X6-10: 4 portun hepsi gigabit)' 'White'
    Yaz '   [ ] Ethernet kartinin link hizi 1000 Mbps gorunuyor' 'White'
    Yaz '   [ ] Test kablo ile yapiliyor (Wi-Fi ile 1 Gbps beklenmez)' 'White'
    Yaz '   [ ] Test sirasinda baska cihaz indirme yapmiyor' 'White'
    Yaz '   [ ] Modemde QoS / hiz limidi / ebeveyn kontrolu kapali' 'White'
    if ($bulunan -and $bulunan.Veri.LanMax -ge 1000) {
        Yaz ('   >> Bu modem 1000 Mbps paketi KABLO ile destekliyor (port basi ~940 Mbps).') 'Green'
    }
}

# =====================================================================
# 6) GERCEK HIZ OLCUMU
# =====================================================================
Baslik '6) GERCEK HIZ OLCUMU'
Yaz '   Yontem 1: Bu ekranda 10 saniyelik yerel olcum (o anki gercek trafik).' 'DarkGray'
Yaz '   Yontem 2: Tarayicidan hiztesti.com veya speedtest.net (en dogru sonuc, KABLO ile).' 'DarkGray'
$olc = $false
if ($ad) {
    Write-Host '   Yerel 10 saniyelik olcum yapilsin mi? (E/H): ' -NoNewline -ForegroundColor Yellow
    $cev = Read-Host
    if ($cev -match '^[EeYy]') { $olc = $true }
} else {
    Uyari 'Aktif kablolu adaptor yok, yerel olcum atlandi.'
}
if ($olc) {
    Yaz '   Olculuyor... (10 sn) interneti kullanma.' 'Yellow'
    $b1 = Get-NetAdapterStatistics -Name $ad.Name
    Start-Sleep -Seconds 10
    $b2 = Get-NetAdapterStatistics -Name $ad.Name
    $rx = [math]::Round((($b2.ReceivedBytes - $b1.ReceivedBytes) * 8) / 10 / 1MB, 2)
    $tx = [math]::Round((($b2.SentBytes - $b1.SentBytes) * 8) / 10 / 1MB, 2)
    if ($rx -lt 0) { $rx = 0 }
    if ($tx -lt 0) { $tx = 0 }
    Alan 'Anlik Indirme' (HizMetni $rx) 'Green'
    Alan 'Anlik Yukleme' (HizMetni $tx) 'Green'
    Yaz '   Not: Bu deger o an ne indiriyorsan onu gosterir; bos bilgisayarda dusuk cikmasi normaldir.' 'DarkGray'
}

# =====================================================================
# 7) OZET + RAPOR
# =====================================================================
Baslik '7) OZET (TEK BAKISTA)'
if ($script:Ozet.Count -eq 0) { Yaz '   Ozet olusturulamadi.' 'Yellow' }
foreach ($o in $script:Ozet) { Yaz ('   ' + $o) 'Cyan' }
Yaz ''
if ($paket -gt 0 -and $enZayif) {
    Yaz ('   Hedef paket      : ' + (HizMetni $paket)) 'White'
    Yaz ('   Beklenen sonuc   : ' + (HizMetni ([Math]::Min($paket, $enZayif.Mbps))) + '  (kablo ile)') 'Green'
}
Yaz ('   Uyari sayisi     : ' + $script:Uyari) $(if ($script:Uyari -gt 0) { 'Red' } else { 'Green' })

$masaustu = [Environment]::GetFolderPath('Desktop')
if (-not $masaustu) { $masaustu = $env:USERPROFILE }
$raporYolu = Join-Path $masaustu ('internet_hiz_raporu_{0}.txt' -f (Get-Date -Format 'yyyy-MM-dd_HHmm'))
try {
    $script:Rapor | Out-File -FilePath $raporYolu -Encoding UTF8
    Yaz ''
    Yaz ('   Rapor kaydedildi: ' + $raporYolu) 'Green'
    Write-Host '   Raporu simdi acmak ister misin? (E/H): ' -NoNewline -ForegroundColor Yellow
    $ac = Read-Host
    if ($ac -match '^[EeYy]') { Start-Process notepad.exe $raporYolu }
} catch {
    Uyari ('Rapor kaydedilemedi: ' + $_.Exception.Message)
}

Yaz ''
Yaz '   Bitirmek icin bir tusa bas...' 'DarkGray'
try { $null = [Console]::ReadKey($true) } catch {}
