package me.dl.GravityGun;

import org.bukkit.plugin.java.JavaPlugin;

public final class GravityGun extends JavaPlugin {
    private static GravityGun instance;
    public static Manager manager;
    public static LanguageManager langManager;
    public static Commands commands;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        langManager = new LanguageManager(this);
        commands = new Commands();
        manager = new Manager();

        commands.register();

        reload();
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.releaseAll();
        }
    }

    public void reload() {
        reloadConfig();
        langManager.reload();
        manager.reload();
    }

    public static GravityGun getInstance() { return instance; }
}
