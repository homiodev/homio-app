#!/bin/bash

function print_info {
        echo -n -e '\e[1;36m'
        echo -n $1
        echo -e '\e[0m'
}

function print_warn {
        echo -n -e '\e[1;33m'
        echo -n $1
        echo -e '\e[0m'
}

function check_install {
        if [ -z "`which "$1" 2>/dev/null`" ]
        then
                executable=$1
                shift
                while [ -n "$1" ]
                do
                        DEBIAN_FRONTEND=noninteractive apt-get -q -y install "$1"
                        print_info "$1 installed for $executable"
                        shift
                done
                return 1
        else
                print_warn "$2 already installed"
                return 0
        fi
}

function stop_app {
   print_warn "stop program"
   if test -f "/etc/systemd/system/touchhome.service"; then
      systemctl stop touchhome
   else
      sudo kill ${PID}
   fi
}

function start_app {
   print_info "Running program"
   if test -f "/etc/systemd/system/touchhome.service"; then
      systemctl start touchhome
   else
      nohup sudo java -jar ${PROGRAM_PATH} &>/dev/null &
   fi
}

sudo apt update

check_install psql postgresql
if [ $? -ne 0 ]; then
   print_info "Alter postgres password"
   sudo -u postgres psql -c"ALTER user postgres WITH PASSWORD 'postgres'"
   sudo service postgresql restart
fi

PROGRAM_NAME="touchhome"
PROGRAM_PATH=/opt/${PROGRAM_NAME}.jar
EXPECTED_CHECKSUM="`wget -qO- https://bintray.com/touchhome/touchhome/download_file?file_path=touchhome.jar.md5`"
RELEASE_PROGRAM_URL="https://bintray.com/touchhome/touchhome/download_file?file_path=touchhome.jar"

PROGRAM_NAME="touchhome"
PID=`ps aux | grep -v -e 'grep ' | grep ${PROGRAM_NAME} | tr -s " " | cut -d " " -f 2`

PROGRAM_RELEASE_PATH=/opt/${PROGRAM_NAME}.tmp
rm -f ${PROGRAM_RELEASE_PATH}

if [ -f "$PROGRAM_PATH" ]; then
    ACTUAL_CHECKSUM=`md5sum ${PROGRAM_PATH} | awk '{ print $1 }'`
    if [ "$ACTUAL_CHECKSUM" == "$EXPECTED_CHECKSUM" ]; then
       print_info "Program already updated"
       if ! [ -z "${PID}" ]; then
          print_info "App already started"
       else
          start_app
       fi
       exit 0
    else
       print_warn "Files checksum not equals. Expected: $EXPECTED_CHECKSUM, but actual is $ACTUAL_CHECKSUM"
    fi
fi

print_warn "Downloading release: ${RELEASE_PROGRAM_URL}"
wget ${RELEASE_PROGRAM_URL} -O ${PROGRAM_RELEASE_PATH}

ACTUAL_CHECKSUM=`md5sum ${PROGRAM_RELEASE_PATH} | awk '{ print $1 }'`
if [ "$ACTUAL_CHECKSUM" != "$EXPECTED_CHECKSUM" ]; then
    print_warn "Files checksum not equals. Expected: $EXPECTED_CHECKSUM, but actual is $ACTUAL_CHECKSUM"
    exit 2
fi

if ! [ -z "${PID}" ]; then
   print_warn "Killing old program '${PID}'"
   sudo kill ${PID}
fi

stop_app

print_info "Replace program"
mv ${PROGRAM_RELEASE_PATH} ${PROGRAM_PATH}

start_app

