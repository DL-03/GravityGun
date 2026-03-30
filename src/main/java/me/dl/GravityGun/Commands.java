package me.dl.GravityGun;

import com.google.common.collect.Lists;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class Commands extends AbstractCommand {
    private final GravityGun plugin;
    private final LanguageManager lang;

    public Commands() {
        super("gg");
        this.plugin = GravityGun.getInstance();
        this.lang = GravityGun.langManager;
    }

    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(lang.getMessage(sender, "message.gg.help", "Help message"));
        } else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gravity-gun.reload")) {
                sender.sendMessage(lang.getMessage(sender, "message.no-permission", "No permission"));
            } else {
                GravityGun.getInstance().reload();
                sender.sendMessage(lang.getMessage(sender, "message.config-reloaded", "Config reloaded"));
            }
        } else if (args[0].equalsIgnoreCase("give")) {
            Player player;
            if (!sender.hasPermission("gravity-gun.give")) {
                sender.sendMessage(lang.getMessage(sender, "message.no-permission", "No permission"));
                return;
            }
            if (args.length > 1) {
                player = plugin.getServer().getPlayer(args[1]);
                if (player == null) {
                    sender.sendMessage(lang.getMessage(sender, "message.unknown-player", "Unknown player: <player>", Placeholder.parsed("player", args[1])));
                    return;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(lang.getMessage(lang.serverLanguage, "message.console-cant-use-command", "Console can`t"));
                    return;
                }
                player = (Player) sender;
            }

            GravityGun.manager.giveTool(player);
            sender.sendMessage(lang.getMessage(sender, "message.gg.give.success", "Gravity Gun given: <player>", Placeholder.parsed("player", player.getName())));
        } else {
            sender.sendMessage(lang.getMessage(sender, "message.gg.unknown-command", "Unknown command"));
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0 || args.length == 1) {
            return Lists.newArrayList(new String[]{"help", "reload", "give"});
        } else {
            if (args.length == 1 || args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return null;
            }
            return Lists.newArrayList();
        }
    }
}
