package io.github.cndrbrbr.blockwatch.storage;

import java.util.UUID;

/**
 * Ein Protokolleintrag. {@code playerId} ist null, wenn kein Spieler
 * verantwortlich war (z. B. Creeper, Feuerausbreitung); {@code actor} ist
 * dann eine Beschreibung wie "#Creeper".
 */
public record LogEntry(long timestamp, String world, int x, int y, int z, UUID playerId, String actor,
                       Action action, String material) {
}
