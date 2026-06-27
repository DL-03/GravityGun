package me.dl.GravityGun;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {
    private final LanguageManager lang;

    private final JavaPlugin plugin;
    private final String currentVersion;
    private final String githubRepo;

    private String latestVersion = null;
    private boolean isUpdateAvailable = false;

    public UpdateChecker(JavaPlugin plugin, String githubRepo) {
        this.plugin = plugin;
        lang = GravityGun.langManager;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.githubRepo = githubRepo;
    }

    /**
     * Запускает асинхронную проверку версии.
     * Не блокирует основной поток сервера!
     */
    public void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + githubRepo + "/releases/latest"))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "GravityGun-UpdateChecker")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(response.body());

                    if (matcher.find()) {
                        String rawTag = matcher.group(1);
                        this.latestVersion = rawTag.replace("v", "").replace("_BETA", "").replace("_ALPHA", "").trim();

                        // Сравниваем версии
                        this.isUpdateAvailable = isNewerVersion(currentVersion, latestVersion);

                        // Выводим результат в консоль
                        Bukkit.getScheduler().runTask(plugin, this::logResultToConsole);
                    } else {
                        Bukkit.getConsoleSender().sendMessage(lang.getMessage(Bukkit.getConsoleSender(), "message.update-checker.tag-not-found", "[GG] UpdateChecker couldn't find the version tag in the GitHub response."));
                    }
                } else {
                    Bukkit.getConsoleSender().sendMessage(lang.getMessage(Bukkit.getConsoleSender(), "message.update-checker.unsecceed-tag-check", "[GG] UpdateChecker Unable to check for updates. GitHub response code: <code>", Placeholder.parsed("code", String.valueOf(response.statusCode()))));
                }
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(lang.getMessage(Bukkit.getConsoleSender(), "message.update-checker.error-while-checking-updates", "[GG] UpdateChecker Error while checking for updates: <message>", Placeholder.parsed("message", e.getMessage())));
            }
        });
    }

    private void logResultToConsole() {
        if (isUpdateAvailable) {
            Bukkit.getConsoleSender().sendMessage(lang.getMessage(Bukkit.getConsoleSender(), "message.update-checker.update-available", "[GG] Update Available <current-version> -> <new-version> | <link>", Placeholder.parsed("current-version", currentVersion), Placeholder.parsed("new-version", latestVersion), Placeholder.parsed("link", "https://github.com/" + githubRepo + "/releases")));
        } else {
            Bukkit.getConsoleSender().sendMessage(lang.getMessage(Bukkit.getConsoleSender(), "message.update-checker.no-updates", "[GG] No updates available <current-version>", Placeholder.parsed("current-version", currentVersion)));
        }
    }

    /**
     * Сравнивает две версии в формате 1.2.3.
     * Возвращает true, если remoteVersion новее, чем localVersion.
     */
    private boolean isNewerVersion(String localVersion, String remoteVersion) {
        if (localVersion.equalsIgnoreCase(remoteVersion)) {
            return false;
        }

        String[] localParts = localVersion.split("\\.");
        String[] remoteParts = remoteVersion.split("\\.");
        int length = Math.max(localParts.length, remoteParts.length);

        for (int i = 0; i < length; i++) {
            int localVal = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
            int remoteVal = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;

            if (remoteVal > localVal) return true;
            if (localVal > remoteVal) return false;
        }

        return false;
    }

    private int parseVersionPart(String part) {
        try {
            // Убираем возможные суффиксы вроде _SNAPSHOT, _ALPHA или _BETA
            return Integer.parseInt(part.split("_")[0].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Если доступно обновление и зашедший игрок имеет права администратора
        if (isUpdateAvailable && player.hasPermission("gravity-gun.admin")) {
            // Отправляем красивое сообщение в чат через 3 секунды после входа

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(lang.getMessage(player, "message.update-checker.update-available", "[GG] Update Available <current-version> -> <new-version> | <link>", Placeholder.parsed("current-version", currentVersion), Placeholder.parsed("new-version", latestVersion), Placeholder.parsed("link", "https://github.com/" + githubRepo + "/releases")));
            }, 60L); // 60 тиков = 3 секунды delay
        }
    }
}