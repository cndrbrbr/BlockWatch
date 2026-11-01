package io.github.cndrbrbr.blockwatch.listener;

import io.github.cndrbrbr.blockwatch.storage.Action;
import io.github.cndrbrbr.blockwatch.storage.LogEntry;
import io.github.cndrbrbr.blockwatch.storage.Recorder;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.List;

/**
 * Erfasst Blockaenderungen und gefaehrliche Aktionen. Alle Handler laufen
 * mit MONITOR und ignorieren abgebrochene Events - protokolliert wird nur,
 * was tatsaechlich passiert ist.
 */
public final class WatchListener implements Listener {

    private final Recorder recorder;
    private final boolean logBreak;
    private final boolean logPlace;

    public WatchListener(Recorder recorder, FileConfiguration config) {
        this.recorder = recorder;
        this.logBreak = config.getBoolean("log-break", true);
        this.logPlace = config.getBoolean("log-place", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (logBreak) {
            record(event.getBlock(), event.getPlayer(), Action.BREAK, event.getBlock().getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type == Material.TNT) {
            record(event.getBlockPlaced(), event.getPlayer(), Action.TNT_PLACE, type);
        } else if (logPlace) {
            record(event.getBlockPlaced(), event.getPlayer(), Action.PLACE, type);
        }
    }

    /** Nur von Spielern gelegtes Feuer (Feuerzeug, Feuerkugel) - natuerliche Ausbreitung waere zu viel Rauschen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            record(event.getBlock(), event.getPlayer(), Action.IGNITE, event.getCause().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Player player = responsiblePlayer(event.getPrimingEntity());
        String actor = player != null ? player.getName() : "#" + causeName(event.getCause());
        record(event.getBlock().getLocation(), player, actor, Action.TNT_PRIME, Material.TNT.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            record(event.getBlock(), event.getPlayer(), Action.LAVA, Material.LAVA);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Action action = switch (event.getEntity().getType()) {
            case TNT_MINECART -> Action.TNT_MINECART;
            case END_CRYSTAL -> Action.END_CRYSTAL;
            default -> null;
        };
        if (action != null) {
            Player player = event.getPlayer();
            record(event.getEntity().getLocation(), player, player != null ? player.getName() : "#unbekannt",
                    action, event.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!destroysBlocks(event.getExplosionResult())) {
            return; // z. B. Windkugel oder Creeper ohne mobGriefing: kein Block geht kaputt
        }
        Entity entity = event.getEntity();
        Player player = null;
        String actor;
        if (entity instanceof TNTPrimed tnt) {
            player = responsiblePlayer(tnt.getSource());
            actor = player != null ? player.getName() : "#TNT";
        } else if (entity instanceof Creeper) {
            actor = "#Creeper";
        } else if (entity instanceof ExplosiveMinecart) {
            actor = "#TNT-Lore";
        } else if (entity instanceof EnderCrystal) {
            actor = "#Endkristall";
        } else if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            player = responsiblePlayer(shooter);
            actor = player != null ? player.getName() : "#" + shooter.getType().name();
        } else {
            actor = "#" + entity.getType().name();
        }
        recordExploded(event.blockList(), player, actor);
    }

    /** Betten/Seelenanker ausserhalb ihrer Dimension - der ausloesende Spieler ist hier nicht bekannt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!destroysBlocks(event.getExplosionResult())) {
            return;
        }
        recordExploded(event.blockList(), null, "#" + event.getExplodedBlockState().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        record(event.getBlock().getLocation(), null, "#Feuer", Action.BURN, event.getBlock().getType().name());
    }

    /**
     * Nur DESTROY/DESTROY_WITH_DECAY zerstoeren Bloecke. Windkugeln (TRIGGER_BLOCK) liefern
     * trotzdem eine blockList - die Bloecke werden nur ausgeloest (Knoepfe, Tueren), nicht entfernt.
     */
    private static boolean destroysBlocks(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    private void recordExploded(List<Block> blocks, Player player, String actor) {
        for (Block block : blocks) {
            if (!block.getType().isAir()) {
                record(block.getLocation(), player, actor, Action.EXPLODE, block.getType().name());
            }
        }
    }

    /** Spieler hinter einer Aktion, auch ueber Projektile (z. B. brennender Pfeil auf TNT). */
    private static Player responsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != entity) {
            return responsiblePlayer(tnt.getSource());
        }
        return null;
    }

    private static String causeName(TNTPrimeEvent.PrimeCause cause) {
        return switch (cause) {
            case FIRE -> "Feuer";
            case REDSTONE -> "Redstone";
            case EXPLOSION -> "Explosion";
            case PROJECTILE -> "Geschoss";
            case DISPENSER -> "Werfer";
            case BLOCK_BREAK -> "Abbau";
            default -> cause.name();
        };
    }

    private void record(Block block, Player player, Action action, Material material) {
        record(block.getLocation(), player, player.getName(), action, material.name());
    }

    private void record(Block block, Player player, Action action, String material) {
        record(block.getLocation(), player, player.getName(), action, material);
    }

    private void record(Location location, Player player, String actor, Action action, String material) {
        recorder.record(new LogEntry(System.currentTimeMillis(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                player != null ? player.getUniqueId() : null, actor, action, material));
    }
}
