package org.touchhome.bundle.rf433;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.rf433.dto.Rf433JSON;
import org.touchhome.bundle.rf433.model.RF433SignalEntity;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.IntStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Log4j2
@Component
@RequiredArgsConstructor
public class Rf433Service implements BundleContext {
    private static Path rf433Dir = TouchHomeUtils.resolvePath("rf433");
    private final EntityContext entityContext;
    private Path rf433TransmitterPy;
    private Path rf433ReceiverPy;
    private boolean isTestApplication = false;

    public void init() {
        if (EntityContext.isTestEnvironment()) {
            return;
        }
        try {
            FileUtils.cleanDirectory(rf433Dir.toFile());
            rf433TransmitterPy = rf433Dir.resolve("rf433Transmitter.py");
            Files.copy(TouchHomeUtils.getFilesPath().resolve("rf433").resolve("rf433Transmitter.py"), rf433TransmitterPy, REPLACE_EXISTING);

            rf433ReceiverPy = rf433Dir.resolve("rf433Sniffer.py");
            Files.copy(TouchHomeUtils.getFilesPath().resolve("rf433").resolve("rf433Sniffer.py"), rf433ReceiverPy, REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getBundleId() {
        return "rf433";
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    public RF433Signal readSignal(int rf433ReceiverPinAddress, Rf433JSON rf433JSON) throws IOException, InterruptedException {
        RF433Signal signal = new RF433Signal();
        if (!isTestApplication) {
            ProcessBuilder processBuilder = new ProcessBuilder(Arrays.asList(
                    "python",
                    rf433ReceiverPy.toString(),
                    "-d" + rf433JSON.getMaxDuration(),
                    "-p" + rf433ReceiverPinAddress,
                    rf433Dir.resolve("Rf433SnifferResult").toAbsolutePath().toString()
            ));
            Process process = processBuilder.start();
            int statusCode = process.waitFor();
            log.info("Rf433 sniffer finished with code: " + statusCode);
            if (statusCode != 0) {
                throw new RuntimeException(String.join(",", IOUtils.readLines(process.getErrorStream())));
            }

            // read times
            FileChannel fileChannel = new RandomAccessFile(rf433Dir.resolve("Rf433SnifferResult.times").toFile(), "r").getChannel();
            ByteBuffer mBuf = ByteBuffer.allocate((int) fileChannel.size());
            fileChannel.read(mBuf);
            fileChannel.close();
            mBuf.flip();
            signal.times = new Float[mBuf.capacity() / 4];
            int i = 0;
            while (mBuf.remaining() > 0) {
                signal.times[i++] = mBuf.getFloat();
            }

            signal.values = new Integer[signal.times.length];
            i = 0;
            for (byte value : Files.readAllBytes(rf433Dir.resolve("Rf433SnifferResult.values"))) {
                signal.values[i++] = value == '1' ? 1 : 0;
            }
        } else {
            signal.times = TouchHomeUtils.readFile("test/RECEIVED_SIGNAL_0_CONVERTED")
                    .stream()
                    .map(line -> {
                        BigDecimal value = new BigDecimal(line);
                        value = value.setScale(rf433JSON.getSignalAccuracy(), RoundingMode.HALF_EVEN);
                        return value.floatValue();
                    }).toArray(Float[]::new);
            signal.values = TouchHomeUtils.readFile("test/RECEIVED_SIGNAL_1")
                    .stream()
                    .map(v -> v.equals("1") ? 1 : 0).toArray(Integer[]::new);
        }
        return signal;
    }

    public List<String> buildAndRunScriptFromSignal(RF433Signal signal, Rf433JSON rf433JSON, int rf433Pin) throws IOException, InterruptedException {
        Path testWavePath = rf433Dir.resolve("testWave.getPinRequestType");
        signal.joinSameImpulses();
        ByteBuffer byteBuffer = ByteBuffer.allocate(signal.times.length * 4);
        float[] hh = new float[signal.times.length / 2];
        for (int i = (signal.values[0] == 0 ? 2 : 0); i < signal.times.length; i += 2) {
            byteBuffer.putFloat(signal.times[i + 1] - signal.times[i]);
            hh[i == 0 ? 0 : i / 2] = signal.times[i + 1] - signal.times[i];
        }
        Files.write(testWavePath, byteBuffer.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ProcessBuilder processBuilder = new ProcessBuilder(Arrays.asList(
                "python",
                rf433TransmitterPy.toString(),
                "-c" + rf433JSON.getRepeats(),
                "-d" + rf433JSON.getMaxDuration(),
                "-p" + rf433Pin,
                testWavePath.toAbsolutePath().toString()
        ));
        Process process = processBuilder.start();
        int statusCode = process.waitFor();
        log.info("Process finished with code: " + statusCode);
        return IOUtils.readLines(statusCode == 0 ? process.getInputStream() : process.getErrorStream());
    }

    public RF433SignalEntity save() throws IOException {
        RF433SignalEntity entity = new RF433SignalEntity();
        Path path = rf433Dir.resolve("testWave.getPinRequestType");
        Path entityPath = rf433Dir.resolve(entity.getEntityID());
        entity.setPath(entityPath.toString());
        Files.move(path, entityPath, REPLACE_EXISTING);

        return entityContext.save(entity);
    }

    public static class RF433Signal implements Serializable {
        static final int ZERO_LEADING = 50;
        private Float[] times;
        private Integer[] values;
        private boolean trimmed;

        public static void main(String[] args) {
            RF433Signal signal = new RF433Signal();
            signal.values = new Integer[]{1, 1, 1, 1, 1, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1};
            signal.times = new Float[]{0F, 1F, 2F, 3F, 4F, 5F, 6F, 7F, 8F, 9F, 10F, 11F, 12F, 13F, 14F, 15F, 16F, 17F, 18F};

            signal.findDuplicates(3);
        }

        public Float[] getTimes() {
            return times;
        }

        public Integer[] getValues() {
            return values;
        }

        public void omitDuplicateTime() {
            assertSize();

            Float prevTime = -1F;
            int newIndex = 0;
            for (int i = 0; i < times.length; i++) {
                Float curTime = times[i];
                if (!curTime.equals(prevTime)) {
                    prevTime = curTime;
                    times[newIndex] = curTime;
                    values[newIndex] = values[i];
                    newIndex++;
                }
            }
            this.times = Arrays.copyOf(this.times, newIndex);
            this.values = Arrays.copyOf(this.values, newIndex);
        }

        public void trimTime() {
            if (!trimmed) {
                trimmed = true;
                assertSize();
                int first = 0;
                int last = times.length - 1;
                while (values[first] == 0 && first < values.length - 1) {
                    first++;
                }
                if (first == last) { // empty data
                    return;
                }
                while (values[last] == 0 && last > 0) {
                    last--;
                }

                this.times = Arrays.copyOfRange(this.times, first, last);
                this.values = Arrays.copyOfRange(this.values, first, last);
            }
        }

        public void ignoreNoise(Integer ignoreNoiseCount) {
            assertSize();
            while (ignoreNoiseInternal(ignoreNoiseCount)) {

            }
        }

        public void joinSameImpulses() {
            assertSize();
            Float[] localTimes = new Float[times.length * 2];
            Integer[] localValues = new Integer[values.length * 2];

            int resultIndex = 0;
            for (int i = 0; i < times.length; i++) {
                int value = values[i];
                int iValue = i; // first occurrence
                while (i < values.length - 1 && values[i + 1] == value) {
                    i++;
                }
                localValues[resultIndex] = localValues[resultIndex + 1] = value;
                localTimes[resultIndex] = times[iValue];
                localTimes[resultIndex + 1] = times[i];
                resultIndex += 2;
            }
            this.times = Arrays.copyOf(localTimes, resultIndex);
            this.values = Arrays.copyOf(localValues, resultIndex);
        }

        public void zoom(List<Rf433JSON.ZoomItem> zoom) {
            int from = 0;
            int to = times.length;
            for (Rf433JSON.ZoomItem item : zoom) {
                int tempFrom = from;
                from = (int) (tempFrom + ((to - tempFrom) * item.getFrom() / 100));
                to = (int) (tempFrom + ((to - tempFrom) * item.getTo() / 100));
            }
            this.times = Arrays.copyOfRange(this.times, from, to);
            this.values = Arrays.copyOfRange(this.values, from, to);
        }

        public Signal[] findDuplicates(float maxTime) {
            List<List<RF433Signal>> signals = new ArrayList<>();
            int fromIndex;
            Set<Integer> blockedIndexes = new HashSet<>();
            Pair<Integer, Integer> firstBucket = findTimeBucketStartFrom(0, maxTime, blockedIndexes);

            Signal[] signals1 = new Signal[times.length];
            for (int i = 0; i < times.length; i++) {
                signals1[i] = new Signal(times[i], values[i]);
            }
            int grouId = 0;


            while (firstBucket != null) {
                List<RF433Signal> sliceSignals = null;

                // try find second bucket that fit to first bucket
                fromIndex = firstBucket.getValue() + 1;

                Pair<Integer, Integer> secondBucket = findTimeBucketStartFrom(fromIndex, maxTime, blockedIndexes); // second bucket stars from end of first bucket
                while (secondBucket != null) { // fromIndex < times.length
                    if (compareTimeBuckets(firstBucket, secondBucket)) {
                        if (sliceSignals == null) {
                            grouId++;
                            sliceSignals = new ArrayList<>();
                            sliceSignals.add(sliceOf(firstBucket.getKey(), firstBucket.getValue()));
                            signals.add(sliceSignals);
                            addToSignals(firstBucket, signals1, grouId);
                        }
                        sliceSignals.add(sliceOf(secondBucket.getKey(), secondBucket.getValue()));
                        addToSignals(secondBucket, signals1, grouId);
                        // add indexes to blockedSet
                        IntStream.rangeClosed(firstBucket.getKey(), firstBucket.getValue()).forEach(blockedIndexes::add);
                        IntStream.rangeClosed(secondBucket.getKey(), secondBucket.getValue()).forEach(blockedIndexes::add);
                        // now first index is last index of second bucket + 1
                        fromIndex = secondBucket.getValue() + 1;
                        while (blockedIndexes.contains(fromIndex)) {
                            fromIndex++;
                        }
                    } else {
                        // try figure out next index to find
                        fromIndex = secondBucket.getValue() - fromIndex > maxTime ? secondBucket.getValue() + 1 : fromIndex + 1;
                        while (blockedIndexes.contains(fromIndex)) {
                            fromIndex++;
                        }
                    }
                    secondBucket = findTimeBucketStartFrom(fromIndex, maxTime, blockedIndexes);
                }
                firstBucket = findTimeBucketStartFrom(firstBucket.getValue() + 1, maxTime, blockedIndexes);
            }

            return signals1;
        }

        @SuppressWarnings("Duplicates")
        public List<GroupSignal> findDuplicates2(float maxTime) {
            List<List<RF433Signal>> signals = new ArrayList<>();
            List<GroupSignal> groupSignals = new ArrayList<>();
            int fromIndex;
            Set<Integer> blockedIndexes = new HashSet<>();
            Pair<Integer, Integer> firstBucket = findTimeBucketStartFrom(0, maxTime, blockedIndexes);

            Signal[] signals1 = new Signal[times.length];
            for (int i = 0; i < times.length; i++) {
                signals1[i] = new Signal(times[i], values[i]);
            }
            int grouId = 0;


            while (firstBucket != null) {
                List<RF433Signal> sliceSignals = null;

                // try find second bucket that fit to first bucket
                fromIndex = firstBucket.getValue() + 1;

                Pair<Integer, Integer> secondBucket = findTimeBucketStartFrom(fromIndex, maxTime, blockedIndexes); // second bucket stars from end of first bucket
                while (secondBucket != null) { // fromIndex < times.length
                    if (compareTimeBuckets(firstBucket, secondBucket)) {
                        if (sliceSignals == null) {
                            grouId++;
                            sliceSignals = new ArrayList<>();
                            sliceSignals.add(sliceOf(firstBucket.getKey(), firstBucket.getValue()));
                            groupSignals.add(new GroupSignal(firstBucket.getKey(), firstBucket.getValue(), grouId));
                            signals.add(sliceSignals);
                            addToSignals(firstBucket, signals1, grouId);
                        }
                        sliceSignals.add(sliceOf(secondBucket.getKey(), secondBucket.getValue()));
                        groupSignals.add(new GroupSignal(secondBucket.getKey(), secondBucket.getValue(), grouId));

                        addToSignals(secondBucket, signals1, grouId);
                        // add indexes to blockedSet
                        IntStream.rangeClosed(firstBucket.getKey(), firstBucket.getValue()).forEach(blockedIndexes::add);
                        IntStream.rangeClosed(secondBucket.getKey(), secondBucket.getValue()).forEach(blockedIndexes::add);
                        // now first index is last index of second bucket + 1
                        fromIndex = secondBucket.getValue() + 1;
                        while (blockedIndexes.contains(fromIndex)) {
                            fromIndex++;
                        }
                    } else {
                        // try figure out next index to find
                        fromIndex = secondBucket.getValue() - fromIndex > maxTime ? secondBucket.getValue() + 1 : fromIndex + 1;
                        while (blockedIndexes.contains(fromIndex)) {
                            fromIndex++;
                        }
                    }
                    secondBucket = findTimeBucketStartFrom(fromIndex, maxTime, blockedIndexes);
                }
                firstBucket = findTimeBucketStartFrom(firstBucket.getValue() + 1, maxTime, blockedIndexes);
            }

            return groupSignals;
        }

        private void addToSignals(Pair<Integer, Integer> bucket, Signal[] signals, int grouId) {
            for (int i = bucket.getKey(); i <= bucket.getValue(); i++) {
                signals[i].groupId = grouId;
            }
        }

        private RF433Signal sliceOf(Integer key, Integer value) {
            RF433Signal rf433Signal = new RF433Signal();
            rf433Signal.times = Arrays.copyOfRange(this.times, key, value + 1);
            rf433Signal.values = Arrays.copyOfRange(this.values, key, value + 1);
            return rf433Signal;
        }

        private boolean compareTimeBuckets(Pair<Integer, Integer> firstBucket, Pair<Integer, Integer> secondBucket) {
            for (int i = 0; i <= firstBucket.getValue() - firstBucket.getKey(); i++) {
                if (!Objects.equals(this.values[firstBucket.getKey() + i], this.values[secondBucket.getKey() + i])) {
                    return false;
                }
            }
            return true;
        }

        private Pair<Integer, Integer> findTimeBucketStartFrom(int fromIndex, float maxTime, Set<Integer> blockedIndexes) {
            int toIndex = fromIndex;
            if (toIndex >= times.length) {
                return null;
            }
            while (times[toIndex] - times[fromIndex] < maxTime) {
                toIndex++;
                if (blockedIndexes.contains(toIndex)) {
                    while (blockedIndexes.contains(toIndex)) {
                        toIndex++;
                    }
                    return findTimeBucketStartFrom(toIndex, maxTime, blockedIndexes);
                }
                if (toIndex >= times.length) {
                    return null;
                }
            }
            return Pair.of(fromIndex, toIndex);
        }

        private boolean ignoreNoiseInternal(Integer ignoreNoiseCount) {
            int prefixLow = 0;
            while (values[prefixLow] == 0 && prefixLow < values.length - 1) {
                prefixLow++;
            }
            int hight = prefixLow;
            while (values[hight] == 1 && hight < values.length - 1) {
                hight++;
            }
            if (hight <= ignoreNoiseCount) {
                int suffixLow = hight;
                while (values[suffixLow] == 0 && suffixLow < values.length - 1) {
                    suffixLow++;
                }

                if (prefixLow + suffixLow > ZERO_LEADING) {
                    for (int i = prefixLow; i < hight; i++) {
                        values[i] = 0;
                    }
                    return true;
                }
            }
            return false;
        }

        private void assertSize() {
            if (times.length != values.length) {
                throw new RuntimeException(String.format("Times length %d must be same as values length %d", times.length, values.length));
            }
        }

        public static class Signal {
            public Float time;
            public Integer value;
            public int groupId = 0;

            Signal(Float time, Integer value) {
                this.time = time;
                this.value = value;
            }
        }

        public static class GroupSignal {
            public int from;
            public int to;
            public int groupId;

            public GroupSignal(int from, int to, int groupId) {
                this.from = from;
                this.to = to;
                this.groupId = groupId;
            }
        }
    }
}
