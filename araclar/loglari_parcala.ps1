# =====================================================================
#  KITSUGI LOG BOLUCU  (Windows PowerShell)
#  Buyuk log dosyalarini 250 KB'lik parcalara ayirir; boylece yapay zeka
#  her parcayi ayri ayri, atlamadan okuyabilir.
#
#  KULLANIM:
#    1) Bu dosyaya sag tik -> "PowerShell ile calistir"
#    2) Loglarin bulundugu klasoru yaz (orn. C:\Users\Sen\Desktop\logs)
#    3) Olusan "parcalar" klasorunu Google Drive'daki klasore yukle
# =====================================================================

$hedef = Read-Host "Log dosyalarinin bulundugu klasor"
if (-not (Test-Path $hedef)) { Write-Host "Klasor yok: $hedef" -ForegroundColor Red; exit 1 }

$cikti = Join-Path $hedef "parcalar"
New-Item -ItemType Directory -Force -Path $cikti | Out-Null

$limit = 250000   # karakter (~250 KB)
$adet  = 0

Get-ChildItem $hedef -File | Where-Object {
    ($_.Extension -eq '.txt' -or $_.Extension -eq '.log') -and $_.Length -gt $limit
} | ForEach-Object {
    $icerik = [IO.File]::ReadAllText($_.FullName)
    $ad     = ($_.BaseName -replace '[^\w\-]', '_')
    $i = 0; $p = 0
    while ($p -lt $icerik.Length) {
        $len = [Math]::Min($limit, $icerik.Length - $p)
        $parca = $icerik.Substring($p, $len)
        $dosya = Join-Path $cikti ("{0}_part{1:d3}.txt" -f $ad, $i)
        [IO.File]::WriteAllText($dosya, $parca, (New-Object Text.UTF8Encoding($false)))
        $p += $len; $i++
    }
    $adet++
    Write-Host ("{0}  ->  {1} parca" -f $_.Name, $i) -ForegroundColor Green
}

if ($adet -eq 0) { Write-Host "250 KB'dan buyuk dosya yok." -ForegroundColor Yellow }
else { Write-Host ("`nBitti. {0} dosya bolundu. 'parcalar' klasorunu Drive'a yukle." -f $adet) -ForegroundColor Cyan }
