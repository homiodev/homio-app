@echo off

setlocal enabledelayedexpansion

set "root_path=%USERPROFILE%\homio"

if exist "config/homio.properties" (
    for /f "usebackq delims=" %%i in ("config/homio.properties") do (
        for /f "tokens=1* delims==" %%a in ("%%i") do (
            if "%%a"=="rootPath" if exist "%%b" (
                set "root_path=%%b"
            )
        )
    )
)

echo root_path: '%root_path%'

set "java_path=java"
where %java_path% >nul 2>&1
if %errorlevel% neq 0 (
    set "java_path=%root_path%/jdk-17.0.7+7-jre/bin/java"
    echo Java not found. Checking local: !java_path!

    if exist "!java_path!" (
	     echo Java is installed at path !java_path!
	  ) else (
	     echo Java not installed. Downloading...

       REM Download the Java installer
       powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7+7/OpenJDK17U-jre_x64_windows_hotspot_17.0.7_7.zip', '%root_path%\jre.zip')"

       REM Unzip the downloaded file to the root path
       tar -xf "%root_path%\jre.zip" -C "%root_path%"

       REM Clean up the downloaded zip file
       del "%root_path%\jre.zip"
       echo Java has been installed.
	  )
)

echo Use java: !java_path!

:start

rem Define the file name and path
set "launcher=homio-launcher.jar"

rem Check if the file exists locally
if not exist "%root_path%\%launcher%" (
    echo %launcher% does not exist locally. Downloading from GitHub...

    rem Download the file from GitHub
    powershell -command "(New-Object Net.WebClient).DownloadFile('https://github.com/homiodev/static-files/raw/master/homio-launcher.jar', '%root_path%\%launcher%')"

    echo File 'homio-launcher.jar' downloaded successfully.
)

if exist "%root_path%\homio-app.jar" (
   set "app=homio-app.jar"
) else (
   set "app=homio-launcher.jar"
)

echo "Run '!java_path! -jar %root_path%/%app%'"
call "!java_path!" -jar "%root_path%\%app%"
set "exit_code=%errorlevel%"

if %exit_code% equ 4 (
    echo Exit code is 4. Update application...
    if exist "%root_path%\homio-app.zip" (
        echo homio-app.zip file found. Extracting...
        tar -xf "%root_path%\homio-app.zip" -C "%root_path%"
        del "%root_path%\homio-app.zip"
        goto start
    )
) else (
    echo Homio app exit code with abnormal: %exit_code%
)

endlocal
