# 推送修复后的原生工程到 GitHub
Set-Location $PSScriptRoot

git add .
git status
git commit -m "fix CI build and playlist Flow"
if ($LASTEXITCODE -ne 0) {
  Write-Host "No new commit (maybe nothing changed), continue push..."
}

git remote remove origin 2>$null
git remote add origin https://github.com/nidycai/yunplayer.git
git push -u origin main --force

Write-Host ""
Write-Host "Done. Open Actions and re-run Build YunPlayer Native APK"
Write-Host "https://github.com/nidycai/yunplayer/actions"
