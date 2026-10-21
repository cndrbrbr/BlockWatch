package io.github.cndrbrbr.blockwatch.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseTest {

    private static final UUID ALEX = UUID.randomUUID();

    @TempDir
    Path dir;
    private Database database;

    @BeforeEach
    void open() throws Exception {
        database = new Database(dir.resolve("test.db").toFile());
        database.insert(List.of(
                entry(1000, 0, 64, 0, ALEX, "Alex", Action.BREAK, "STONE"),
                entry(2000, 1, 64, 1, ALEX, "Alex", Action.TNT_PRIME, "TNT"),
                entry(3000, 2, 63, 0, null, "#TNT", Action.EXPLODE, "DIRT"),
                entry(4000, 100, 64, 100, ALEX, "Alex", Action.PLACE, "OAK_PLANKS"),
                entry(5000, 0, 64, 0, ALEX, "Alex", Action.PLACE, "GLASS")));
    }

    @AfterEach
    void close() throws Exception {
        database.close();
    }

    @Test
    void materialsGroupsByActorAndMaterialWithLastTime() throws Exception {
        List<Database.MaterialSummary> rows = database.materials(new Database.Area("world", 0, 64, 0, 5), null, 0, false);
        assertEquals(4, rows.size());
        assertEquals("Alex", rows.get(0).actor());
        assertEquals("#TNT", rows.get(3).actor());
        assertEquals("DIRT", rows.get(3).material());
        assertEquals(3000L, rows.get(3).lastTimestamp());
    }

    @Test
    void materialsCanFilterByActorAndDanger() throws Exception {
        List<Database.MaterialSummary> rows = database.materials(null, "alex", 0, false);
        assertEquals(4, rows.size());
        List<Database.MaterialSummary> danger = database.materials(null, null, 0, true);
        assertEquals(2, danger.size());
        assertEquals(2, database.materials(new Database.Area("world", 0, 64, 0, 0), null, 0, false).size());
    }

    @Test
    void summaryCountsPerActor() throws Exception {
        List<Database.ActorSummary> summary = database.summary(0, null);
        assertEquals("Alex", summary.get(0).actor());
        assertEquals(4, summary.get(0).total());
        assertEquals(1, summary.get(0).dangerous());
    }

    @Test
    void purgeRemovesOldEntries() throws Exception {
        assertEquals(2, database.purge(2500));
        assertEquals(3, database.count());
    }

    private static LogEntry entry(long ts, int x, int y, int z, UUID player, String actor, Action action,
                                  String material) {
        return new LogEntry(ts, "world", x, y, z, player, actor, action, material);
    }
}
