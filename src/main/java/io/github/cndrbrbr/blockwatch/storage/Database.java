package io.github.cndrbrbr.blockwatch.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite-Speicher (der Treiber steckt im Spigot-Server-Jar). Alle Methoden
 * sind synchronisiert und duerfen nur von Async-Tasks aufgerufen werden,
 * nie vom Server-Thread.
 */
public final class Database implements AutoCloseable {

    /** Zusammenfassung pro Akteur: Anzahl je Aktion. */
    public record ActorSummary(String actor, Map<Action, Integer> counts) {
        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int dangerous() {
            return counts.entrySet().stream().filter(e -> e.getKey().isDangerous())
                    .mapToInt(Map.Entry::getValue).sum();
        }
    }

    /** Anzahl je Aktion fuer eine Kombination aus Akteur und Blocktyp. */
    public record MaterialSummary(String actor, String material, Map<Action, Integer> counts) {
        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Suchbereich: Quader um einen Punkt, optional nur gefaehrliche Aktionen und ab einem Zeitpunkt. */
    public record Area(String world, int x, int y, int z, int radius) {
    }

    private final Connection connection;

    public Database(File file) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ts INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                        player TEXT,
                        actor TEXT NOT NULL,
                        action TEXT NOT NULL,
                        material TEXT NOT NULL,
                        danger INTEGER NOT NULL)""");
            statement.execute("CREATE INDEX IF NOT EXISTS events_pos ON events(world, x, z, y)");
            statement.execute("CREATE INDEX IF NOT EXISTS events_actor ON events(actor COLLATE NOCASE, ts)");
            statement.execute("CREATE INDEX IF NOT EXISTS events_danger ON events(danger, ts)");
            statement.execute("CREATE INDEX IF NOT EXISTS events_ts ON events(ts)");
        }
    }

    public synchronized void insert(List<LogEntry> entries) throws SQLException {
        if (entries.isEmpty()) {
            return;
        }
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO events(ts, world, x, y, z, player, actor, action, material, danger) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (LogEntry entry : entries) {
                insert.setLong(1, entry.timestamp());
                insert.setString(2, entry.world());
                insert.setInt(3, entry.x());
                insert.setInt(4, entry.y());
                insert.setInt(5, entry.z());
                insert.setString(6, entry.playerId() != null ? entry.playerId().toString() : null);
                insert.setString(7, entry.actor());
                insert.setString(8, entry.action().name());
                insert.setString(9, entry.material());
                insert.setInt(10, entry.action().isDangerous() ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Eintraege im Quader um {@code area}, neueste zuerst. */
    public synchronized List<LogEntry> near(Area area, long since, boolean dangerOnly, int limit) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM events WHERE " + areaClause()
                + " AND ts >= ?" + (dangerOnly ? " AND danger = 1" : "") + " ORDER BY ts DESC, id DESC LIMIT ?")) {
            int index = bindArea(query, area);
            query.setLong(index++, since);
            query.setInt(index, limit);
            return readEntries(query);
        }
    }

    /** Zusammenfassung je Akteur im Quader um {@code area}, meiste Aktionen zuerst. */
    public synchronized List<ActorSummary> summaryNear(Area area, long since, boolean dangerOnly) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT actor, action, COUNT(*) FROM events WHERE "
                + areaClause() + " AND ts >= ?" + (dangerOnly ? " AND danger = 1" : "") + " GROUP BY actor, action")) {
            int index = bindArea(query, area);
            query.setLong(index, since);
            return readSummaries(query);
        }
    }

    /** Zusammenfassung je Akteur und Blocktyp im Quader, sortiert nach Akteur (meiste zuerst), dann Anzahl. */
    public synchronized List<MaterialSummary> materialsNear(Area area, long since, boolean dangerOnly)
            throws SQLException {
        Map<String, Map<String, Map<Action, Integer>>> byActor = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT actor, material, action, COUNT(*) FROM events "
                + "WHERE " + areaClause() + " AND ts >= ?" + (dangerOnly ? " AND danger = 1" : "")
                + " GROUP BY actor, material, action")) {
            int index = bindArea(query, area);
            query.setLong(index, since);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    Action action = Action.fromName(result.getString(3)).orElse(null);
                    if (action != null) {
                        byActor.computeIfAbsent(result.getString(1), key -> new LinkedHashMap<>())
                                .computeIfAbsent(result.getString(2), key -> new LinkedHashMap<>())
                                .merge(action, result.getInt(4), Integer::sum);
                    }
                }
            }
        }
        List<MaterialSummary> summaries = new ArrayList<>();
        byActor.entrySet().stream()
                .sorted((a, b) -> Integer.compare(actorTotal(b.getValue()), actorTotal(a.getValue())))
                .forEach(actor -> actor.getValue().entrySet().stream()
                        .map(material -> new MaterialSummary(actor.getKey(), material.getKey(), material.getValue()))
                        .sorted((a, b) -> Integer.compare(b.total(), a.total()))
                        .forEach(summaries::add));
        return summaries;
    }

    private static int actorTotal(Map<String, Map<Action, Integer>> materials) {
        return materials.values().stream().flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
    }

    /** Alle Eintraege genau dieses Blocks, neueste zuerst. */
    public synchronized List<LogEntry> block(String world, int x, int y, int z, int limit) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM events WHERE world = ? AND x = ? AND z = ? AND y = ? ORDER BY ts DESC, id DESC LIMIT ?")) {
            query.setString(1, world);
            query.setInt(2, x);
            query.setInt(3, z);
            query.setInt(4, y);
            query.setInt(5, limit);
            return readEntries(query);
        }
    }

    /** Gefaehrliche Aktionen auf dem ganzen Server, neueste zuerst. */
    public synchronized List<LogEntry> dangerous(long since, int limit) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM events WHERE danger = 1 AND ts >= ? ORDER BY ts DESC, id DESC LIMIT ?")) {
            query.setLong(1, since);
            query.setInt(2, limit);
            return readEntries(query);
        }
    }

    /** Aktionen eines Akteurs (Spielername, ohne Gross-/Kleinschreibung), neueste zuerst. */
    public synchronized List<LogEntry> byActor(String actor, long since, boolean dangerOnly, int limit)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM events WHERE actor = ? COLLATE NOCASE AND ts >= ?"
                        + (dangerOnly ? " AND danger = 1" : "") + " ORDER BY ts DESC, id DESC LIMIT ?")) {
            query.setString(1, actor);
            query.setLong(2, since);
            query.setInt(3, limit);
            return readEntries(query);
        }
    }

    /** Zusammenfassung je Akteur auf dem ganzen Server (fuer /bw stats und /bw spieler). */
    public synchronized List<ActorSummary> summary(long since, String actorOrNull) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT actor, action, COUNT(*) FROM events WHERE ts >= ?"
                + (actorOrNull != null ? " AND actor = ? COLLATE NOCASE" : "") + " GROUP BY actor, action")) {
            query.setLong(1, since);
            if (actorOrNull != null) {
                query.setString(2, actorOrNull);
            }
            return readSummaries(query);
        }
    }

    /** Loescht Eintraege vor {@code before}; liefert die Anzahl. */
    public synchronized int purge(long before) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM events WHERE ts < ?")) {
            delete.setLong(1, before);
            return delete.executeUpdate();
        }
    }

    public synchronized long count() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM events")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private static String areaClause() {
        return "world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ?";
    }

    private static int bindArea(PreparedStatement statement, Area area) throws SQLException {
        statement.setString(1, area.world());
        statement.setInt(2, area.x() - area.radius());
        statement.setInt(3, area.x() + area.radius());
        statement.setInt(4, area.z() - area.radius());
        statement.setInt(5, area.z() + area.radius());
        statement.setInt(6, area.y() - area.radius());
        statement.setInt(7, area.y() + area.radius());
        return 8;
    }

    private static List<LogEntry> readEntries(PreparedStatement query) throws SQLException {
        List<LogEntry> entries = new ArrayList<>();
        try (ResultSet result = query.executeQuery()) {
            while (result.next()) {
                Action action = Action.fromName(result.getString("action")).orElse(null);
                if (action == null) {
                    continue;
                }
                String player = result.getString("player");
                entries.add(new LogEntry(result.getLong("ts"), result.getString("world"), result.getInt("x"),
                        result.getInt("y"), result.getInt("z"), player != null ? UUID.fromString(player) : null,
                        result.getString("actor"), action, result.getString("material")));
            }
        }
        return entries;
    }

    private static List<ActorSummary> readSummaries(PreparedStatement query) throws SQLException {
        Map<String, Map<Action, Integer>> byActor = new LinkedHashMap<>();
        try (ResultSet result = query.executeQuery()) {
            while (result.next()) {
                Action action = Action.fromName(result.getString(2)).orElse(null);
                if (action != null) {
                    byActor.computeIfAbsent(result.getString(1), key -> new LinkedHashMap<>())
                            .merge(action, result.getInt(3), Integer::sum);
                }
            }
        }
        List<ActorSummary> summaries = new ArrayList<>();
        byActor.forEach((actor, counts) -> summaries.add(new ActorSummary(actor, counts)));
        summaries.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return summaries;
    }
}
