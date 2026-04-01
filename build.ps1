# Build script for EloRanks plugin
# Use JAVA_HOME provided by GitHub Actions

mvn clean package

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nBuild successful!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nBuild failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}