package pw.gatchina.chia;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Sorts;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pw.gatchina.util.Metric;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class LogHandlerTask implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(LogHandlerTask.class);
    private final MongoDatabaseManager mongo;
    private final JsonConfig config;

    /* Log date format see
     * https://github.com/Chia-Network/chia-blockchain/blob/0ffbe1339cb2b44b7ce4fa4238b8d9c0b3dbfdec/chia/util/chia_logging.py#L14
     * Example from log: 2021-07-31T09:03:22.726
     */
    public static DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static String regExp = "(?<datetime>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+(?<service>.*?):\\s+(?<level>INFO|ERROR|WARNING|DEBUG|CRITICAL)\\s+(?<message>.*?)\\Z";
    public static Pattern pattern = Pattern.compile(regExp, Pattern.DOTALL);

    public LogHandlerTask(final @NotNull JsonConfig config, final @NotNull MongoDatabaseManager mongo) {
        this.mongo = mongo;
        this.config = config;
    }

    @Override
    public Void call() throws Exception {
        final var defaultHostname = InetAddress.getLocalHost().getHostName();
        final var hostname = System.getProperty("hostname", defaultHostname);

        final var logCollection = mongo.getLogsCollection();
        final var fileCollection = mongo.getFilesCollection();

        final var lastHandledFile = fileCollection.find()
                .filter(Filters.eq("hostname", hostname))
                .sort(Sorts.descending("lastModifiedTime"))
                .first();

        final var logsDir = System.getProperty("user.home") + "/.chia/mainnet/log/";

        Files.find(Paths.get(logsDir), 1, (path, basicFileAttributes) -> {
            final var filename = path.getFileName().toString();

            if (!filename.startsWith("debug.log.")) {
                return false;
            }

            if (lastHandledFile == null) {
                return true;
            }

            final var lastModifiedTime = basicFileAttributes.lastModifiedTime().toInstant().toEpochMilli();
            final var isHandledFile = lastHandledFile.lastModifiedTime.toEpochMilli() >= lastModifiedTime;

            if (isHandledFile) {
                logger.info("skip file {} is handled", filename);
                return false;
            }

            return true;
        }, FileVisitOption.FOLLOW_LINKS).forEach(logFile -> {
            logger.info("process log file {}", logFile);

            final var logEntries = new ArrayList<LogEntry>();

            try (final var br = new BufferedReader(new FileReader(logFile.toFile()))) {
                var lineCounter = 0;
                var matchesCounter = 0;
                String line;
                while ((line = br.readLine()) != null) {
                    lineCounter++;
                    final var matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        if (config.skipDebug && "debug".equalsIgnoreCase(matcher.group("level"))) {
                            continue;
                        }
                        final var logEntry = new LogEntry();
                        logEntry.hostname = hostname;
                        logEntry.datetime = LocalDateTime.parse(matcher.group("datetime"), formatter);
                        logEntry.level = matcher.group("level");
                        logEntry.message = matcher.group("message");

                        final var services = matcher.group("service").split(" ", 2);
                        logEntry.serviceName = services[0];
                        logEntry.serviceFullName = services[1];

                        logEntries.add(logEntry);

                        matchesCounter++;
                    } else {
                        logger.warn("no pattern matching for line {}", line);
                    }
                }

                logger.info("parsed log records {}", logEntries.size());

                if (logEntries.size() > 0) {
                    var metric = Metric.now();
                    var bulkWriteResult = bulkWrite(logCollection, logEntries);
                    logger.info("save {} documents to database required time {} seconds", bulkWriteResult.getInsertedCount(), metric.diffAsSeconds());
                }

                logger.info("lines count: {}", lineCounter);
                logger.info("matches count: {}", matchesCounter);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }

            try {
                final var attrs = Files.readAttributes(logFile, BasicFileAttributes.class);

                final var handledFile = new LogFile();
                handledFile.filename = logFile.getFileName().toString();
                handledFile.lastModifiedTime = attrs.lastModifiedTime().toInstant();
                handledFile.hostname = hostname;
                handledFile.lines = logEntries.size();

                fileCollection.insertOne(handledFile);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });

        return null;
    }

    public BulkWriteResult bulkWrite(final @NotNull MongoCollection<LogEntry> logCollection, final @NotNull List<LogEntry> logEntries) {
        final var insertOneModels = new ArrayList<InsertOneModel<LogEntry>>(logEntries.size());

        for (final var logEntry : logEntries) {
            insertOneModels.add(new InsertOneModel<>(logEntry));
        }

        return logCollection.bulkWrite(insertOneModels);
    }
}
