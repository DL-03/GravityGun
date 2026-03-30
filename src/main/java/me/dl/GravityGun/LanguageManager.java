package me.dl.GravityGun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LanguageManager implements Listener {
    private final GravityGun plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<String, FileConfiguration> languageCache = new HashMap<>();

    private final String GITHUB_BASE_URL = "https://raw.githubusercontent.com/DL-03/GravityGun/langs/";
    public String serverLanguage = "en_us"; // Устанавливаем значение по умолчанию сразу
    public final String DEFAULT_LANGUAGE = "en_us";

    private final int REQUIRED_LANG_VERSION = 1;

    public LanguageManager(GravityGun plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setup() {
        // Убеждаемся, что значение из конфига не null и приводим к нужному формату
        String configLang = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        this.serverLanguage = (configLang != null ? configLang : DEFAULT_LANGUAGE).toLowerCase().replace('-', '_');

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        prepareInternalResource(DEFAULT_LANGUAGE);

        loadOrDownloadSync(serverLanguage);
        if (!serverLanguage.equals(DEFAULT_LANGUAGE)) {
            loadOrDownloadSync(DEFAULT_LANGUAGE);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            preLoadPlayerLanguage(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        preLoadPlayerLanguage(event.getPlayer());
    }

    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            preLoadPlayerLanguage(event.getPlayer());
        }, 1L);
    }

    public void preLoadPlayerLanguage(Player player) {
        if (player == null) return;
        String playerLocale = player.locale().toString().toLowerCase().replace('-', '_');
        checkAndLoadLocalFile(playerLocale);

        if (!languageCache.containsKey(playerLocale) || isLangOutdated(playerLocale)) {
            loadOrDownloadAsync(playerLocale);
        }
    }

    private void checkAndLoadLocalFile(String langName) {
        if (langName == null || languageCache.containsKey(langName)) return;

        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");
        if (file.exists()) {
            languageCache.put(langName, YamlConfiguration.loadConfiguration(file));
        }
    }

    private boolean isLangOutdated(String langName) {
        if (langName == null) return true;
        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");
        if (!file.exists()) return true;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getInt("version", 0) < REQUIRED_LANG_VERSION;
    }

    private void loadOrDownloadSync(String langName) {
        if (langName == null) return;
        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");

        if (!file.exists()) {
            prepareInternalResource(langName);
        }

        if (!file.exists() || isLangOutdated(langName)) {
            downloadLanguageSync(langName, file);
        }

        if (file.exists()) {
            languageCache.put(langName, YamlConfiguration.loadConfiguration(file));
        }
    }

    private void loadOrDownloadAsync(String langName) {
        if (langName == null) return;
        CompletableFuture.runAsync(() -> {
            File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");

            if (!file.exists()) {
                prepareInternalResource(langName);
                if (file.exists()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!languageCache.containsKey(langName)) {
                            languageCache.put(langName, YamlConfiguration.loadConfiguration(file));
                        }
                    });
                }
            }

            if (!file.exists() || isLangOutdated(langName)) {
                downloadLanguageSync(langName, file);

                if (file.exists()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        languageCache.put(langName, YamlConfiguration.loadConfiguration(file));
                    });
                }
            }
        });
    }

    private void downloadLanguageSync(String langName, File localFile) {
        String versionFolder = getVersionFolder();
        String remotePath = versionFolder + "/" + langName + ".yml";

        try {
            URL url = URI.create(GITHUB_BASE_URL + remotePath).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream in = connection.getInputStream()) {
                    Files.copy(in, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Language pack updated from GitHub: " + langName);
                }
            }
        } catch (Exception ignored) {}
    }

    private void prepareInternalResource(String langName) {
        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");
        if (!file.exists()) {
            try {
                if (plugin.getResource("lang/" + langName + ".yml") != null) {
                    plugin.saveResource("lang/" + langName + ".yml", false);
                }
            } catch (Exception ignored) {}
        }
    }

    private String getVersionFolder() {
        return "v1";
    }

    public Component getMessage(Player player, String path, String def, TagResolver... resolvers) {
        String lang = serverLanguage;
        List<TagResolver> finalResolvers = new ArrayList<>(List.of(resolvers));

        if (player != null) {
            String playerLocale = player.locale().toString().toLowerCase().replace('-', '_');
            checkAndLoadLocalFile(playerLocale);

            if (languageCache.containsKey(playerLocale)) {
                lang = playerLocale;
            } else {
                loadOrDownloadAsync(playerLocale);
            }

            finalResolvers.add(Placeholder.parsed("player", player.getName()));
            finalResolvers.add(Placeholder.component("displayname", player.displayName()));
        }

        return getResultComponent(lang, path, def, finalResolvers.toArray(new TagResolver[0]));
    }

    public Component getMessage(String language, String path, String def, TagResolver... resolvers) {
        // Защита от NPE: если язык не указан, используем серверный
        if (language == null) return getResultComponent(serverLanguage, path, def, resolvers);

        String langKey = language.toLowerCase().replace('-', '_');
        checkAndLoadLocalFile(langKey);

        String lang = languageCache.containsKey(langKey) ? langKey : serverLanguage;
        return getResultComponent(lang, path, def, resolvers);
    }

    private Component getResultComponent(String lang, String path, String def, TagResolver... resolvers) {
        // lang гарантированно не null благодаря проверкам выше
        FileConfiguration config = languageCache.get(lang);

        if (config == null || !config.contains(path)) {
            config = languageCache.get(DEFAULT_LANGUAGE);
        }

        String raw = (config != null) ? config.getString(path) : null;
        if (raw == null) {
            raw = plugin.getConfig().getString(path, def);
        }

        // Окончательная защита, если даже в конфиге пусто
        if (raw == null) raw = def;

        return miniMessage.deserialize(raw, TagResolver.resolver(resolvers));
    }

    public Component getMessage(CommandSender sender, String path, String def, TagResolver... resolvers) {
        if (sender instanceof Player) {
            return getMessage((Player) sender, path, def, resolvers);
        } else {
            return getMessage(serverLanguage, path, def, resolvers);
        }
    }

    public void reload() {
        languageCache.clear();
        setup();
    }
}