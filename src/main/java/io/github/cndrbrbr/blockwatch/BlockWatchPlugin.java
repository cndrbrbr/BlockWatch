package io.github.cndrbrbr.blockwatch;

import io.github.cndrbrbr.blockwatch.command.BlockWatchCommand;
import io.github.cndrbrbr.blockwatch.listener.InspectListener;
import io.github.cndrbrbr.blockwatch.listener.WatchListener;
import io.github.cndrbrbr.blockwatch.protection.JailManager;
import io.github.cndrbrbr.blockwatch.protection.ProtectionListener;
import io.github.cndrbrbr.blockwatch.storage.Database;
import io.github.cndrbrbr.blockwatch.storage.Recorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

/** Protokolliert Blockaenderungen und gefaehrliche Aktionen, siehe README.md. */
public final class BlockWatchPlugin extends JavaPlugin {

    private static final long FLUSH_TICKS = 40L;
    private static final long PURGE_TICKS = 24L * 60 * 60 * 20;

    private Database database;
    private Recorder recorder;
    private JailManager jailManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Neue Einstellungen aus Updates in eine bestehende config.yml uebernehmen.
        getConfig().options().copyDefaults(true);
        saveConfig();
        getDataFolder().mkdirs();
        try {
            database = new Database(new File(getDataFolder(), "blockwatch.db"));
        } catch (SQLException exception) {
            getLogger().severe("Datenbank konnte nicht geöffnet werden: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        recorder = new Recorder(database, getLogger());
        getServer().getScheduler().runTaskTimerAsynchronously(this, recorder::flush, FLUSH_TICKS, FLUSH_TICKS);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::purgeOld, 20L * 60, PURGE_TICKS);

        jailManager = new JailManager(this);
        getServer().getPluginManager().registerEvents(jailManager, this);
        if (getConfig().getBoolean("protection.enabled", true)) {
            getServer().getPluginManager().registerEvents(
                    new ProtectionListener(recorder, jailManager, getConfig(), getLogger()), this);
        }

        BlockWatchCommand command = new BlockWatchCommand(this, database);
        getCommand("bw").setExecutor(command);
        getCommand("bw").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new WatchListener(recorder, getConfig()), this);
        getServer().getPluginManager().registerEvents(new InspectListener(command), this);
        getLogger().info("BlockWatch aktiviert.");
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            recorder.flush();
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().warning("Datenbank konnte nicht geschlossen werden: " + exception.getMessage());
            }
        }
    }

    public Recorder getRecorder() {
        return recorder;
    }

    public JailManager getJailManager() {
        return jailManager;
    }

    private void purgeOld() {
        int days = getConfig().getInt("retention-days", 180);
        if (days <= 0) {
            return;
        }
        try {
            int removed = database.purge(System.currentTimeMillis() - days * 86_400_000L);
            if (removed > 0) {
                getLogger().info(removed + " Einträge älter als " + days + " Tage gelöscht.");
            }
        } catch (SQLException exception) {
            getLogger().warning("Aufräumen fehlgeschlagen: " + exception.getMessage());
        }
    }
}
