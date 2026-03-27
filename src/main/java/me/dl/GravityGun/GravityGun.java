package me.dl.GravityGun;

import org.bukkit.plugin.java.JavaPlugin;

public final class GravityGun extends JavaPlugin {
    private static GravityGun instance;
    public static Manager manager;
    final public static Commands commands = new Commands();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        manager = new Manager();
        getServer().getPluginManager().registerEvents(manager, this);

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
        manager.reload();
    }

    public static GravityGun getInstance() { return instance; }
}
