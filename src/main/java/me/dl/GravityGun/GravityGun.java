package me.dl.GravityGun;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class GravityGun extends JavaPlugin {
    private static GravityGun instance;
    public static Manager manager;
    public static LanguageManager langManager;
    public static CommandsManager commandsManager;
    public static UpdateChecker updateChecker;

    public static Metrics metrics;

    public int bStats_pluginId = 32241;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        metrics = new Metrics(this, bStats_pluginId);

        langManager = new LanguageManager(this);

        updateChecker = new UpdateChecker(this, "DL-03/GravityGun");
        updateChecker.checkForUpdates();

        commandsManager = new CommandsManager(this);
        manager = new Manager();

        reload();

        Bukkit.getConsoleSender().sendMessage(langManager.getMessage(Bukkit.getConsoleSender(), "message.gg.enabled", "[GG] Enabled GravityGun v<version>", Placeholder.parsed("version", getPluginMeta().getVersion())));
    }

    @Override
    public void onDisable() {
        metrics.shutdown();

        if (manager != null) {
            manager.releaseAll();
        }
        Bukkit.getConsoleSender().sendMessage(langManager.getMessage(Bukkit.getConsoleSender(), "message.gg.disabled", "[GG] Disabled GravityGun v<version>", Placeholder.parsed("version", getPluginMeta().getVersion())));
    }

    public void reload() {
        reloadConfig();
        langManager.reload();
        manager.reload();
    }

    public static GravityGun getInstance() { return instance; }
}
