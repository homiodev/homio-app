#!/bin/sh

VERSION=$1
PROGRAM_NAME="touchHome.jar"
PROGRAM_PATH=$HOME/${PROGRAM_NAME}.jar
EXPECTED_CHECKSUM=$(curl https://github.com/touchhome/touchHome-core/releases/download/${VERSION}/touchHome.jar.md5 -s)
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
echo "Downloading release..."
curl ${RELEASE_PROGRAM_URL} -o ${PROGRAM_RELEASE_PATH}

ACTUAL_CHECKSUM=`md5sum ${PROGRAM_RELEASE_PATH} | awk '{ print $1 }'`
if [ "$ACTUAL_CHECKSUM" != "$EXPECTED_CHECKSUM" ]; then
    echo "Files checksum not equals. Expected: $EXPECTED_CHECKSUM, but actual is $ACTUAL_CHECKSUM"
    exit 2
fi

PID=`ps -ef | grep '$PROGRAM_NAME' | awk '{print $2}'`
if ! [ -z "${PID}" ]; then
   echo "Killing old program"
   kill ${PID}
fi

echo "Replace program"
mv ${PROGRAM_RELEASE_PATH} ${PROGRAM_PATH}

echo "Running program"
nohup sudo java -jar ${PROGRAM_PATH} &>/dev/null &
