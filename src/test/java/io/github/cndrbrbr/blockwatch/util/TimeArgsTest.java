package io.github.cndrbrbr.blockwatch.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeArgsTest {

    @Test
    void parsesUnits() {
        assertEquals(30 * 60_000L, TimeArgs.parseDuration("30m").getAsLong());
        assertEquals(2 * 3_600_000L, TimeArgs.parseDuration("2h").getAsLong());
        assertEquals(7 * 86_400_000L, TimeArgs.parseDuration("7D").getAsLong());
        assertEquals(14 * 86_400_000L, TimeArgs.parseDuration("2w").getAsLong());
    }

    @Test
    void rejectsOtherText() {
        assertTrue(TimeArgs.parseDuration("5").isEmpty());
        assertTrue(TimeArgs.parseDuration("gefahr").isEmpty());
        assertTrue(TimeArgs.parseDuration("2x").isEmpty());
    }

    @Test
    void describesInGerman() {
        assertEquals("letzte 2 Stunden", TimeArgs.describe("2h"));
        assertEquals("letzte Woche", TimeArgs.describe("1w"));
    }
}
