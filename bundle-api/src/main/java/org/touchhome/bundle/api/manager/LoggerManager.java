package org.touchhome.bundle.api.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import static org.touchhome.bundle.api.util.TouchHomeUtils.resolvePath;

@Log4j2
@Controller
@RequiredArgsConstructor
public class LoggerManager {

    private static Path logsDir = resolvePath("logs");

    public final EntityContext entityContext;

    private static String escapeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static int countLines(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        }
    }

    public void postConstruct() {
        try {
            Files.walkFileTree(logsDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".lck")) {
                        try {
                            Files.delete(file);
                        } catch (Exception ex) {
                            log.error("Can't delete lock file: <{}>", TouchHomeUtils.getErrorMessage(ex), ex);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public boolean notExists(String logFile) {
        return StringUtils.isEmpty(logFile) || Files.notExists(Paths.get(escapeName(logFile)));
    }

    public Path getOrCreateLogFile(String key, BaseEntity entity, boolean allowCreate) {
        BaseEntity targetEntity = entityContext.getEntity(entity);
        return getOrCreateLogFile(targetEntity.getType(), "log_" + escapeName(key), allowCreate);
    }

    private Path getOrCreateLogFile(String group, String fileName, boolean allowCreate) {
        try {
            Path logGroup = logsDir.resolve(group);
            if (!Files.exists(logGroup)) {
                Files.createDirectory(logGroup);
            }
            Path resolve = logGroup.resolve(fileName + ".log");
            if (Files.notExists(resolve)) {
                return Files.createFile(resolve);
            }
            return resolve;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Logger getLogger(String group, String fileName) {
        Path logFile = getOrCreateLogFile(group, fileName, true);
        try {
            PrintStream logOutputStream = new PrintStream(logFile.toFile());
            return getLogger(logOutputStream);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // scriptEntity.getBackgroundProcessServiceID()
    public Logger getLogger(String group, BaseEntity scriptEntity) { // , String group, String name
        try {
            Path logFile = getOrCreateLogFile(group, scriptEntity, true);
            log.info("Requested log file <{}>", logFile);
            PrintStream logOutputStream = new PrintStream(logFile.toFile());
            return getLogger(logOutputStream);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Logger getLogger(PrintStream printStream) {
        return new InternalLogger(true, printStream);
    }

    public int getLogFileLineCount(String key, BaseEntity scriptEntity) throws IOException {
        return countLines(getOrCreateLogFile(key, scriptEntity, true));
    }

    public void clearLogs(String key, BaseEntity scriptEntity) throws IOException {
        Path path = getOrCreateLogFile(key, scriptEntity, true);
        Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
    }

    public List<String> getLogFileContent(String key, Integer fromLine, BaseEntity scriptEntity) {
        Path logFile = getOrCreateLogFile(key, scriptEntity, true);
        List<String> result = new ArrayList<>();
        try {
            try (BufferedReader br = Files.newBufferedReader(logFile, Charset.defaultCharset())) {
                String line;
                int index = 0;
                while ((line = br.readLine()) != null) {
                    if (index >= fromLine) {
                        result.add(line);
                    }
                    index++;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    private static class InternalLogger extends SimpleLogger {

        private static final char SPACE = ' ';
        private final SimpleDateFormat dateFormatter;
        private Map<String, Consumer<String>> consumers = new HashMap<>();
        private int counter = 0;
        private boolean showDateTime;
        private PrintStream stream;

        InternalLogger(boolean showDateTime, PrintStream stream) {
            super("OutputStreamLogger", Level.DEBUG, false, false, showDateTime, false,
                    Strings.EMPTY, ParameterizedNoReferenceMessageFactory.INSTANCE, new PropertiesUtil(new Properties()), stream);
            this.showDateTime = showDateTime;
            if (showDateTime) {
                this.dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS zzz");
            } else {
                this.dateFormatter = null;
            }
            this.stream = stream;
        }

        @Override
        public void logMessage(String fqcn, Level mgsLevel, Marker marker, Message msg, Throwable throwable) {
            final StringBuilder sb = new StringBuilder();
            // Append date-time if so configured
            if (showDateTime) {
                final Date now = new Date();
                String dateText;
                synchronized (dateFormatter) {
                    dateText = dateFormatter.format(now);
                }
                sb.append(dateText);
                sb.append(SPACE);
            }

            sb.append(mgsLevel.toString());
            sb.append(SPACE);
            sb.append(msg.getFormattedMessage());

            final Object[] params = msg.getParameters();
            Throwable t;
            if (throwable == null && params != null && params.length > 0
                    && params[params.length - 1] instanceof Throwable) {
                t = (Throwable) params[params.length - 1];
            } else {
                t = throwable;
            }
            stream.println(sb.toString());
            if (t != null) {
                stream.print(SPACE);
                t.printStackTrace(stream);
            }

            counter++;
            for (Consumer<String> consumer : consumers.values()) {
                consumer.accept(sb.toString());
            }
        }

        public boolean addNotifier(String name, Consumer<String> consumer) {
            return consumers.put(name, consumer) == null;
        }

        public void removeNotifier(String name) {
            consumers.remove(name);
        }

        public int getNotifyCounter() {
            return counter;
        }

        public List<String> getOldNotifications(Integer toLine) {
            try {
                OutputStream outputStream = (OutputStream) FieldUtils.readField(stream, "out", true);
                if (outputStream instanceof FileOutputStream) {
                    String path = (String) FieldUtils.readField(outputStream, "path", true);

                    ArrayList<String> array = new ArrayList<>();
                    Scanner input = new Scanner(new File(path));
                    while (input.hasNextLine() && toLine-- >= 0) {
                        array.add(input.nextLine());
                    }
                    return array;
                }
            } catch (Exception ignore) {
                log.warn("Unable to fetch old logs from stream <{}>", stream.getClass().getSimpleName());
            }
            return Collections.emptyList();
        }
    }
}
