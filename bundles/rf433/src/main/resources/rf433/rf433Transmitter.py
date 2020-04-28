import RPi.GPIO as GPIO
import argparse
import struct
import time

parser = argparse.ArgumentParser(
    description='Script to transmit data by rf433 transmitter')
parser.add_argument("-c", "--try-count", type=int, help="Try count")
parser.add_argument("-d", "--duration", type=int, help="Duration")
parser.add_argument("-p", "--pin", type=int, help="Pin")
parser.add_argument('filename', action='store')

args = parser.parse_args()
NUM_ATTEMPTS = args.try_count
DURATION = args.duration
TRANSMIT_PIN = args.pin
print("TRANSMIT_PIN:  " + str(TRANSMIT_PIN))

times = []

with open(args.filename) as data:
    while True:
        part = data.read(4)
        if len(part) == 0:
            break
        times.append(round(struct.unpack('>f', part)[0], DURATION));

print("Read " + str(len(times)) + " floats")

GPIO.setmode(GPIO.BCM)
GPIO.setup(TRANSMIT_PIN, GPIO.OUT)
for t in range(NUM_ATTEMPTS):
    i = 0
    while i < len(times):
        GPIO.output(TRANSMIT_PIN, 1)
        time.sleep(times[i])
        GPIO.output(TRANSMIT_PIN, 0)
        time.sleep(times[i + 1])
        i += 2

GPIO.cleanup()
