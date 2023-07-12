#!/bin/bash

# Define the file name and path
launcher="homio-launcher.jar"
root_path="$HOME/homio"
java_path="java"

# Check if the file exists locally
if [ ! -f "$root_path/$launcher" ]; then
    echo "$launcher does not exist locally. Downloading from GitHub..."

    # Download the file from GitHub
    wget -P "$root_path" "https://github.com/homiodev/static-files/raw/master/homio-launcher.jar"

    echo "File 'homio-launcher.jar' downloaded successfully."
fi

if [ -f "$root_path/homio-app.jar" ]; then
    app="homio-app.jar"
else
    app="homio-launcher.jar"
fi

"$java_path" -jar "$root_path/$app"
exit_code=$?

if [ "$exit_code" -eq 4 ]; then
    echo "Exit code is 4. Updating application..."

    if [ -f "$root_path/homio-app.zip" ]; then
        echo "homio-app.zip file found. Extracting..."
        tar -xf "$root_path/homio-app.zip" -C "$root_path"
        rm "$root_path/homio-app.zip"
        exec "$0" # Restart the script
    fi
else
    echo "Homio app exit code with abnormal: $exit_code"
fi
