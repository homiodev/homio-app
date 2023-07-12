#!/bin/bash

root_path="$HOME/homio"

if [[ -f "homio.properties" ]]; then
    while IFS="=" read -r key value; do
        if [[ "$key" == "rootPath" && -d "$value" ]]; then
            root_path="$value"
        fi
    done < "homio.properties"
fi

mkdir -p $root_path
echo "root_path: '$root_path'"

java_path=$(command -v java)
if [[ -z "$java_path" || "$(java -version 2>&1 | awk 'NR==1{print $3}' | cut -d'"' -f2)" != "17" ]]; then
	java_path="$root_path/jdk-17.0.7+7-jre/bin/java"
    if [ -x "$java_path" ]; then
	   echo "Java is installed at path $java_path"
	else
      echo "Java not installed or version is not 17. Installing..."

      # Download Java 17
      wget -O "$root_path/jre.tar.gz" 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7+7/OpenJDK17U-jre_x64_linux_hotspot_17.0.7_7.tar.gz'

     # Extract Java archive
      tar xzf "$root_path/jre.tar.gz" -C "$root_path"

      # Remove the Java archive
      rm "$root_path/jre.tar.gz"
      echo "Java 17 has been installed  to $java_path"
	fi
else
    echo "Java 17 already installed."
fi

# Define the file name and path
launcher="homio-launcher.jar"

# Check if the file exists locally
if [[ ! -f "$root_path/$launcher" ]]; then
    echo "$launcher does not exist locally. Downloading from GitHub..."

    # Download the file from GitHub
    wget -O "$root_path/$launcher" 'https://github.com/homiodev/static-files/raw/master/homio-launcher.jar'

    echo "File 'homio-launcher.jar' downloaded successfully."
fi

if [[ -f "$root_path/homio-app.jar" ]]; then
    app="homio-app.jar"
else
    app="homio-launcher.jar"
fi

echo "Run $java_path -jar $root_path/$app"
"$java_path" -jar "$root_path/$app"
exit_code=$?

# Unzip install/update if result code is 4 and update file exists
if [[ $exit_code -eq 4 ]]; then
    echo "Exit code is 4. Update application..."
    if [[ -f "$root_path/homio-app.zip" ]]; then
        echo "homio-app.zip file found. Extracting..."
        tar -xf "$root_path/homio-app.zip" -C "$root_path"
        rm "$root_path/homio-app.zip"
        exec "$0"
    fi
else
    echo "Homio app exit code with abnormal: $exit_code"
fi
