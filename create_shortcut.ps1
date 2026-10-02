$WshShell = New-Object -comObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("C:\Users\sd190901\Desktop\OpenClaw起動.lnk")
$Shortcut.TargetPath = "C:\オープン\openclaw\OpenClaw起動.bat"
$Shortcut.WorkingDirectory = "C:\オープン\openclaw"
$Shortcut.Save()
Write-Output "Shortcut created successfully."
