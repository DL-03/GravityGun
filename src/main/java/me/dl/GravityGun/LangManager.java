package me.dl.GravityGun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class LangManager {
    private final GravityGun plugin;
    private FileConfiguration langConfig;
    private File langFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Базовый URL к ветке langs
    private final String GITHUB_BASE_URL = "https://raw.githubusercontent.com/YourUser/GravityGun/langs/";

    public LangManager(GravityGun plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        String langName = plugin.getConfig().getString("language", "en_us");
        File langDir = new File(plugin.getDataFolder(), "lang");

        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        langFile = new File(langDir, langName + ".yml");

        // Если файла нет локально, скачиваем из соответствующей папки версии
        if (!langFile.exists()) {
            downloadLanguage(langName);
        }

        reload();
    }

    /**
     * Определяет имя папки на GitHub на основе версии плагина.
     * Например, для 0.1.0 - 0.2.6 это будет папка "v1".
     */
    private String getVersionFolder() {
        String pluginVersion = plugin.getDescription().getVersion();

        // Логика распределения версий по папкам
        if (isVersionInRange(pluginVersion, "0.1.0", "0.2.6")) {
            return "v1";
        }

        // Если версия выше или не попала в диапазон, можно вернуть "latest" или пустую строку
        return "v1";
    }

    private boolean isVersionInRange(String current, String min, String max) {
        try {
            // Упрощенная проверка. Для более точной проверки лучше использовать семантическое сравнение версий
            return current.compareTo(min) >= 0 && current.compareTo(max) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadLanguage(String langName) {
        String versionFolder = getVersionFolder();
        // Путь теперь выглядит как: .../langs/v1/ru_ru.yml
        String remotePath = versionFolder + "/" + langName + ".yml";

        plugin.getLogger().info("Downloading language from GitHub: branch 'langs', folder '" + versionFolder + "'");

        try {
            URL url = new URL(GITHUB_BASE_URL + remotePath);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream in = connection.getInputStream()) {
                    Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Successfully downloaded " + langName + ".yml from " + versionFolder);
                }
            } else {
                plugin.getLogger().warning("Could not find lang file at " + remotePath + " (HTTP " + connection.getResponseCode() + ")");
                // Если в папке версии файла нет, пытаемся достать из ресурсов плагина (внутри .jar)
                plugin.saveResource("lang/" + langName + ".yml", false);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while downloading language file!", e);
        }
    }

    public void reload() {
        if (langFile == null || !langFile.exists()) {
            langConfig = new YamlConfiguration();
            return;
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public Component getMessage(String path, String def) {
        if (langConfig == null) return miniMessage.deserialize(def);
        String raw = langConfig.getString(path, def);
        return miniMessage.deserialize(raw != null ? raw : def);
    }

    public String getRaw(String path, String def) {
        return langConfig != null ? langConfig.getString(path, def) : def;
    }
}