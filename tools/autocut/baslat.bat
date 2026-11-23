@echo off
chcp 65001 >nul
title KITSUGI AUTOCUT
cd /d "%~dp0"

REM ---- Python bul ----
where python >nul 2>nul
if errorlevel 1 (
  where py >nul 2>nul
  if errorlevel 1 (
    echo.
    echo [HATA] Python bulunamadi.
    echo python.org adresinden Python 3.10+ kur, kurulumda
    echo "Add python.exe to PATH" isaretle, sonra bu dosyayi tekrar ac.
    pause
    exit /b 1
  )
  set PY=py -3
) else (
  set PY=python
)

REM ---- ilk acilis: sanal ortam + paketler ----
if not exist "venv\Scripts\python.exe" (
  echo [KURULUM] Sanal ortam olusturuluyor - sadece ilk acilista...
  %PY% -m venv venv
  call venv\Scripts\activate
  python -m pip install --upgrade pip
  pip install -r requirements.txt
) else (
  call venv\Scripts\activate
)

REM ---- FFmpeg kontrolu ----
where ffmpeg >nul 2>nul
if errorlevel 1 (
  echo [BILGI] PATH'te ffmpeg yok, imageio-ffmpeg kullanilacak - sorun degil.
)

python app.py
if errorlevel 1 pause
