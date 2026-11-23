/*
 * Tien ich "Gui Zalo" cho may tinh Windows (cai 1 lan) - lam giong app cu: chay Zalo.exe kem file PDF
 * -> Zalo PC mo bang chon nguoi nhan. Trinh duyet khong tu chay duoc chuong trinh, nen:
 *  - Bo cai (.bat, khong can quyen quan tri): dang ky lien ket nskd-zalo:// cho tai khoan Windows dang dung
 *    + chep script PowerShell vao %LOCALAPPDATA%\NhomSatKiemDien.
 *  - Bam "Gui Zalo" tren web -> mo nskd-zalo://send/<ma>/<ten file> -> script tai file tu CHINH trang web nay
 *    (dia chi ghi cung luc cai, khong lay tu lien ket) -> chay Zalo.exe "file".
 * Lien ket chi chua ma + ten file, script kiem tra chat (chi chu, so, _ . -) -> khong chay duoc lenh la.
 */
export const ZALO_SCHEME = 'nskd-zalo'

const PS1 = (origin: string) => `param([string]$Link)
# Gui Zalo - Nhom Sat Kiem Dien. Duoc goi khi bam "Gui Zalo" tren ${origin}
Add-Type -AssemblyName PresentationFramework
$Base = '${origin}'
function Say([string]$m) { Log ('THONG BAO: ' + $m); [System.Windows.MessageBox]::Show($m, 'Gửi Zalo') | Out-Null }
# Nhat ky tung buoc (gui cho nguoi cai dat khi loi): %LOCALAPPDATA%\\NhomSatKiemDien\\gui-zalo.log, giu ~300 dong cuoi
$LogFile = Join-Path $env:LOCALAPPDATA 'NhomSatKiemDien\\gui-zalo.log'
function Log([string]$m) {
  try {
    Add-Content -LiteralPath $LogFile -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $m) -Encoding UTF8
    $l = @(Get-Content -LiteralPath $LogFile -Encoding UTF8); if ($l.Count -gt 400) { Set-Content -LiteralPath $LogFile -Value $l[-300..-1] -Encoding UTF8 }
  } catch {}
}
Log ('--- Bat dau (phien ban 3). Lien ket: ' + $Link)

# 1. Kiem tra lien ket: chi ma + ten file (chu, so, _ . -) -> khong chay duoc lenh la
if ($Link -notmatch '^${ZALO_SCHEME}://send/([A-Za-z0-9_-]{20,80})/([A-Za-z0-9_.-]{1,80}\\.(pdf|png))/?$') { Say 'Liên kết gửi Zalo không hợp lệ.'; exit 1 }
$token = $Matches[1]; $name = $Matches[2]
Log ('Lien ket hop le, file: ' + $name)

# 2. Tai file tu chinh trang web
try {
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  $dir = Join-Path $env:TEMP 'NhomSatKiemDien'
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $file = Join-Path $dir $name
  Invoke-WebRequest -UseBasicParsing -Uri "$Base/api/public/share/$token" -OutFile $file -ErrorAction Stop
  Log ('Tai file xong: ' + $file + ' (' + (Get-Item -LiteralPath $file).Length + ' byte)')
} catch { Say ('Tải file lỗi: ' + $_.Exception.Message + "\`n\`nBấm Gửi Zalo lại trên trang web."); exit 1 }

# 3. Tim Zalo PC: cho cai mac dinh -> Zalo dang chay -> danh sach phan mem da cai (tung muc, muc loi thi bo qua)
function Find-Zalo {
  foreach ($c in @("$env:LOCALAPPDATA\\Programs\\Zalo\\Zalo.exe", "$env:ProgramFiles\\Zalo\\Zalo.exe", "\${env:ProgramFiles(x86)}\\Zalo\\Zalo.exe")) {
    try { if ($c -and (Test-Path -LiteralPath $c)) { return $c } } catch {}
  }
  try {
    foreach ($pr in @(Get-Process -Name 'Zalo' -ErrorAction SilentlyContinue)) {
      try { if ($pr.Path -and (Test-Path -LiteralPath $pr.Path)) { return $pr.Path } } catch {}
    }
  } catch {}
  foreach ($root in 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall', 'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall', 'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall') {
    $keys = @()
    try { $keys = @(Get-ChildItem -LiteralPath $root -ErrorAction SilentlyContinue) } catch {}
    foreach ($k in $keys) {
      try {
        $dn = [string]$k.GetValue('DisplayName')
        if ($dn -notlike 'Zalo*') { continue }
        $loc = [string]$k.GetValue('InstallLocation')
        if ($loc) { $c = Join-Path $loc 'Zalo.exe'; if (Test-Path -LiteralPath $c) { return $c } }
        $icon = ([string]$k.GetValue('DisplayIcon')).Split(',')[0].Trim('"')
        if ($icon -like '*.exe' -and (Test-Path -LiteralPath $icon)) { return $icon }
      } catch {}
    }
  }
  return $null
}
$zalo = $null
try { $zalo = Find-Zalo } catch { Log ('Tim Zalo loi: ' + $_.Exception.Message) }
Log ('Zalo.exe: ' + $zalo + ' | Zalo dang chay: ' + @(Get-Process -Name 'Zalo' -ErrorAction SilentlyContinue).Count + ' tien trinh')
if (-not $zalo) {
  Say "Không tìm thấy Zalo PC trên máy này. Cài Zalo PC (zalo.me/pc) rồi bấm Gửi Zalo lại.\`n\`nFile đã tải về: $file"
  Start-Process explorer.exe "/select,\`"$file\`""
  exit 1
}

# 4. Giong app cu: Zalo.exe "duong dan file" -> Zalo mo bang chon nguoi nhan
try {
  $pr = Start-Process -FilePath $zalo -ArgumentList "\`"$file\`"" -PassThru -ErrorAction Stop
  Log ('Da chay: "' + $zalo + '" "' + $file + '" (PID ' + $pr.Id + ')')
} catch { Say ('Mở Zalo lỗi (' + $zalo + '): ' + $_.Exception.Message) }
`

/** Noi dung file cai-gui-zalo.bat (CRLF). Bat chi chua chu khong dau; phan PowerShell (co dau) doc bang UTF-8. */
export function installerBat(origin: string): string {
  const bat = `@echo off
rem Cai tien ich "Gui Zalo" cho trang ${origin} (chi tai khoan Windows nay, khong can quyen quan tri)
set "D=%LOCALAPPDATA%\\NhomSatKiemDien"
if not exist "%D%" mkdir "%D%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t = Get-Content -Raw -Encoding UTF8 -LiteralPath '%~f0'; $m = '#PS1' + '#START'; $i = $t.IndexOf($m); Set-Content -LiteralPath (Join-Path $env:LOCALAPPDATA 'NhomSatKiemDien\\gui-zalo.ps1') -Value $t.Substring($i + $m.Length).TrimStart() -Encoding UTF8"
if errorlevel 1 goto loi
reg add "HKCU\\Software\\Classes\\${ZALO_SCHEME}" /ve /d "URL:Gui Zalo - Nhom Sat Kiem Dien" /f >nul
reg add "HKCU\\Software\\Classes\\${ZALO_SCHEME}" /v "URL Protocol" /d "" /f >nul
reg add "HKCU\\Software\\Classes\\${ZALO_SCHEME}\\shell\\open\\command" /ve /d "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \\"%D%\\gui-zalo.ps1\\" \\"%%1\\"" /f >nul
if errorlevel 1 goto loi
echo.
echo  Da cai xong "Gui Zalo". Quay lai trang web, bam Gui Zalo.
echo  Lan dau trinh duyet hoi "Mo Gui Zalo?": tich "Luon cho phep" roi bam Mo.
echo.
pause
exit /b 0
:loi
echo  Cai khong thanh cong. Chup man hinh nay gui nguoi cai dat.
pause
exit /b 1
#PS1#START
${PS1(origin)}`
  return bat.replace(/\r?\n/g, '\r\n')
}

export function downloadInstaller() {
  const blob = new Blob([installerBat(window.location.origin)], { type: 'application/octet-stream' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'cai-gui-zalo.bat'
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 5000)
}
