package io.github.cndrbrbr.blockwatch.command;

import io.github.cndrbrbr.blockwatch.BlockWatchPlugin;
import io.github.cndrbrbr.blockwatch.storage.Action;
import io.github.cndrbrbr.blockwatch.storage.Database;
import io.github.cndrbrbr.blockwatch.util.TimeArgs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /bw} - Abfragen des Protokolls, siehe README.md. Alle Abfragen
 * zeigen eine Statistik "Spieler · Blocktyp · Anzahl je Aktion" mit dem
 * Zeitpunkt der letzten Aktion, keine Einzeleintraege.
 */
public final class BlockWatchCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("hier", "inspect", "gefahr", "spieler", "stats", "bei", "seite",
            "jail", "frei", "setjail");
    private static final int TOP_ACTORS = 15;

    /** Ergebnis einer Abfrage zum Blaettern: feste Kopfzeilen plus Statistikzeilen. */
    private record Pages(List<String> header, List<String> rows) {
    }

    /** Abfrage, die (fuer Spieler) im Async-Task laeuft und die Seiten liefert. */
    @FunctionalInterface
    private interface Query {
        Pages run() throws SQLException;
    }

    /** Radius und Zeitraum aus den Argumenten (Reihenfolge egal). */
    private record Filter(int radius, long since, String timeLabel) {
    }

    private final BlockWatchPlugin plugin;
    private final Database database;
    private final DateTimeFormatter timeFormat;
    private final int defaultRadius;
    private final int maxRadius;
    private final int pageSize;
    private final Map<String, Pages> lastResults = new HashMap<>();
    private final Set<UUID> inspecting = new HashSet<>();

    public BlockWatchCommand(BlockWatchPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.timeFormat = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
                .withZone(ZoneId.of(plugin.getConfig().getString("timezone", "Europe/Berlin")));
        this.defaultRadius = plugin.getConfig().getInt("default-radius", 5);
        this.maxRadius = plugin.getConfig().getInt("max-radius", 50);
        this.pageSize = Math.max(1, plugin.getConfig().getInt("page-size", 10));
    }

    public boolean isInspecting(Player player) {
        return inspecting.contains(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "hier", "here" -> handleHere(sender, rest, false);
            case "gefahr", "danger" -> handleDanger(sender, rest);
            case "inspect", "i" -> handleInspect(sender);
            case "spieler", "player", "p" -> handlePlayer(sender, rest);
            case "stats" -> handleStats(sender, rest);
            case "bei", "at" -> handleAt(sender, rest);
            case "seite", "page" -> handlePage(sender, rest);
            case "jail" -> handleJail(sender, rest);
            case "frei", "release" -> handleRelease(sender, rest);
            case "setjail" -> handleSetJail(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6BlockWatch §7- wer hat wann was gemacht?");
        sender.sendMessage("§e/bw hier §7[radius] [zeit] §f- Statistik im Umkreis um dich");
        sender.sendMessage("§e/bw gefahr §7[radius|alle] [zeit] §f- nur TNT, Feuer, Lava, Explosionen");
        sender.sendMessage("§e/bw inspect §f- an/aus: Linksklick = dieser Block, Rechtsklick = Platz davor");
        sender.sendMessage("§e/bw spieler §7<name> [gefahr] [zeit] §f- Statistik eines Spielers");
        sender.sendMessage("§e/bw stats §7[zeit] §f- Rangliste aller Spieler");
        sender.sendMessage("§e/bw bei §7<welt> <x> <y> <z> [radius] [zeit] §f- wie hier, z. B. von der Konsole");
        sender.sendMessage("§e/bw seite §7<n> §f- weiterblättern");
        sender.sendMessage("§e/bw jail §7<spieler> [minuten] §f/ §e/bw frei §7<spieler> §f- Gefängnis");
        sender.sendMessage("§e/bw setjail §f- Gefängniszelle an deine Position setzen");
        sender.sendMessage("§7Zeit z. B. §f30m§7, §f2h§7, §f7d§7, §f2w §7- ohne Angabe: alles");
    }

    private void handleHere(CommandSender sender, String[] args, boolean dangerOnly) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur im Spiel - von der Konsole: /bw bei <welt> <x> <y> <z> [radius] [zeit]");
            return;
        }
        queryArea(sender, player.getLocation(), args, dangerOnly);
    }

    private void handleAt(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§eBenutzung: /bw bei <welt> <x> <y> <z> [radius] [zeit] [gefahr]");
            return;
        }
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage("§cUnbekannte Welt: " + args[0]);
            return;
        }
        try {
            Location location = new Location(world, Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]));
            String[] rest = Arrays.copyOfRange(args, 4, args.length);
            queryArea(sender, location, rest, hasFlag(rest, "gefahr"));
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cUngültige Koordinaten.");
        }
    }

    private void handleDanger(CommandSender sender, String[] args) {
        if (sender instanceof Player && !hasFlag(args, "alle")) {
            handleHere(sender, args, true);
            return;
        }
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        runQuery(sender, () -> statistics("§6BlockWatch §7- §cgefährliche Aktionen §7auf dem ganzen Server, "
                + filter.timeLabel, database.materials(null, null, filter.since, true)));
    }

    private void queryArea(CommandSender sender, Location location, String[] args, boolean dangerOnly) {
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        Database.Area area = new Database.Area(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ(), filter.radius);
        runQuery(sender, () -> statistics("§6BlockWatch §7- " + (dangerOnly ? "§cgefährliche Aktionen §7" : "")
                + "Umkreis " + filter.radius + " um §f" + area.x() + " " + area.y() + " " + area.z() + "§7, "
                + filter.timeLabel, database.materials(area, null, filter.since, dangerOnly)));
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur im Spiel.");
            return;
        }
        if (inspecting.remove(player.getUniqueId())) {
            player.sendMessage("§6BlockWatch §7Inspektion §caus§7.");
        } else {
            inspecting.add(player.getUniqueId());
            player.sendMessage("§6BlockWatch §7Inspektion §aan§7: §fLinksklick§7 = dieser Block, "
                    + "§fRechtsklick§7 = Platz davor (z. B. abgebaute Blöcke). Aus mit §e/bw inspect§7.");
        }
    }

    /** Vom InspectListener aufgerufen: Statistik genau dieses Blocks (Radius 0). */
    public void showBlock(Player player, Block block) {
        Database.Area area = new Database.Area(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), 0);
        runQuery(player, () -> statistics("§6BlockWatch §7- Block §f" + block.getX() + " " + block.getY() + " "
                + block.getZ() + " §7(jetzt: " + pretty(block.getType().name()) + ")",
                database.materials(area, null, 0, false)));
    }

    private void handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eBenutzung: /bw spieler <name> [gefahr] [zeit]");
            return;
        }
        String name = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        boolean dangerOnly = hasFlag(rest, "gefahr");
        Filter filter = parseFilter(sender, rest);
        if (filter == null) {
            return;
        }
        runQuery(sender, () -> statistics("§6BlockWatch §7- Spieler §f" + name + "§7, " + filter.timeLabel
                + (dangerOnly ? ", nur gefährliche Aktionen" : ""), database.materials(null, name, filter.since, dangerOnly)));
    }

    private void handleStats(CommandSender sender, String[] args) {
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        runQuery(sender, () -> {
            List<Database.ActorSummary> summaries = database.summary(filter.since, null);
            List<String> rows = new ArrayList<>();
            int rank = 1;
            for (Database.ActorSummary summary : summaries) {
                if (summary.actor().startsWith("#")) {
                    continue;
                }
                rows.add("§e" + rank + ". " + summaryLine(summary));
                if (++rank > TOP_ACTORS) {
                    break;
                }
            }
            List<String> environment = summaries.stream().filter(s -> s.actor().startsWith("#"))
                    .map(s -> s.actor().substring(1) + " " + s.total()).toList();
            if (!environment.isEmpty()) {
                rows.add("§7Ohne Spieler: " + String.join(", ", environment));
            }
            return new Pages(List.of("§6BlockWatch §7- Rangliste, " + filter.timeLabel + " §8(Einträge: "
                    + database.count() + ")"), rows);
        });
    }

    private void handlePage(CommandSender sender, String[] args) {
        Pages pages = lastResults.get(key(sender));
        if (pages == null) {
            sender.sendMessage("§cKeine vorherige Abfrage.");
            return;
        }
        try {
            sendPage(sender, pages, args.length > 0 ? Integer.parseInt(args[0]) : 1);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cUngültige Seitenzahl.");
        }
    }

    private void handleJail(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eBenutzung: /bw jail <spieler> [minuten]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cSpieler ist nicht online: " + args[0]);
            return;
        }
        int minutes = plugin.getConfig().getInt("protection.jail-minutes", 3);
        if (args.length > 1) {
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cUngültige Minutenzahl: " + args[1]);
                return;
            }
        }
        plugin.getJailManager().jail(target, minutes, "Eingesperrt von " + sender.getName() + ".");
        sender.sendMessage("§a" + target.getName() + " ist für " + minutes + " Minuten im Gefängnis.");
    }
    private void handleRelease(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eBenutzung: /bw frei <spieler>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        Optional<UUID> id = online != null ? Optional.of(online.getUniqueId())
                : plugin.getJailManager().findByName(args[0]);
        if (id.isPresent() && plugin.getJailManager().release(id.get())) {
            sender.sendMessage("§a" + args[0] + " ist frei" + (online == null ? " (beim nächsten Login)." : "."));
        } else {
            sender.sendMessage("§c" + args[0] + " ist nicht im Gefängnis.");
        }
    }
    private void handleSetJail(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur im Spiel.");
            return;
        }
        plugin.getJailManager().setCell(player.getLocation());
        sender.sendMessage("§aGefängniszelle ist jetzt hier. Achte darauf, dass man hier nicht herauskommt.");
    }

    /** Statistik-Seiten: eine Zeile je Spieler und Blocktyp (Sortierung siehe Database#materials). */
    private Pages statistics(String title, List<Database.MaterialSummary> rows) {
        List<String> lines = new ArrayList<>();
        for (Database.MaterialSummary row : rows) {
            lines.add(materialLine(row));
        }
        List<String> header = new ArrayList<>();
        header.add(title);
        if (!rows.isEmpty()) {
            header.add("§8Spieler · Blocktyp · Anzahl · zuletzt");
        }
        return new Pages(header, lines);
    }

    private String materialLine(Database.MaterialSummary row) {
        List<String> parts = new ArrayList<>();
        for (Action action : Action.values()) {
            Integer count = row.counts().get(action);
            if (count != null) {
                parts.add((action.isDangerous() ? "§c" : "§a") + count + " " + action.label());
            }
        }
        return actorColor(row.actor()) + row.actor() + " §8· §e" + pretty(row.material()) + " §8· "
                + String.join("§7, ", parts) + " §8· " + timeFormat.format(Instant.ofEpochMilli(row.lastTimestamp()));
    }

    private Filter parseFilter(CommandSender sender, String[] args) {
        int radius = defaultRadius;
        long since = 0;
        String timeLabel = "gesamter Zeitraum";
        for (String arg : args) {
            if (arg.equalsIgnoreCase("gefahr") || arg.equalsIgnoreCase("alle")) {
                continue;
            }
            OptionalLong duration = TimeArgs.parseDuration(arg);
            if (duration.isPresent()) {
                since = System.currentTimeMillis() - duration.getAsLong();
                timeLabel = TimeArgs.describe(arg);
                continue;
            }
            try {
                radius = Integer.parseInt(arg);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cUnbekannte Angabe: " + arg + " §7(Radius als Zahl, Zeit z. B. 2h oder 7d)");
                return null;
            }
            if (radius < 0 || radius > maxRadius) {
                sender.sendMessage("§cRadius muss zwischen 0 und " + maxRadius + " liegen.");
                return null;
            }
        }
        return new Filter(radius, since, timeLabel);
    }

    private static boolean hasFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase(flag));
    }

    /**
     * Konsole/RCON: direkt beantworten - RCON nimmt nur Ausgaben an, die
     * waehrend des Befehls kommen (Abfragen sind indexiert und kurz).
     * Spieler: im Async-Task, Ausgabe danach auf dem Server-Thread.
     */
    private void runQuery(CommandSender sender, Query query) {
        if (!(sender instanceof Player)) {
            try {
                plugin.getRecorder().flush();
                show(sender, query.run());
            } catch (SQLException exception) {
                sender.sendMessage("§cAbfrage fehlgeschlagen: " + exception.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getRecorder().flush();
                Pages pages = query.run();
                Bukkit.getScheduler().runTask(plugin, () -> show(sender, pages));
            } catch (SQLException exception) {
                plugin.getLogger().warning("Abfrage fehlgeschlagen: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§cAbfrage fehlgeschlagen: "
                        + exception.getMessage()));
            }
        });
    }

    private void show(CommandSender sender, Pages pages) {
        lastResults.put(key(sender), pages);
        sendPage(sender, pages, 1);
    }

    private void sendPage(CommandSender sender, Pages pages, int page) {
        int pageCount = Math.max(1, (pages.rows().size() + pageSize - 1) / pageSize);
        page = Math.min(Math.max(1, page), pageCount);
        pages.header().forEach(sender::sendMessage);
        if (pages.rows().isEmpty()) {
            sender.sendMessage("§7Keine Einträge gefunden.");
            return;
        }
        int from = (page - 1) * pageSize;
        pages.rows().subList(from, Math.min(from + pageSize, pages.rows().size())).forEach(sender::sendMessage);
        if (pageCount > 1) {
            sender.sendMessage("§7Seite " + page + "/" + pageCount
                    + (page < pageCount ? " §7- weiter mit §e/bw seite " + (page + 1) : ""));
        }
    }

    private static String summaryLine(Database.ActorSummary summary) {
        int broken = summary.counts().getOrDefault(Action.BREAK, 0);
        int placed = summary.counts().getOrDefault(Action.PLACE, 0);
        int dangerous = summary.dangerous();
        List<String> parts = new ArrayList<>();
        if (broken > 0) {
            parts.add(broken + " abgebaut");
        }
        if (placed > 0) {
            parts.add(placed + " gesetzt");
        }
        if (dangerous > 0) {
            parts.add("§c" + dangerous + " gefährlich");
        }
        return actorColor(summary.actor()) + summary.actor() + "§7: " + String.join("§7, ", parts);
    }

    private static String actorColor(String actor) {
        return actor.startsWith("#") ? "§d" : "§f";
    }

    static String pretty(String material) {
        String lower = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String key(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spieler") || args[0].equalsIgnoreCase("jail")
                || args[0].equalsIgnoreCase("frei"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bei")) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        return List.of();
    }
}
