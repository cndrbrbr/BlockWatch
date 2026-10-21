package io.github.cndrbrbr.blockwatch.command;

import io.github.cndrbrbr.blockwatch.BlockWatchPlugin;
import io.github.cndrbrbr.blockwatch.storage.Action;
import io.github.cndrbrbr.blockwatch.storage.Database;
import io.github.cndrbrbr.blockwatch.storage.LogEntry;
import io.github.cndrbrbr.blockwatch.util.TimeArgs;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** {@code /bw} - Abfragen des Protokolls, siehe README.md. */
public final class BlockWatchCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("hier", "inspect", "gefahr", "spieler", "stats", "bei", "seite");
    /** Hoechstens so viele Eintraege je Abfrage laden (danach weiter eingrenzen). */
    private static final int QUERY_LIMIT = 1000;
    private static final int TOP_ACTORS = 15;

    /** Ergebnis einer Abfrage zum Blaettern: feste Kopfzeilen plus Eintragszeilen. */
    private record Pages(List<String> header, List<LogEntry> entries) {
    }

    /** Abfrage, die im Async-Task laeuft und die Seiten liefert. */
    @FunctionalInterface
    private interface Query {
        Pages run() throws SQLException;
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
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "hier", "here" -> handleHere(sender, rest, false);
            case "gefahr", "danger" -> handleDanger(sender, rest);
            case "inspect", "i" -> handleInspect(sender);
            case "spieler", "player", "p" -> handlePlayer(sender, rest);
            case "stats" -> handleStats(sender, rest);
            case "bei", "at" -> handleAt(sender, rest);
            case "seite", "page" -> handlePage(sender, rest);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6BlockWatch §7- wer hat wann was gemacht?");
        sender.sendMessage("§e/bw hier §7[radius] [zeit] §f- alles im Umkreis um dich");
        sender.sendMessage("§e/bw gefahr §7[radius|alle] [zeit] §f- TNT, Feuer, Lava, Explosionen");
        sender.sendMessage("§e/bw inspect §f- an/aus: Linksklick = dieser Block, Rechtsklick = Platz davor");
        sender.sendMessage("§e/bw spieler §7<name> [gefahr] [zeit] §f- was ein Spieler gemacht hat");
        sender.sendMessage("§e/bw stats §7[zeit] §f- Rangliste aller Spieler");
        sender.sendMessage("§e/bw bei §7<welt> <x> <y> <z> [radius] [zeit] §f- wie hier, z. B. von der Konsole");
        sender.sendMessage("§e/bw seite §7<n> §f- weiterblättern");
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
            String[] rest = java.util.Arrays.copyOfRange(args, 4, args.length);
            boolean dangerOnly = java.util.Arrays.stream(rest).anyMatch(a -> a.equalsIgnoreCase("gefahr"));
            queryArea(sender, location, rest, dangerOnly);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cUngültige Koordinaten.");
        }
    }

    private void handleDanger(CommandSender sender, String[] args) {
        boolean everywhere = !(sender instanceof Player)
                || java.util.Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("alle"));
        if (!everywhere) {
            handleHere(sender, args, true);
            return;
        }
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        runQuery(sender, () -> new Pages(List.of("§6BlockWatch §7- gefährliche Aktionen auf dem ganzen Server, "
                + filter.timeLabel), database.dangerous(filter.since, QUERY_LIMIT)));
    }

    private void queryArea(CommandSender sender, Location location, String[] args, boolean dangerOnly) {
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        Database.Area area = new Database.Area(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ(), filter.radius);
        runQuery(sender, () -> {
            List<String> header = new ArrayList<>();
            header.add("§6BlockWatch §7- " + (dangerOnly ? "§cgefährliche Aktionen §7" : "")
                    + "Umkreis " + filter.radius + " um §f" + area.x() + " " + area.y() + " " + area.z()
                    + "§7, " + filter.timeLabel);
            for (Database.ActorSummary summary : database.summaryNear(area, filter.since, dangerOnly)) {
                header.add(summaryLine(summary));
            }
            return new Pages(header, database.near(area, filter.since, dangerOnly, QUERY_LIMIT));
        });
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
            player.sendMessage("§6BlockWatch §7Inspektion §aan§7: §fLinksklick§7 = Verlauf dieses Blocks, "
                    + "§fRechtsklick§7 = Verlauf des Platzes davor (z. B. abgebaute Blöcke). Aus mit §e/bw inspect§7.");
        }
    }

    /** Vom InspectListener aufgerufen. */
    public void showBlock(Player player, Block block) {
        runQuery(player, () -> new Pages(List.of("§6BlockWatch §7- Verlauf von §f" + block.getX() + " "
                + block.getY() + " " + block.getZ() + " §7(jetzt: " + pretty(block.getType().name()) + ")"),
                database.block(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), QUERY_LIMIT)));
    }

    private void handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eBenutzung: /bw spieler <name> [gefahr] [zeit]");
            return;
        }
        String name = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        boolean dangerOnly = java.util.Arrays.stream(rest).anyMatch(a -> a.equalsIgnoreCase("gefahr"));
        Filter filter = parseFilter(sender, rest);
        if (filter == null) {
            return;
        }
        runQuery(sender, () -> {
            List<String> header = new ArrayList<>();
            header.add("§6BlockWatch §7- Spieler §f" + name + "§7, " + filter.timeLabel
                    + (dangerOnly ? ", nur gefährliche Aktionen" : ""));
            List<Database.ActorSummary> summary = database.summary(filter.since, name);
            if (summary.isEmpty()) {
                header.add("§7Keine Einträge.");
            } else {
                header.add(detailLine(summary.get(0)));
            }
            return new Pages(header, database.byActor(name, filter.since, dangerOnly, QUERY_LIMIT));
        });
    }

    private void handleStats(CommandSender sender, String[] args) {
        Filter filter = parseFilter(sender, args);
        if (filter == null) {
            return;
        }
        runAsync(sender, () -> {
            List<Database.ActorSummary> summaries = database.summary(filter.since, null);
            List<String> lines = new ArrayList<>();
            lines.add("§6BlockWatch §7- Rangliste, " + filter.timeLabel + " §8(Einträge: " + database.count() + ")");
            int rank = 1;
            for (Database.ActorSummary summary : summaries) {
                if (summary.actor().startsWith("#")) {
                    continue;
                }
                lines.add("§e" + rank + ". " + summaryLine(summary));
                if (++rank > TOP_ACTORS) {
                    break;
                }
            }
            List<String> environment = summaries.stream().filter(s -> s.actor().startsWith("#"))
                    .map(s -> s.actor().substring(1) + " " + s.total()).toList();
            if (!environment.isEmpty()) {
                lines.add("§7Ohne Spieler: " + String.join(", ", environment));
            }
            return lines;
        });
    }

    private void handlePage(CommandSender sender, String[] args) {
        Pages pages = lastResults.get(key(sender));
        if (pages == null) {
            sender.sendMessage("§cKeine vorherige Abfrage.");
            return;
        }
        int page;
        try {
            page = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cUngültige Seitenzahl.");
            return;
        }
        sendPage(sender, pages, page);
    }

    /** Radius und Zeitraum aus den Argumenten (Reihenfolge egal). */
    private record Filter(int radius, long since, String timeLabel) {
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

    private void runQuery(CommandSender sender, Query query) {
        if (!(sender instanceof Player)) {
            // Konsole/RCON: direkt beantworten - RCON nimmt nur Ausgaben an, die
            // waehrend des Befehls kommen. Abfragen sind indexiert und kurz.
            try {
                plugin.getRecorder().flush();
                Pages pages = query.run();
                lastResults.put(key(sender), pages);
                sendPage(sender, pages, 1);
            } catch (SQLException exception) {
                sender.sendMessage("§cAbfrage fehlgeschlagen: " + exception.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getRecorder().flush();
                Pages pages = query.run();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    lastResults.put(key(sender), pages);
                    sendPage(sender, pages, 1);
                });
            } catch (SQLException exception) {
                fail(sender, exception);
            }
        });
    }

    @FunctionalInterface
    private interface LinesQuery {
        List<String> run() throws SQLException;
    }

    private void runAsync(CommandSender sender, LinesQuery query) {
        if (!(sender instanceof Player)) {
            try {
                plugin.getRecorder().flush();
                query.run().forEach(sender::sendMessage);
            } catch (SQLException exception) {
                sender.sendMessage("§cAbfrage fehlgeschlagen: " + exception.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getRecorder().flush();
                List<String> lines = query.run();
                Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (SQLException exception) {
                fail(sender, exception);
            }
        });
    }

    private void fail(CommandSender sender, SQLException exception) {
        plugin.getLogger().warning("Abfrage fehlgeschlagen: " + exception.getMessage());
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§cAbfrage fehlgeschlagen: "
                + exception.getMessage()));
    }

    private void sendPage(CommandSender sender, Pages pages, int page) {
        int pageCount = Math.max(1, (pages.entries().size() + pageSize - 1) / pageSize);
        page = Math.min(Math.max(1, page), pageCount);
        pages.header().forEach(sender::sendMessage);
        if (pages.entries().isEmpty()) {
            sender.sendMessage("§7Keine Einträge gefunden.");
            return;
        }
        int from = (page - 1) * pageSize;
        for (LogEntry entry : pages.entries().subList(from, Math.min(from + pageSize, pages.entries().size()))) {
            sendEntry(sender, entry);
        }
        String more = pages.entries().size() >= QUERY_LIMIT ? " §8(nur die neuesten " + QUERY_LIMIT + ")" : "";
        sender.sendMessage("§7Seite " + page + "/" + pageCount + " §8(" + pages.entries().size() + " Einträge)"
                + more + (page < pageCount ? " §7- weiter mit §e/bw seite " + (page + 1) : ""));
    }

    private void sendEntry(CommandSender sender, LogEntry entry) {
        String coords = entry.x() + " " + entry.y() + " " + entry.z();
        String text = "§7" + timeFormat.format(Instant.ofEpochMilli(entry.timestamp())) + " "
                + actorColor(entry.actor()) + entry.actor() + " " + (entry.action().isDangerous() ? "§c" : "§a")
                + entry.action().label() + " §e" + pretty(entry.material()) + " §8(" + coords + ")";
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text + " §8" + entry.world());
            return;
        }
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
        if (player.getWorld().getName().equals(entry.world())) {
            line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/minecraft:tp @s " + entry.x() + " " + entry.y() + " " + entry.z()));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Klicken: hinteleportieren")));
        }
        player.spigot().sendMessage(new BaseComponent[]{line});
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

    /** Alle Aktionsarten einzeln, fuer /bw spieler. */
    private static String detailLine(Database.ActorSummary summary) {
        List<String> parts = new ArrayList<>();
        for (Action action : Action.values()) {
            Integer count = summary.counts().get(action);
            if (count != null) {
                parts.add((action.isDangerous() ? "§c" : "§a") + count + " " + action.label());
            }
        }
        return String.join("§7, ", parts);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("spieler")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bei")) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        return List.of();
    }
}
