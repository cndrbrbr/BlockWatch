package io.github.cndrbrbr.blockwatch.protection;

import io.github.cndrbrbr.blockwatch.storage.Action;
import io.github.cndrbrbr.blockwatch.storage.LogEntry;
import io.github.cndrbrbr.blockwatch.storage.Recorder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Verhindert gefaehrliche Gegenstaende (protection.blocked in config.yml,
 * z. B. TNT, Lava, Feuerzeug): die Aktion wird abgebrochen, als
 * "verbotener Versuch" protokolliert und der Spieler kommt ins Gefaengnis.
 * Spieler mit blockwatch.bypass (Standard: OP) sind ausgenommen. Werfer
 * duerfen diese Gegenstaende ebenfalls nicht ausgeben (ohne Strafe, es gibt
 * keinen Verantwortlichen).
 */
public final class ProtectionListener implements Listener {

    private final Recorder recorder;
    private final JailManager jail;
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);
    private final int jailMinutes;

    public ProtectionListener(Recorder recorder, JailManager jail, FileConfiguration config, Logger logger) {
        this.recorder = recorder;
        this.jail = jail;
        this.jailMinutes = config.getInt("protection.jail-minutes", 3);
        for (String name : config.getStringList("protection.blocked")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                blocked.add(material);
            } else {
                logger.warning("protection.blocked: unbekanntes Material " + name);
            }
        }
    }

    /**
     * Rechtsklick auf einen Block mit einem verbotenen Gegenstand - deckt
     * Setzen (TNT, Seelenanker), Anzuenden (Feuerzeug, Feuerkugel), Lava,
     * TNT-Loren und Endkristalle ab, bevor etwas passiert. Ein normales
     * Oeffnen von Truhen/Tueren mit dem Gegenstand in der Hand bleibt erlaubt.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return;
        }
        Material item = event.getItem().getType();
        Block clicked = event.getClickedBlock();
        if (!blocked.contains(item) || clicked == null
                || (clicked.getType().isInteractable() && !event.getPlayer().isSneaking())) {
            return;
        }
        if (punish(event.getPlayer(), item, clicked.getRelative(event.getBlockFace()).getLocation())) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Rueckfallebene, falls ein Setzen nicht ueber den Rechtsklick abgefangen wurde. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (blocked.contains(type) && punish(event.getPlayer(), type, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (blocked.contains(event.getBucket()) && punish(event.getPlayer(), event.getBucket(),
                event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Rueckfallebene fuer direkt angezuendetes TNT (z. B. Feuerzeug auf TNT, Pfeil). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (!blocked.contains(Material.TNT)) {
            return;
        }
        Player player = event.getPrimingEntity() instanceof Player p ? p
                : event.getPrimingEntity() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player != null && punish(player, Material.TNT, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (blocked.contains(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    /** @return true, wenn die Aktion verboten ist (Spieler ohne bypass) - dann wurde schon bestraft. */
    private boolean punish(Player player, Material material, Location where) {
        if (player.hasPermission("blockwatch.bypass")) {
            return false;
        }
        if (jail.isJailed(player.getUniqueId())) {
            return true;
        }
        recorder.record(new LogEntry(System.currentTimeMillis(), where.getWorld().getName(), where.getBlockX(),
                where.getBlockY(), where.getBlockZ(), player.getUniqueId(), player.getName(), Action.BLOCKED,
                material.name()));
        jail.jail(player, jailMinutes, pretty(material) + " ist hier verboten.");
        return true;
    }

    private static String pretty(Material material) {
        String lower = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
