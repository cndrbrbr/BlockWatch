package io.github.cndrbrbr.blockwatch.storage;

import java.util.Optional;

/** Protokollierte Aktionsarten; {@link #isDangerous} markiert TNT, Feuer, Lava und Explosionen. */
public enum Action {
    BREAK("abgebaut", false),
    PLACE("gesetzt", false),
    TNT_PLACE("TNT gesetzt", true),
    TNT_PRIME("TNT gezündet", true),
    TNT_MINECART("TNT-Lore gesetzt", true),
    END_CRYSTAL("Endkristall gesetzt", true),
    IGNITE("Feuer gelegt", true),
    LAVA("Lava ausgegossen", true),
    EXPLODE("gesprengt", true),
    BURN("verbrannt", true);

    private final String label;
    private final boolean dangerous;

    Action(String label, boolean dangerous) {
        this.label = label;
        this.dangerous = dangerous;
    }

    public String label() {
        return label;
    }

    public boolean isDangerous() {
        return dangerous;
    }

    public static Optional<Action> fromName(String name) {
        try {
            return Optional.of(valueOf(name));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
