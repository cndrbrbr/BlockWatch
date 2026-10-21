package io.github.cndrbrbr.blockwatch.protection;

import io.github.cndrbrbr.blockwatch.BlockWatchPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gefaengnis: eingesperrte Spieler sitzen bis {@code until} in der Zelle,
 * im Abenteuermodus, ohne Befehle und ohne fremde Teleports. Der Zustand
 * steht in jail.yml und ueberlebt Logout, Tod und Neustart; die Zeit laeuft
 * auch offline weiter. Danach geht es zurueck an den alten Ort mit dem
 * alten Spielmodus.
 */
public final class JailManager implements Listener {

    /** Wie weit sich ein Insasse von der Zelle entfernen darf, bevor er zurueckgeholt wird. */
    private static final double MAX_DISTANCE = 4.0;

    private record Inmate(String name, long until, Location returnTo, GameMode gameMode) {
    }

    private final BlockWatchPlugin plugin;
    private final File file;
    private final Map<UUID, Inmate> inmates = new HashMap<>();
    /** Erlaubt die eigenen Teleports trotz {@link #onTeleport}. */
    private boolean teleporting;

    public JailManager(BlockWatchPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jail.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isJailed(UUID playerId) {
        return inmates.containsKey(playerId);
    }

    /** Sperrt ein (bzw. verlaengert bis mindestens jetzt + minutes). */
    public void jail(Player player, int minutes, String reason) {
        Optional<Location> cell = cell();
        if (cell.isEmpty()) {
            plugin.getLogger().warning("Kein Gefängnis verfügbar - " + player.getName() + " nicht eingesperrt.");
            return;
        }
        long until = System.currentTimeMillis() + minutes * 60_000L;
        Inmate previous = inmates.get(player.getUniqueId());
        Inmate inmate = previous != null
                ? new Inmate(player.getName(), Math.max(previous.until(), until), previous.returnTo(), previous.gameMode())
                : new Inmate(player.getName(), until, player.getLocation(), player.getGameMode());
        inmates.put(player.getUniqueId(), inmate);
        save();
        moveToCell(player, cell.get());
        player.sendMessage("§c§lGefängnis! §r§c" + reason + " Du bist für " + minutes + " Minute"
                + (minutes == 1 ? "" : "n") + " eingesperrt.");
        String notice = "§6BlockWatch §7" + player.getName() + " sitzt " + minutes + " Min. im Gefängnis: " + reason;
        plugin.getLogger().info(player.getName() + " eingesperrt (" + minutes + " Min.): " + reason);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != player && online.hasPermission("blockwatch.use")) {
                online.sendMessage(notice);
            }
        }
    }

    /** Vorzeitig freilassen; offline Spieler werden beim naechsten Login freigelassen. */
    public boolean release(UUID playerId) {
        Inmate inmate = inmates.get(playerId);
        if (inmate == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            inmates.put(playerId, new Inmate(inmate.name(), 0, inmate.returnTo(), inmate.gameMode()));
            save();
            return true;
        }
        inmates.remove(playerId);
        save();
        teleport(player, inmate.returnTo() != null && inmate.returnTo().getWorld() != null
                ? inmate.returnTo() : player.getWorld().getSpawnLocation());
        player.setGameMode(inmate.gameMode());
        player.sendMessage("§aDu bist wieder frei. Bitte kein TNT, Feuer oder Lava!");
        return true;
    }

    /** Spielername zu einer UUID unter den Insassen (fuer /bw frei mit Offline-Spielern). */
    public Optional<UUID> findByName(String name) {
        return inmates.entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst();
    }

    public void setCell(Location location) {
        plugin.getConfig().set("protection.jail.world", location.getWorld().getName());
        plugin.getConfig().set("protection.jail.x", location.getX());
        plugin.getConfig().set("protection.jail.y", location.getY());
        plugin.getConfig().set("protection.jail.z", location.getZ());
        plugin.saveConfig();
    }

    /** Konfigurierte Zelle, sonst einmalig eine Grundgesteinszelle hoch ueber dem Spawn der Hauptwelt bauen. */
    private Optional<Location> cell() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("protection.jail");
        if (section != null && section.contains("world")) {
            World world = Bukkit.getWorld(section.getString("world"));
            if (world != null) {
                return Optional.of(new Location(world, section.getDouble("x"), section.getDouble("y"),
                        section.getDouble("z")));
            }
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return Optional.empty();
        }
        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int floorY = world.getMaxHeight() - 12;
        buildCell(world, spawn.getBlockX(), floorY, spawn.getBlockZ());
        Location cell = new Location(world, spawn.getBlockX() + 0.5, floorY + 1, spawn.getBlockZ() + 0.5);
        setCell(cell);
        plugin.getLogger().info("Gefängniszelle gebaut bei " + cell.getBlockX() + " " + cell.getBlockY() + " "
                + cell.getBlockZ() + " (verschieben mit /bw setjail).");
        return Optional.of(cell);
    }

    /** Hohler 5x5x5-Wuerfel aus Grundgestein, innen 3x3x3, Seelaterne in der Decke. */
    private static void buildCell(World world, int centerX, int floorY, int centerZ) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 0 || dy == 4;
                    world.getBlockAt(centerX + dx, floorY + dy, centerZ + dz)
                            .setType(wall ? Material.BEDROCK : Material.AIR);
                }
            }
        }
        world.getBlockAt(centerX, floorY + 4, centerZ).setType(Material.SEA_LANTERN);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Inmate> entry : Map.copyOf(inmates).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            long remaining = entry.getValue().until() - now;
            if (remaining <= 0) {
                release(entry.getKey());
                continue;
            }
            long seconds = (remaining + 999) / 1000;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                    String.format("§cGefängnis: noch %d:%02d", seconds / 60, seconds % 60)));
            cell().ifPresent(cell -> {
                if (player.getWorld() != cell.getWorld() || player.getLocation().distance(cell) > MAX_DISTANCE) {
                    moveToCell(player, cell);
                }
            });
        }
    }

    private void moveToCell(Player player, Location cell) {
        teleport(player, cell);
        player.setGameMode(GameMode.ADVENTURE);
    }

    private void teleport(Player player, Location location) {
        teleporting = true;
        try {
            player.teleport(location);
        } finally {
            teleporting = false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Inmate inmate = inmates.get(event.getPlayer().getUniqueId());
        if (inmate == null) {
            return;
        }
        if (inmate.until() <= System.currentTimeMillis()) {
            release(event.getPlayer().getUniqueId());
        } else {
            cell().ifPresent(cell -> moveToCell(event.getPlayer(), cell));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (isJailed(event.getPlayer().getUniqueId())) {
            cell().ifPresent(event::setRespawnLocation);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isJailed(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("blockwatch.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cIm Gefängnis sind keine Befehle erlaubt.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!teleporting && isJailed(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("blockwatch.bypass")) {
            event.setCancelled(true);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            try {
                GameMode mode = GameMode.valueOf(section.getString("gamemode", "SURVIVAL"));
                inmates.put(UUID.fromString(key), new Inmate(section.getString("name", "?"), section.getLong("until"),
                        section.getLocation("return"), mode));
            } catch (IllegalArgumentException | NullPointerException exception) {
                plugin.getLogger().warning("Ungültiger Eintrag in jail.yml: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        inmates.forEach((id, inmate) -> {
            String key = id.toString();
            yaml.set(key + ".name", inmate.name());
            yaml.set(key + ".until", inmate.until());
            yaml.set(key + ".return", inmate.returnTo());
            yaml.set(key + ".gamemode", inmate.gameMode().name());
        });
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("jail.yml konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }
}
