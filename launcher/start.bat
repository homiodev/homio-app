@echo off

setlocal

:start

rem Define the file name and path
set "launcher=homio-launcher.jar"

set "rootpath=%USERPROFILE%\homio"
set "java_path=java"

rem Check if the file exists locally
if not exist "%rootpath%\%launcher%" (
    echo %launcher% does not exist locally. Downloading from GitHub...

    rem Download the file from GitHub
    powershell -command "(New-Object Net.WebClient).DownloadFile('https://github.com/homiodev/static-files/raw/master/homio-launcher.jar', '%rootpath%\%launcher%')"

    echo File 'homio-launcher.jar' downloaded successfully.
)

if exist "%rootpath%\homio-app.jar" (
   set "app=homio-app.jar"
) else (
   set "app=homio-launcher.jar"
)

"%java_path%" -jar "%rootpath%\%app%"
set "exit_code=%errorlevel%"

if %exit_code% equ 4 (
    echo Exit code is 4. Update application...
    if exist "%rootpath%\homio-app.zip" (
        echo homio-app.zip file found. Extracting...
        tar -xf "%rootpath%\homio-app.zip" -C "%rootpath%"
        del "%rootpath%\homio-app.zip"
        goto start
    )
) else (
    echo Homio app exit code with abnormal: %exit_code%
)

endlocal
