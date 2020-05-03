#!/bin/bash

VERSION=$(curl https://api.github.com/repos/touchhome/touchHome-core/releases/latest | python -c "import sys,json; print json.load(sys.stdin)['name']")
echo "Latest app version: $VERSION"
PROGRAM_NAME="touchHome"
PROGRAM_PATH=$HOME/${PROGRAM_NAME}.jar
EXPECTED_CHECKSUM="`wget -qO- https://github.com/touchhome/touchHome-core/releases/download/${VERSION}/touchHome.jar.md5`"
RELEASE_PROGRAM_URL="https://github.com/touchhome/touchHome-core/releases/download/${VERSION}/touchHome.jar"
if [ -f "$PROGRAM_PATH" ]; then
    ACTUAL_CHECKSUM=`md5sum ${PROGRAM_PATH} | awk '{ print $1 }'`
    if [ "$ACTUAL_CHECKSUM" == "$EXPECTED_CHECKSUM" ]; then
       echo "Program already updated"
       exit 0
    fi
fi

PROGRAM_RELEASE_PATH=$HOME/${PROGRAM_NAME}.tmp
rm -f ${PROGRAM_RELEASE_PATH}
echo "Downloading release: ${RELEASE_PROGRAM_URL}"
wget -q ${RELEASE_PROGRAM_URL} -O ${PROGRAM_RELEASE_PATH}

ACTUAL_CHECKSUM=`md5sum ${PROGRAM_RELEASE_PATH} | awk '{ print $1 }'`
if [ "$ACTUAL_CHECKSUM" != "$EXPECTED_CHECKSUM" ]; then
    echo "Files checksum not equals. Expected: $EXPECTED_CHECKSUM, but actual is $ACTUAL_CHECKSUM"
    exit 2
fi


PROGRAM_NAME="touchHome"
PID=`ps -ef | grep ${PROGRAM_NAME} | awk '{print $2}'`
if ! [ -z "${PID}" ]; then
   echo "Killing old program '${PID}'"
   sudo kill ${PID}
fi

echo "Replace program"
mv ${PROGRAM_RELEASE_PATH} ${PROGRAM_PATH}

echo "Running program"
nohup sudo java -jar ${PROGRAM_PATH} &>/dev/null &
