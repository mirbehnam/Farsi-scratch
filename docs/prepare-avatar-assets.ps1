$ErrorActionPreference = 'Stop'
$assetRoot = Join-Path $PSScriptRoot '../app/src/main/res/drawable-nodpi'
$manifest = Get-Content (Join-Path $PSScriptRoot 'AVATAR_IMAGE_PROMPTS.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($asset in $manifest.assets) {
    $target = Join-Path $assetRoot ("learner_badge_" + $asset.id + ".webp")
    if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite: $target" }
    & 'C:/ffmpeg/ffmpeg-7.1.1-essentials_build/bin/ffmpeg.exe' -hide_banner -loglevel error -i $asset.source -vf 'scale=600:600:flags=lanczos' -c:v libwebp -quality 95 -compression_level 6 -frames:v 1 $target
    if ($LASTEXITCODE -ne 0) { throw "Asset conversion failed: $($asset.id)" }
    Get-Item -LiteralPath $target | Select-Object Name, Length
}
