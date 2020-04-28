
#include <nRF24L01.h>
#include <printf.h>
#include <RF24.h>
#include <RF24_config.h>
#include <EEPROM.h>
#include "SparkFunBME280.h"
#include "Buffer.h"
#include "RTCTimer2.h"
#include <DHT2.h>

// Spark fun block initialization
BME280 bme280Sensor;
DHT2 dht(DHT22);

#define DEBUG              1

#ifdef DEBUG
  #define  INFO(arg0)            { printf("\nInfo[M:%s, L:%d, V: %d", __func__, __LINE__, arg0); }
  #define  INFO_MANY(arg0, arg1) { printf("\nInfo[M:%s, L:%d, V1: %d, V2: %d", __func__, __LINE__, arg0, arg1); }
  #define  EXEC(arg0)            { printf("\nExec[M:%s, L:%d, V: %i", __func__, __LINE__, arg0); }
  #define  ERROR(arg0)           { printf("\nErr[M:%s, L:%d, V: %d", __func__, __LINE__, arg0); }
  #define  PRINTF(...)           { printf(__VA_ARGS__); }
#else
  #define  INFO(arg0)            { }
  #define  INFO_MANY(arg0, arg1) { }
  #define  EXEC(arg0)            { }
  #define  ERROR(arg0)           { }
  #define  PRINTF(...)           { }
#endif

#define CLEAR_EEPROM       0
#define HARDCODE_DEVICE_ID 1
#define DEBUG_ERROR        1
#define HANDLER_SIZE      80
#define MAX_HANDLER_SIZE  12
#define OFFSET_WRITE_INDEX 9

// Handlers definitions
#define INVERT_PIN           0
#define SET_PIN              1
#define SET_PIN_N_SECONDS    2
#define INVERT_PIN_N_SECONDS 3
#define REQUEST_PIN          4
#define PLAY_TONE            5
int gg = 0;

#define PIN_REQUEST_TYPE_DHT               1
#define PIN_REQUEST_TYPE_SPARK_FUN_BME280  2

// handlers command sizes
uint8_t FIRST_LEVEL_SIZES[]{
        1, // INVERT_PIN
        2, // SET_PIN
        3, // SET_PIN_N_SECONDS
        2, // INVERT_PIN_N_SECONDS
        4, // REQUEST_PIN
        3  // PLAY_TONE
};
// End handlers definitions

const uint8_t EEPROM_CONFIGURED = 4;
const uint8_t TIMEOUT_BETWEEN_REGISTER = 10; // seconds

const uint8_t ARDUINO_DEVICE_TYPE = 17;

const uint8_t EXECUTED = 0;
const uint8_t FAILED_EXECUTED = 1;
const uint8_t REGISTER_COMMAND = 2;
const uint8_t SET_UNIQUE_READ_ADDRESS = 3;
const uint8_t GET_ID_COMMAND = 4;
const uint8_t GET_TIME_COMMAND = 5;
//const uint8_t SET_PIN_MODE_COMMAND                         = 6;
const uint8_t SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND = 6;
const uint8_t GET_PIN_VALUE_COMMAND = 7;
const uint8_t SET_PIN_VALUE_COMMAND = 8;
//const uint8_t GET_PIN_MODE_COMMAND                         = 9;
const uint8_t RESPONSE_COMMAND = 10;
const uint8_t PING = 11;
const uint8_t REMOVE_GET_PIN_VALUE_REQUEST_COMMAND = 12;
const uint8_t GET_PIN_VALUE_REQUEST_COMMAND = 14;
const uint8_t REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN = 15;


const uint8_t HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN = 30;
uint8_t settedUpValuesByHandler[21]; // uses for setPin

struct ArduinoConfig {
    unsigned int deviceID;
} arduinoConfig;

uint64_t readingPipe = 0;

RF24 radio(9, 10);

uint8_t handler_buf[MAX_HANDLER_SIZE]; // assume we have no data more than 12 bytes
uint8_t handler_buf_2[MAX_HANDLER_SIZE];

uint8_t handlers[HANDLER_SIZE]; // TODO: INCREASE SIZE AND SET DEBUG as 0
uint8_t genID = 0;
unsigned long lastPing = 0;
unsigned long MAX_PING_BEFORE_REMOVE_DEVICE = 600000; // 10 minutes

RTCTimer2 timer;

unsigned int calcCRC(uint8_t messageID, uint8_t commandID, uint8_t length, uint8_t peekStartIndex) {
    //PRINTF("\nCRC: %d, %d, %d\n", messageID, commandID, length);
    unsigned int crc = messageID + arduinoConfig.deviceID + commandID;
    for (uint8_t i = peekStartIndex; i < peekStartIndex + length; i++) {
        char ch = peekCharAbsolute(i); // char. not uint8_t!
        crc += abs(ch); // [from current index to length]
    }
    unsigned int finalCRC = (0xbeaf + crc) & 0x0FFFF;
    return finalCRC;
}

void writeMessage(uint8_t messageID, uint8_t commandID, uint8_t writtenLength) {
    INFO(writtenLength);
    radio.stopListening();
    // CRC
    unsigned int crc = calcCRC(messageID, commandID, writtenLength, OFFSET_WRITE_INDEX);
    resetAll();
 //   printPayload(writtenLength);

    // header
    writeByte(0x24);
    writeByte(0x24);
    // length
    writeByte(writtenLength);
    writeUInt(crc);
    // messageID
    writeByte(messageID);
    // device ID
    writeUInt(arduinoConfig.deviceID);
    // Command ID
    writeByte(commandID);
    /*for (int i = 9 + writtenLength; i < 32; i++) {
        writeByteAt(i, 0x30); // bad approach
    }*/
    printPayload(writtenLength);
    bool ok = radio.write(&payload, OFFSET_WRITE_INDEX + writtenLength);
    INFO(ok);
    beginWrite(); // reset written length to 0
}

void sendNoPayloadCallback(uint8_t messageID, uint8_t commandID, uint8_t CMD) {
    beginWrite();
    writeByte(commandID);
    writeMessage(messageID, CMD, getCurrentIndex());
}

void sendResponse(uint8_t messageID, uint8_t commandID, uint8_t value) {
    beginWrite();
    writeByte(commandID);
    writeByte(value);
    writeMessage(messageID, RESPONSE_COMMAND, getCurrentIndex());
}

void sendResponseULong(uint8_t messageID, uint8_t commandID, unsigned long value) {
    beginWrite();
    writeByte(commandID);
    writeULong(value);
    writeMessage(messageID, RESPONSE_COMMAND, getCurrentIndex());
}

void sendResponseUInt(uint8_t messageID, uint8_t commandID, unsigned int value) {
    beginWrite();
    writeByte(commandID);
    writeUInt(value);
    writeMessage(messageID, RESPONSE_COMMAND, getCurrentIndex());
}

void sendSuccessCallback(uint8_t messageID, uint8_t commandID) {
    sendNoPayloadCallback(messageID, commandID, EXECUTED);
}

void sendErrorCallback(uint8_t messageID, uint8_t commandID) {
    sendNoPayloadCallback(messageID, commandID, FAILED_EXECUTED);
}

uint8_t readPinID(uint8_t messageID, uint8_t cmd) {
    uint8_t pinID = readByte(); // [0] pinID A0-14,A1-15,A2-16,A3-17,A4-18,A5-19,A6-20,A7-21
    if (pinID < 0 || pinID > 21) {
        ERROR(pinID);
        pinID = static_cast<uint8_t>(-1);
        sendErrorCallback(messageID, cmd);
    }
    return pinID;
}

// LOW - 0, HIGH - 1
boolean setDigitalValue(uint8_t pinID, uint8_t value) {
    if (value == LOW || value == HIGH) {
        INFO_MANY(pinID, value);
        digitalWrite(pinID, value);
        return true;
    }
    ERROR(pinID);
    return false;
}

boolean setAnalogValue(uint8_t pinID, uint8_t value) {
    INFO_MANY(pinID, value);
    analogWrite(pinID, value);
    return true;
}

void setPinValueCommand(uint8_t messageID, uint8_t cmd) {
    uint8_t pinID = readByte();
    unsigned int value = readUInt();
    if (pinID >= 0 && pinID < 21) {
        if (pinID < 14) {
            if (value < 0 || value > 1) {
                ERROR(value);
                sendErrorCallback(messageID, cmd);
            } else {
                setDigitalValue(pinID, value);
            }
        } else {
            setAnalogValue(pinID, value);
        }
        sendSuccessCallback(messageID, cmd);
    } else {
        ERROR(pinID);
        sendErrorCallback(messageID, cmd);
    }
}

bool setUniqueReadAddressCommand(uint8_t messageID, uint8_t cmd) {
    uint64_t uniqueID = readULong();
    PRINTF("\nSP<%lu>", uniqueID);
    radio.openReadingPipe(2, uniqueID);
    //radio.printDetails();
    readingPipe = uniqueID;
    lastPing = millis();
    sendSuccessCallback(messageID, cmd);
    return true;
}

uint8_t getPinValue(uint8_t pinID) {
    if (pinID < 14) {
        return digitalRead(pinID);
    }
    return map(analogRead(pinID), 0, 1023, 0, 255);
}

uint8_t *subArray(int from, int length, bool firstBuffer, const uint8_t srcArray[]) {
    if (MAX_HANDLER_SIZE < length) {
        return nullptr;
    }
    uint8_t *res = firstBuffer ? handler_buf : handler_buf_2;
    uint8_t destIndex = 0;
    for (int i = from; i < from + length; i++) {
        res[destIndex] = srcArray[i];
        destIndex++;
    }
    return res;
}

uint8_t *getNextHandler(int index, uint8_t &sizeOfHandler) {
    uint8_t handlerType = handlers[index];
    if (handlerType == HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN) {
        uint8_t firstLevelHandler = handlers[index + 4];
        sizeOfHandler = 5 + FIRST_LEVEL_SIZES[firstLevelHandler];
        return subArray(index, sizeOfHandler, true, handlers);
    }
    return nullptr;
}

bool handlersMatch(uint8_t handlerType, const uint8_t existedHandler[], uint8_t existedHandlerSize,
                   const uint8_t handler[], uint8_t handlerSize) {
    if (existedHandlerSize != handlerSize + 1 || existedHandler[0] != handlerType)
        return false;
    for (uint8_t i = 0; i < handlerSize; i++)
        if (existedHandler[i + 1] != handler[i])
            return false;
    return true;
}

/**
   top level handler types:
   WHEN_VALUE_COMPARE_TO_VALUE       // 4: i.e.: [4, pinID, pinValue, [op, pinMode], 1-level handler] Description: [op, pinMode] - '>D':0,'<D':1, '=D':2, '>A':3, '<A':4, '=A':5
      1 - level handler types:
          INVERT_PIN                 // 0 [pinID]
          SET_PIN                    // 1 [pinID, value]
          SET_PIN_N_SECONDS          // 2 [pinID, value, seconds]
          INVERT_PIN_N_SECONDS       // 3 [pinID, seconds]
          REQUEST_PIN                // 5 [pinID]
          PLAY_TONE                  // 5 [pinID, frequency, duration]
**/
void addHandler(uint8_t handlerType, uint8_t handler[], uint8_t handlerSize) {
    int index = 0;
    uint8_t sizeOfHandler = 0;
    uint8_t *existedHandler = getNextHandler(index, sizeOfHandler);
    while (existedHandler != nullptr) {
        bool match = handlersMatch(handlerType, existedHandler, sizeOfHandler, handler, handlerSize);
        if (match) {
            return;
        }
        index += sizeOfHandler;
        existedHandler = getNextHandler(index, sizeOfHandler);
    }
    if (index + handlerSize + 1 > HANDLER_SIZE) {
        ERROR(handlerSize);
        return;
    }
    // here index is index for next slot
    uint8_t j = 0;
    handlers[index++] = handlerType;
    for (int i = index; i < index + handlerSize; i++)
        handlers[i] = handler[j++];
}

void printPinsHandlers(uint8_t *handler) {
    PRINTF("\nAll Pin Handlers");
    uint8_t handlerType = handler[0];
    if (handlerType == HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN) { // WHEN_VALUE_COMPARE_TO_VALUE
        PRINTF("\nFH:W.P:%d.V:%d.O:%d.L1:%d",
               handler[1], handler[2], handler[3], handler[4]);
               uint8_t length = FIRST_LEVEL_SIZES[handler[4]];
               PRINTF(" a[");
               for(uint8_t i = 0; i < length; i++) {
                 PRINTF("%d, ", handler[5 + i]);
               }
               PRINTF("]");
    } else
        ERROR(handlerType);
    PRINTF("\n\n");
}

void printAllPinsHandlers() {
        PRINTF("\nAll Handlers");
        int index = 0;
        uint8_t sizeOfHandler = 0;
        uint8_t *handler = getNextHandler(index, sizeOfHandler);
        while (handler != nullptr) {
            index += sizeOfHandler;
            printPinsHandlers(handler);
            handler = getNextHandler(index, sizeOfHandler);
        }
        PRINTF("\n------------");
}

void removeHandler(uint8_t handlerType, uint8_t handler[], uint8_t handlerSize) {
    int index = 0;
    uint8_t sizeOfHandler = 0;
    uint8_t *existedHandler = getNextHandler(index, sizeOfHandler);
    while (existedHandler != nullptr) {
        printPinsHandlers(existedHandler);
        bool match = handlersMatch(handlerType, existedHandler, sizeOfHandler, handler, handlerSize);

        if (match) {
            for (int i = index; i < HANDLER_SIZE - handlerSize - 1; i++) {
                handlers[i] = handlers[i + handlerSize + 1];
            }
            return;
        }
        index += sizeOfHandler;
        existedHandler = getNextHandler(index, sizeOfHandler);
    }
}

void requestPinValueDHT(uint8_t pinID, uint8_t handlerID) {
   INFO_MANY(pinID, handlerID);
   dht.begin(pinID); // if pinID already begin - dht just ignore this call
   float h = dht.readHumidity();
   float t = dht.readTemperature();

   beginWrite();
   writeByte(handlerID);
   writeFloat(isnan(h) ? 0 : h);
   writeFloat(isnan(t) ? 0 : t);
   writeMessage(0, SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND, getCurrentIndex());
}

void requestPinValueBME280(uint8_t pinID, uint8_t handlerID) {
   INFO_MANY(pinID, handlerID);
   float h = bme280Sensor.readFloatHumidity();
   float t = bme280Sensor.readTempC();
  // float p = bme280Sensor.readFloatPressure();

   beginWrite();
   writeByte(handlerID);
   writeFloat(isnan(h) ? 0 : h);
   writeFloat(isnan(t) ? 0 : t);
   //writeFloat(isnan(p) ? 0 : p);
   writeMessage(0, SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND, getCurrentIndex());
}

void requestPinFunc(uint32_t ts, uint8_t pinID, uint8_t handlerID, uint8_t pinRequestType, uint8_t ignore3) {
    INFO_MANY(pinID, pinRequestType);
    switch (pinRequestType) {
        case PIN_REQUEST_TYPE_DHT:
             requestPinValueDHT(pinID, handlerID);

            break;
        case PIN_REQUEST_TYPE_SPARK_FUN_BME280:
            requestPinValueBME280(pinID, handlerID);
            break;
        default:
            // send unique handlerID and simple value
            beginWrite();
            writeByte(handlerID);
            writeByte(getPinValue(pinID));
            writeMessage(0, SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND, getCurrentIndex());
    }
}

void readPinValueRequestCommand(uint8_t messageID, uint8_t cmd, bool remove) {
    uint8_t pinID = readPinID(messageID, cmd);
    if (pinID != -1) {
        uint8_t val = readByte();
        uint8_t handlerID = readByte();
        uint8_t pinRequestType = readByte();
        int interval = val == 0 ? 1 : val * 10;
        INFO_MANY(pinID, remove);
        INFO_MANY(handlerID, pinRequestType);
        if(remove) {
            timer.cancelBy2Params(pinID, handlerID, pinRequestType);
        } else {
            timer.everyTwo(interval, requestPinFunc, pinID, handlerID, pinRequestType);
        }
    }
}

bool executeCommandInternally(uint8_t messageID, unsigned int target, uint8_t cmd) {
    EXEC(cmd);
    uint8_t pinID;
    if (readingPipe == 0) {
        if (target != arduinoConfig.deviceID) {
            ERROR(arduinoConfig.deviceID); // deviceID not match
            return false;
        }
        if (cmd == SET_UNIQUE_READ_ADDRESS) {
            return setUniqueReadAddressCommand(messageID, cmd);
        } else {
            ERROR(cmd); // "\nErr exec cmd <%d> Cmd must SET_ADDRESS",
        }
        return false;
    }

    if (target == 0 || target == arduinoConfig.deviceID) {
        switch (cmd) {
            case EXECUTED:
                ERROR(cmd);
                sendErrorCallback(messageID, cmd);
                break;
            case FAILED_EXECUTED:
                ERROR(cmd);
                sendErrorCallback(messageID, cmd);
                break;
            case REGISTER_COMMAND:
                ERROR(cmd);
                sendErrorCallback(messageID, cmd);
                break;
            case SET_UNIQUE_READ_ADDRESS:
                ERROR(cmd);
                sendErrorCallback(messageID, cmd);
                break;
            case GET_ID_COMMAND:
                ERROR(cmd);
                sendResponseUInt(messageID, cmd, arduinoConfig.deviceID);
                break;
            case GET_TIME_COMMAND:
                ERROR(cmd);
                sendResponseULong(messageID, cmd, millis());
                break;
                //case SET_PIN_MODE_COMMAND:
                //  break;
            case GET_PIN_VALUE_COMMAND:
                pinID = readPinID(messageID, cmd);
                if (pinID > -1) {
                    uint8_t value = getPinValue(pinID);
                    sendResponse(messageID, cmd, value);
                }
                break;
            case SET_PIN_VALUE_COMMAND:
                setPinValueCommand(messageID, cmd);
                break;
                //case GET_PIN_MODE_COMMAND:
                //  break;
            case PING:
                lastPing = millis();
                beginWrite();
                writeMessage(messageID, PING, getCurrentIndex());
                break;
            case HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN:
                pinID = readPinID(messageID, cmd);
                if (pinID > -1) {
                    INFO_MANY(pinID, REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN);
                    uint8_t handlerSize = getFirstLevelHandlerSize();
                    if(handlerSize > -1) {
                        uint8_t *handler = subArray(OFFSET_WRITE_INDEX, handlerSize, false, getUPayload());
                        //printBuffer(handlerSize, handler);
                        if(handler == nullptr) {
                           ERROR(handlerSize);
                           return false;
                        }
                        addHandler(HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN, handler, handlerSize);
                        EEPROM.put(1, arduinoConfig);
                        printAllPinsHandlers();
                    }
                }
                break;
            case REMOVE_GET_PIN_VALUE_REQUEST_COMMAND:
                readPinValueRequestCommand(messageID, cmd, true);
                break;
            case GET_PIN_VALUE_REQUEST_COMMAND:
                readPinValueRequestCommand(messageID, cmd, false);
                break;
            case REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN:
                pinID = readPinID(messageID, cmd);
                if (pinID > -1) {
                    INFO_MANY(pinID, REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN);
                    uint8_t handlerSize = getFirstLevelHandlerSize();
                    if(handlerSize > -1) {
                        uint8_t *handler = subArray(OFFSET_WRITE_INDEX, handlerSize, false, getUPayload());
                        removeHandler(HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN, handler, handlerSize);
                        EEPROM.put(1, arduinoConfig);
                        printAllPinsHandlers();
                    }
                }
                break;
            default:
                ERROR(cmd);
                return false;
        }
        return true;
    } else {
        ERROR(target);
    }
    return false;
}

uint8_t getFirstLevelHandlerSize() {
  uint8_t firstLevelType = peekCharAbsolute(OFFSET_WRITE_INDEX + 3);
  if(firstLevelType > sizeof(FIRST_LEVEL_SIZES)) {
     ERROR(firstLevelType);
    // sendErrorCallback
    return -1;
  }
  return 4 + FIRST_LEVEL_SIZES[firstLevelType];
}

boolean executeCommand(uint8_t expectedMessageID) {
    resetAll(); // reset indexes for reading
    if (!(readChar() == 0x25 && (readChar() == 0x25))) {
        ERROR(0);
        return false;
    }
    char length = readChar(); // 2
    int crc = readUInt(); // 3
    char messageID = readChar(); // 5
    unsigned int target = readUInt(); // 6
    char commandID = readChar(); // 8
    INFO(getCurrentIndex());
    PRINTF("\nGot Length <%d>. M_id <%d>. T <%d>. Cmd <%d>. Crc <%d> ", length, messageID, target, commandID, crc);

    unsigned int calcCrc = calcCRC(messageID, commandID, length, getCurrentIndex()); // start calc CRC from current position - getWrittenLength();

    if (expectedMessageID != 255 && expectedMessageID != messageID) {
        ERROR(expectedMessageID);
        return false;
    }
    if (target != arduinoConfig.deviceID) {
        ERROR(target);
        return false;
    }
    if (calcCrc != crc) {
        ERROR(crc);
        return false;
    }
    return executeCommandInternally(messageID, target, commandID);
}


boolean readMessage() {
    if (radio.available()) { // if there is data ready
        boolean done = false;
        radio.read(getPayload(), 32);
        printPayload(31);
        return true;
    }
    INFO(gg++);
    return false;
}

// Wait here until we get a response, or timeout (250ms)
boolean readMessageNSeconds(int seconds) {
    radio.startListening();
    unsigned long started_waiting_at = millis();
    boolean timeout = false;
    while (!radio.available() && !timeout) {
        if (millis() - started_waiting_at > seconds * 1000) {
            timeout = true;
        }
        delay(200);
    }
    return readMessage();
}

void clearPinHandlers() {
    for (int i = 0; i < HANDLER_SIZE; i++)
        handlers[i] = 255;
}

void setup() {
    Serial.begin(9600);
    printf_begin();
    setOffsetWrite(OFFSET_WRITE_INDEX);
    if (CLEAR_EEPROM) {
        for (uint8_t i = 0; i < EEPROM.length(); i++) {
            EEPROM.write(i, 0);
        }
    }
    clearPinHandlers();
    bme280Sensor.begin();

    for (uint8_t i = 0; i < 21; i++)
        settedUpValuesByHandler[i] = 255;

    if (EEPROM.read(0) == EEPROM_CONFIGURED) {
        EEPROM.get(1, arduinoConfig);
        EXEC(arduinoConfig.deviceID);
        // print all handlers
        printAllPinsHandlers();
        // detect min interval
    } else {
        randomSeed(analogRead(0));
        long randNumber = random(99, 65535);
        if (HARDCODE_DEVICE_ID) {
            arduinoConfig.deviceID = 32621;
        } else {
            arduinoConfig.deviceID = abs(int(randNumber));
        }
        EEPROM.put(1, arduinoConfig);
        EEPROM.write(0, EEPROM_CONFIGURED);
    }
    uint8_t addresses[][6] = {"2Node", "1Node"};

    radio.begin();
    //radio.enableDynamicPayloads();
    //radio.setAutoAck(1);
    radio.setRetries(15, 15);
    radio.setPALevel(RF24_PA_MIN);
    radio.setDataRate(RF24_250KBPS);
    radio.setCRCLength(RF24_CRC_8);
    radio.setChannel(0x4C);
    radio.openReadingPipe(1, addresses[0]);

    radio.openReadingPipe(2, addresses[0]); // leave second pipe as first

    radio.openWritingPipe(addresses[1]);
    radio.printDetails();
}

uint8_t generateID() {
    genID = genID >= 99 ? 0 : genID + 1;
    return genID;
}

void setPinValue(uint8_t pinID, uint8_t value) {
    bool isAnalog = value > 1 ? true : false;
    if (isAnalog) setAnalogValue(pinID, value);
    else setDigitalValue(pinID, value);
}

void registerDevice() {
    if (millis() - lastPing > MAX_PING_BEFORE_REMOVE_DEVICE) { // if device pinged too ago
        ERROR(MAX_PING_BEFORE_REMOVE_DEVICE);
        readingPipe = 0;
        EEPROM.put(1, arduinoConfig);
        for (uint8_t i = 0; i < 21; i++)
            settedUpValuesByHandler[i] = 255;
        clearPinHandlers();
        // clear all timeouts
        for (uint8_t i = 0; i < 21; i++)
            settedUpValuesByHandler[i] = 255;
    }
    if (readingPipe == 0) {
        uint8_t messageID = 0;
        while (readingPipe == 0) {
            beginWrite();
            writeByte(ARDUINO_DEVICE_TYPE);
            messageID = generateID();
            writeMessage(messageID, REGISTER_COMMAND, getCurrentIndex());
            PRINTF("\nRead...");

            while (readingPipe == 0 && readMessageNSeconds(5)) { // while because if internet latency
                executeCommand(messageID);
            }
            clearPayload();
            if (readingPipe == 0) {
                //PRINTF("\nWait %ds", TIMEOUT_BETWEEN_REGISTER);
                delay(TIMEOUT_BETWEEN_REGISTER * 1000);
            }
        }
    }
}

uint8_t getInvertedValue(uint8_t pinID) {
    if (pinID < 14) {
        return digitalRead(pinID) == HIGH ? LOW : HIGH;
    }
    return 255 - map(analogRead(pinID), 0, 1023, 0, 255);
}

// invert state
void invertPinTimerFunc(uint32_t ts, uint8_t pinID, uint8_t pinValue, uint8_t ignore2, uint8_t ignore3) {
    INFO_MANY(pinID, pinValue);
    setPinValue(pinID, pinValue);
    settedUpValuesByHandler[pinID] = false;
}

void invokeDigitalPinHandleCommand(const uint8_t handler[], uint8_t value, uint8_t startCmdIndex, bool isAnalog,
                                   bool isConditionMatch) {
    uint8_t cmd = handler[startCmdIndex];            //4
    uint8_t handlePin = handler[startCmdIndex + 1];  //5
    uint8_t setValue = handler[startCmdIndex + 2];   //6
    switch (cmd) { // command
        case INVERT_PIN_N_SECONDS:
            if (isConditionMatch && settedUpValuesByHandler[handlePin] != 255) {
                settedUpValuesByHandler[handlePin] = 1;
                timer.everyOnceOne(handler[startCmdIndex + 2], invertPinTimerFunc, handlePin, getPinValue(handlePin));
                setPinValue(handlePin, getInvertedValue(handlePin));
            }
            break;
        case INVERT_PIN:
            if (isConditionMatch && settedUpValuesByHandler[handlePin] != 255) {
                setPinValue(handlePin, getInvertedValue(handlePin));
                settedUpValuesByHandler[handlePin] = 1;
            } else if (settedUpValuesByHandler[handlePin] == 1) {
                settedUpValuesByHandler[handlePin] = 255;
                setPinValue(handlePin, getInvertedValue(handlePin));
            }
            break;
        case SET_PIN_N_SECONDS: // uint8_t payload3[] = {CMDID, 3, 123, 4, SET_PIN_N_SECONDS, 9, 1, 2}; // (pin, value, interval)
            if (isConditionMatch && settedUpValuesByHandler[handlePin] == 255) {
                setPinValue(handlePin, setValue);
                settedUpValuesByHandler[handlePin] = 1;
                timer.everyOnceOne(handler[startCmdIndex + 3], invertPinTimerFunc, handlePin,getPinValue(handlePin));
            }
            break;
        case SET_PIN:
            if (isConditionMatch && settedUpValuesByHandler[handlePin] != 255) {
                setPinValue(handlePin, setValue);
                settedUpValuesByHandler[handlePin] = 1;
            } else if (settedUpValuesByHandler[handlePin] == 1) {
                settedUpValuesByHandler[handlePin] = 255;
                setPinValue(handlePin, 0);
            }
            break;
        // TODO: not works WELL
        case PLAY_TONE:
            if (isConditionMatch && settedUpValuesByHandler[handlePin] == 255) {
                unsigned long duration = (handler[startCmdIndex + 3] * 0.5 + 0.5) * 1000;
                int frequency = handlePin * 80 + 80;
                INFO_MANY(handlePin, frequency);
                tone(handlePin, frequency, duration);
                settedUpValuesByHandler[handlePin] = 1;
            } else if (settedUpValuesByHandler[handlePin] == 1) {
                noTone(handlePin);
                if(!isConditionMatch) settedUpValuesByHandler[handlePin] = 255;
            }
        case REQUEST_PIN:
            if (isConditionMatch) {
              if(settedUpValuesByHandler[handlePin] == 255) {
                  int interval = setValue == 0 ? 1 : setValue * 10;
                  uint8_t handlerID = handler[startCmdIndex + 3]; // 7
                  uint8_t pinRequestType = handler[startCmdIndex + 3]; // 7

                  requestPinFunc(0, handlePin, handlerID, pinRequestType, -1);
                  INFO_MANY(interval, handlerID);
                  uint8_t timerID = timer.everyTwo(interval, requestPinFunc, handlePin, handlerID, pinRequestType);

                  settedUpValuesByHandler[handlePin] = timerID;
              }
            } else {
                if(settedUpValuesByHandler[handlePin] != 255) {
                    INFO(settedUpValuesByHandler[handlePin]);
                    timer.cancel(settedUpValuesByHandler[handlePin]); // cancel timer with index from array
                    settedUpValuesByHandler[handlePin] = 255;
                }
            }
            break;
        default:
            ERROR(-1);
    }
}

// 0 - >D, 1 - <D, 2 - =D, 3 - >A, 4 - <A, 5 - =A
void HandleFunc_WhenValueOpValue(uint8_t handler[]) {
    // when pin '3' op '>' than value 243 - set pin 13 as HIGH
    // existedHandler - HANDLER_TYPE, {3, 243, 0, SET_PIN, 13, 29};
    uint8_t pinID = handler[1];
    uint8_t val = handler[2];
    uint8_t op = handler[3];
    bool isAnalog = val > 2;
    uint8_t pinValue = isAnalog ? map(analogRead(pinID), 0, 1023, 0, 255) : digitalRead(pinID);
    bool match = (op % 3 == 0 && pinValue > val) ||
                 (op % 3 == 1 && pinValue < val) ||
                 (op % 3 == 2 && pinValue == val);
    PRINTF("\nfgww:pin_id%d. op:%d. val:%d. actval:%d. match:%d", pinID, op, val, pinValue, match);
    invokeDigitalPinHandleCommand(handler, pinValue, 4, isAnalog, match);
}

typedef void (*HandlerFunc)(uint8_t *handler);

HandlerFunc HandlerFunctions[1] = {&HandleFunc_WhenValueOpValue};

void handlePins() {
    int index = 0;
    uint8_t sizeOfHandler = 0;
    uint8_t *existedHandler = getNextHandler(index, sizeOfHandler);
    while (existedHandler != nullptr) {
        HandlerFunctions[existedHandler[0] - HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN](existedHandler);
        index += sizeOfHandler;
        existedHandler = getNextHandler(index, sizeOfHandler);
    }
}

void loop() {
    registerDevice();

    // try send pin values
    handlePins();

    while (readMessageNSeconds(1)) {
        executeCommand(255);
        clearPayload();
    }

    // not sure it's best implementation
    timer.update();
}

float readFloat() {
    copy(getPayload(), floatBuf, getCurrentIndex(), 0, 6);
    float value = atof(floatBuf);
    addToCurrentIndex(6);
    return value;
}

void writeFloat(float value) {
    dtostrf(value, 6, 2, floatBuf);
    copy(floatBuf, getPayload(), 0, getCurrentIndex() + OFFSET_WRITE_INDEX, 8);
    addToCurrentIndex(8);
}

void printFloat(float value) {
   dtostrf(value, 6, 2, floatBuf);
   PRINTF("\nVal::%s", floatBuf);
}

void printPayload(uint8_t length) {
  printBuffer(OFFSET_WRITE_INDEX + length, getUPayload());
}

void printBuffer(uint8_t length, uint8_t bufferPtr[]) {
     PRINTF("\nPL. [");
     for (uint8_t i = 0; i < length; i++) {
         PRINTF("%d, ", bufferPtr[i]);
     }
     PRINTF("]");
}