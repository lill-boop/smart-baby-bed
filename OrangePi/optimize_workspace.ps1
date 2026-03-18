Write-Host "Starting Project Optimization..."

# Define Android Project Path
$androidProjectPath = Join-Path $PSScriptRoot "Baby\BabyBedApp\BabyBedApp"

# 1. Clean Android Project (Gradle Clean)
if (Test-Path $androidProjectPath) {
    Write-Host "Cleaning Android Project build files at $androidProjectPath..."
    Push-Location $androidProjectPath
    
    if (Test-Path "gradlew.bat") {
        try {
            # Run gradlew clean
            & .\gradlew.bat clean
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Android Project Cleaned Successfully!" -ForegroundColor Green
            } else {
                Write-Host "Android Project Clean encountered an issue." -ForegroundColor Yellow
            }
        } catch {
            Write-Host "Failed to run Gradle clean command: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "gradlew.bat not found, skipping Android clean." -ForegroundColor Yellow
    }
    
    Pop-Location
} else {
    Write-Host "Android project path not found." -ForegroundColor Yellow
}

# 2. General System Tips
Write-Host "`nOptimization Tips:"
Write-Host "1. Run Windows 'Disk Cleanup' tool for system files."
Write-Host "2. Clear files in %TEMP% folder manually if needed."
Write-Host "3. Check Task Manager for startup apps."

Write-Host "`nWorkspace optimization completed."
