@echo off
chcp 65001 >nul
title KITSUGI - Internet Hiz ve Donanim Analizi (Windows 11)
color 0B
mode con: cols=100 lines=42

echo ==============================================================================
echo            KITSUGI - INTERNET HIZ VE DONANIM ANALIZ SISTEMI
echo ==============================================================================
echo   Bu arac sunlari gosterir:
echo     * Anakart / ethernet karti  : destekledigi MAX hiz
echo     * Ag kablosu                : Cat sinifi ve kablo sagligi
echo     * Modem                     : port ve Wi-Fi SON hiz destekleri
echo     * Zincir analizi            : hangi halka hizi dusuruyor (darbogaz)
echo     * Gercek hiz olcumu + masaustune rapor
echo ==============================================================================
echo.
echo   Baslatiliyor... (yonetici izni istemez, sistem sadece okunur)
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0internet_hiz_analiz.ps1"

echo.
echo ==============================================================================
echo   ANALIZ TAMAMLANDI - Rapor masaustune kaydedildi.
echo   Kapatmak icin bir tusa bas.
echo ==============================================================================
pause >nul
