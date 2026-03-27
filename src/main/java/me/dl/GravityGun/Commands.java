package me.dl.GravityGun;

import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class Commands extends AbstractCommand {
    public Commands() {
        super("gg");
    }

    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Component.text("/gg help").color(NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/gg help")).append(Component.text(" - show this help").color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/gg reload").color(NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/gg reload")).append(Component.text(" - reload the plugin configuration").color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/gg give [player]").color(NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/gg give")).append(Component.text(" - give the gravity gun to yourself or another player").color(NamedTextColor.WHITE)));
        } else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gravity-gun.reload")) {
                sender.sendMessage("You do not have permission to do this");
            } else {
                GravityGun.getInstance().reload();
                sender.sendMessage("Configuration reloaded");
            }
        } else if (args[0].equalsIgnoreCase("give")) {
            Player player;
            if (!sender.hasPermission("gravity-gun.give")) {
                sender.sendMessage("You do not have permission to do this");
                return;
            }
            if (args.length > 1) {
                player = GravityGun.getInstance().getServer().getPlayer(args[1]);
                if (player == null) {
                    sender.sendMessage("Player " + args[1] + " not found");
                    return;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("The console cannot use this command");
                    return;
                }
                player = (Player) sender;
            }

            GravityGun.manager.giveTool(player);
            sender.sendMessage("You gave " + player.getName() + " the gravity gun");
        } else {
            sender.sendMessage("Unknown command. Use /gg help for help");
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
