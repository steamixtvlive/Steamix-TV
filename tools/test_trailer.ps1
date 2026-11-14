$adb = "C:\Users\sehab\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$d = "127.0.0.1:5555"
$apk = "C:\Users\sehab\Desktop\NOTFLIX-Android\app\build\outputs\apk\release\notflix.apk"

& $adb -s $d uninstall com.notflix.app 2>&1 | Out-Null
& $adb -s $d install $apk 2>&1 | Select-Object -Last 1
& $adb -s $d logcat -c
& $adb -s $d shell am start -n com.notflix.app/.MainActivity 2>&1 | Select-Object -Last 1
Start-Sleep -Seconds 9

# Filmler sekmesine gec
& $adb -s $d shell uiautomator dump /sdcard/n1.xml | Out-Null
$xml = (& $adb -s $d shell cat /sdcard/n1.xml) -join ''
$m = [regex]::Match($xml, '<node[^>]*text="Filmler"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if ($m.Success) {
  $cx = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
  $cy = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
  & $adb -s $d shell input tap $cx $cy
  "Filmler sekmesi: $cx,$cy"
} else { "Filmler sekmesi bulunamadi" }
Start-Sleep -Seconds 10

# Ilk filmin uzerine tikla (griddeki ilk kart)
& $adb -s $d shell input tap 180 700
Start-Sleep -Seconds 16

& $adb -s $d shell screencap -p /sdcard/tr1.png | Out-Null
& $adb -s $d pull /sdcard/tr1.png "C:\Users\sehab\Desktop\NOTFLIX-Android\t-trailer.png" | Out-Null

Write-Output "--- TRAILER LOGLARI ---"
& $adb -s $d logcat -d | Select-String -Pattern "NotflixTrailer|iframe-api|player-ready|state:|redirect|trailer" | Select-Object -Last 30 | ForEach-Object { $_.Line }
