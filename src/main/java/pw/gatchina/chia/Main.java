package pw.gatchina.chia;

import com.mongodb.client.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pw.gatchina.util.ConfigHelper;
import pw.gatchina.util.CronScheduler;
import pw.gatchina.util.StaticShutdownCallbackRegistry;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static {
        System.setProperty("log4j.shutdownCallbackRegistry", StaticShutdownCallbackRegistry.class.getCanonicalName());
    }

    public static void createCollections(final @NotNull MongoDatabaseManager mongo) {
        if (mongo.collectionNotExists(MongoDatabaseManager.collectionLogName)) {
            final var sizeInBytes = (long) Math.pow(1024, 3) * 64; // 1GB * 64
            final var opts = new CreateCollectionOptions().sizeInBytes(sizeInBytes).capped(true);
            mongo.getDatabase().createCollection(MongoDatabaseManager.collectionLogName, opts);
        }

        final var logCollection = mongo.getLogsCollection();
        logCollection.createIndex(Indexes.ascending("hostname"), new IndexOptions().background(true));
        logCollection.createIndex(Indexes.descending("datetime"), new IndexOptions().background(true));
        logCollection.createIndex(Indexes.ascending("level"), new IndexOptions().background(true));

        final var fileCollection = mongo.getFilesCollection();
        fileCollection.createIndex(Indexes.descending("lastModifiedTime"));
        fileCollection.createIndex(Indexes.ascending("hostname"));
    }

    public static void main(String[] args) throws Exception {
        final var config = ConfigHelper.saveAndLoad("config.json", JsonConfig.class);
        final var mongo = new MongoDatabaseManager(config.mongo.connection);

        createCollections(mongo);

        final var cron = new CronScheduler();

        cron.start("* * * * *", new LogHandlerTask(config, mongo));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cron.shutdown();
            mongo.close();
        }));
    }
}