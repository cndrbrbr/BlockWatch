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
    void nearFindsOnlyEntriesInsideTheCubeNewestFirst() throws Exception {
        List<LogEntry> near = database.near(new Database.Area("world", 0, 64, 0, 5), 0, false, 100);
        assertEquals(List.of(5000L, 3000L, 2000L, 1000L), near.stream().map(LogEntry::timestamp).toList());
    }

    @Test
    void nearCanFilterDangerAndTime() throws Exception {
        Database.Area area = new Database.Area("world", 0, 64, 0, 5);
        assertEquals(2, database.near(area, 0, true, 100).size());
        assertEquals(1, database.near(area, 2500, true, 100).size());
    }

    @Test
    void summaryCountsPerActorAndAction() throws Exception {
        List<Database.ActorSummary> summary = database.summaryNear(new Database.Area("world", 0, 64, 0, 5), 0, false);
        assertEquals("Alex", summary.get(0).actor());
        assertEquals(3, summary.get(0).total());
        assertEquals(1, summary.get(0).dangerous());
        assertEquals(1, summary.get(1).total());
    }

    @Test
    void materialsNearGroupsByActorAndMaterial() throws Exception {
        List<Database.MaterialSummary> rows = database.materialsNear(new Database.Area("world", 0, 64, 0, 5), 0, false);
        assertEquals(4, rows.size());
        assertEquals("Alex", rows.get(0).actor());
        assertEquals("#TNT", rows.get(3).actor());
        assertEquals("DIRT", rows.get(3).material());
    }

        @Test
    void blockHistoryIsExactPosition() throws Exception {
        assertEquals(2, database.block("world", 0, 64, 0, 100).size());
        assertEquals(0, database.block("world", 0, 65, 0, 100).size());
    }

    @Test
    void byActorIgnoresCase() throws Exception {
        assertEquals(4, database.byActor("alex", 0, false, 100).size());
        assertEquals(1, database.byActor("ALEX", 0, true, 100).size());
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
