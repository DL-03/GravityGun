package me.dl.GravityGun;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractCommand implements CommandExecutor, TabCompleter {
    private final String commandName;

    public AbstractCommand(String command) {
        this.commandName = command;
    }

    public void register() {
        PluginCommand pluginCommand = GravityGun.getInstance().getCommand(commandName);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    public abstract void execute(CommandSender var1, String var2, String[] var3);

    public List<String> complete(CommandSender sender, String[] args) {
        return null;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        this.execute(sender, label, args);
        return true;
    }

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return this.filter(this.complete(sender, args), args);
    }

    private List<String> filter(List<String> list, String[] args) {
        if (list == null) {
            return null;
        } else {
            String last = args[args.length - 1];
            List<String> result = new ArrayList();
            Iterator var5 = list.iterator();

            while(var5.hasNext()) {
                String arg = (String)var5.next();
                if (arg.toLowerCase().startsWith(last.toLowerCase())) {
                    result.add(arg);
                }
            }

            return result;
        }
    }
}
