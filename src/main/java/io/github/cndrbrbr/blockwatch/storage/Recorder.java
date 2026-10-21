package io.github.cndrbrbr.blockwatch.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Sammelt Eintraege vom Server-Thread und schreibt sie gebuendelt in die
 * Datenbank ({@link #flush} laeuft periodisch asynchron und beim Abschalten).
 */
public final class Recorder {

    private final Database database;
    private final Logger logger;
    private final ConcurrentLinkedQueue<LogEntry> queue = new ConcurrentLinkedQueue<>();

    public Recorder(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void record(LogEntry entry) {
        queue.add(entry);
    }

    public synchronized void flush() {
        List<LogEntry> batch = new ArrayList<>();
        LogEntry entry;
        while ((entry = queue.poll()) != null) {
            batch.add(entry);
        }
        try {
            database.insert(batch);
        } catch (SQLException exception) {
            logger.warning("Konnte " + batch.size() + " Einträge nicht speichern: " + exception.getMessage());
        }
    }
}
