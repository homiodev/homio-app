import argparse
import struct
import time
from datetime import datetime
from rpi_rf import RFDevice

rfdevice = None

parser = argparse.ArgumentParser(
    description='Script to sniffer data from rf433 receiver')
parser.add_argument("-d", "--duration", type=int, help="Duration")
parser.add_argument("-p", "--pin", type=int, help="GPIO Pin")
parser.add_argument('filename', action='store')

args = parser.parse_args()
MAX_DURATION = args.duration
RECEIVE_PIN = args.pin

RECEIVED_SIGNAL = [[], []]  # [[time of reading], [signal reading]]

if __name__ == '__main__':
    rfdevice = RFDevice(RECEIVE_PIN)
    print('**Set GPOI PIN input as ' + str(RECEIVE_PIN))
    rfdevice.enable_rx()
    cumulative_time = 0
    beginning_time = datetime.now()

    print('**Started recording**')
    while cumulative_time < MAX_DURATION:
        time_delta = rfdevice.rx_code_timestamp - beginning_time
        RECEIVED_SIGNAL[0].append(time_delta)
        RECEIVED_SIGNAL[1].append(rfdevice.rx_code)
        cumulative_time = time_delta.seconds
        time.sleep(0.01)

    print('**Ended recording**')
    print(str(len(RECEIVED_SIGNAL[0])) + 'samples recorded')
    GPIO.cleanup()

    print('**Processing results**')
    for i in range(len(RECEIVED_SIGNAL[0])):
        RECEIVED_SIGNAL[0][i] = RECEIVED_SIGNAL[0][i].seconds + RECEIVED_SIGNAL[0][i].microseconds / 1000000.0

    timeFile = args.filename + '.times'
    print('**Writing times results to file ' + timeFile)
    floats = RECEIVED_SIGNAL[0]
    s = struct.pack('>%sf' % len(floats), *floats)
    f = open(timeFile, 'wb')
    f.write(s)
    f.close()

    valueFile = args.filename + '.values'
    print('**Writing values results to file ' + valueFile)
    values = RECEIVED_SIGNAL[1]
    f = open(valueFile, 'w')
    for item in values:
        f.write("%s" % item)
    f.close()
