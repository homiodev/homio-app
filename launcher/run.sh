#!/bin/bash

set -e

echo "Check wget,tar command is presents"
abort=0
for cmd in wget tar; do
	if [ -z "$(command -v $cmd)" ]; then
		cat >&2 <<-EOF
		Error: unable to find required command: $cmd
		EOF
		abort=1
	fi
done
[ $abort = 1 ] && exit 1

echo "Check sudo"
sudo=
if [ "$(id -u)" -ne 0 ]; then
	if [ -z "$(command -v sudo)" ]; then
		cat >&2 <<-EOF
		Error: this app needs the ability to run commands as root.
		You are not running as root and we are unable to find "sudo" available.
		EOF
		exit 1
	fi
	sudo="sudo -E"
fi

root_path="/opt/homio"

if [[ -f "config/homio.properties" ]]; then
    while IFS="=" read -r key value; do
        if [[ "$key" == "rootPath" && -d "$value" ]]; then
            root_path="$value"
        fi
    done < "config/homio.properties"
fi

sudo mkdir -p $root_path
echo "root_path: '$root_path'"

java_path=$(command -v java)
if [[ -z "$java_path" || "$($java_path -version 2>&1 | grep -oP 'version "\K\d+')" != "17" ]]; then
  echo "Unable to find java 17 in classpath"
  java_path="$root_path/jdk-17.0.7+7-jre/bin/java"
  if [ -x "$java_path" ]; then
  	   echo "Java is installed at path $java_path"
  	else
        echo "Java not installed. Installing..."

        # Download Java 17
        wget -O "$root_path/jre.tar.gz" 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7+7/OpenJDK17U-jre_x64_linux_hotspot_17.0.7_7.tar.gz'

       # Extract Java archive
        tar xzf "$root_path/jre.tar.gz" -C "$root_path"

        # Remove the Java archive
        rm -f "$root_path/jre.tar.gz"
        echo "Java 17 has been installed  to $java_path"
  	fi
else
  echo "Found java 17 in classpath"
fi

echo "Java path: $java_path"

# Define the file name and path
launcher="$root_path/homio-launcher.jar"

# Check if the file exists locally
if [[ ! -f "$launcher" ]]; then
    echo "$launcher does not exist locally. Downloading from GitHub..."

    # Download the file from GitHub
    wget -O "$launcher" 'https://github.com/homiodev/static-files/raw/master/homio-launcher.jar'

    echo "File 'homio-launcher.jar' downloaded successfully."
fi

update_application() {
    if [[ -f "$root_path/homio-app.zip" ]]; then
        if [[ -f "$root_path/homio-app.jar" ]]; then
            echo "Backup $root_path/homio-app.jar to $root_path/homio-app.jar_backup"
            cp "$root_path/homio-app.jar" "$root_path/homio-app.jar_backup"
        fi

        echo "Extracting $root_path/homio-app.zip"
        if unzip -o "$root_path/homio-app.zip" -d "$root_path"; then
            echo "Homio ZIP file extracted successfully."
            echo "Remove archive $root_path/homio-app.zip"
            rm -f "$root_path/homio-app.zip"
        else
            echo "Failed extract Homio ZIP file"
            if [[ -f "$root_path/homio-app.jar_backup" ]]; then
                echo "Recovery backup from $root_path/homio-app.jar_backup"
                mv "$root_path/homio-app.jar_backup" "$root_path/homio-app.jar"
            else
              echo "Remove archive $root_path/homio-app.zip"
              rm -f "$root_path/homio-app.zip"
            fi
        fi
    fi
}

update_application

if [[ -f "$root_path/homio-app.jar" ]]; then
    app="homio-app.jar"
else
    app="homio-launcher.jar"
fi

echo "Run $java_path -jar $root_path/$app"
sudo "$java_path" -jar "$root_path/$app"
exit_code=$?

# Unzip install/update if result code is 4 and update file exists
if [[ $exit_code -eq 221 ]]; then
    echo "Update application..."
    update_application
else
    echo "Homio app exit code with abnormal: $exit_code"
    if [[ -f "$root_path/homio-app.jar_backup" ]]; then
      echo "Recovery homio-app.jar backup"
      cp "$root_path/homio-app.jar_backup" "$root_path/homio-app.jar"
    else
      rm -f "$root_path/homio-app.jar"
    fi
fi

echo "Restarting Homio"
exec "$0"
