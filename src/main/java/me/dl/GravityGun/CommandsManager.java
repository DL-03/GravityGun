package me.dl.GravityGun;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class CommandsManager {
    private final LanguageManager lang;

    GravityGun gravityGun;

    public CommandsManager(GravityGun _gravityGun) {
        gravityGun = _gravityGun;
        lang = GravityGun.langManager;

        List<String> commandNames = List.of("gravity-gun","gg");

        gravityGun.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event->{
            Commands commands = event.registrar();

            var commandBuilder = Commands.literal(commandNames.getFirst())
                    //.requires(source -> source.getSender().hasPermission("gravity-gun"))

                        // Help
                        .executes(ctx->{
                            CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage(lang.getMessage(sender, "message.gg.help", "[GG] /gg (help|reload|give [target])"));
                            return 1;
                        })

                        .then(Commands.literal("help")
                        .executes(ctx->{
                            CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage(lang.getMessage(sender, "message.gg.help", "[GG] /gg (help|reload|give [target])"));
                            return 1;
                        }))

                        // Config reload
                        .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("gravity-gun.reload"))
                        .executes(ctx->{
                            gravityGun.reload();
                            CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage(lang.getMessage(sender, "message.config-reloaded", "[GG] Config reloaded"));
                            return 1;
                        }))

                        // Give Gravity gun to player
                        .then(Commands.literal("give")
                        .requires(source -> source.getSender().hasPermission("gravity-gun.give"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();

                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(lang.getMessage(lang.serverLanguage, "message.console-cant-use-command", "[GG] Console can`t use this command"));
                                return 0;
                            } else {
                                GravityGun.manager.giveTool(player);
                                sender.sendMessage(lang.getMessage(sender, "message.gg.give.success", "Gravity Gun given: <player>", Placeholder.parsed("player", player.getName())));
                                return 1;
                            }
                        })
                            .then(Commands.argument("target", ArgumentTypes.players())
                            .executes(ctx->{
                                PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                Collection<Player> targets = targetResolver.resolve(ctx.getSource());

                                CommandSender sender = ctx.getSource().getSender();

                                if (targets.isEmpty()) {
                                    sender.sendMessage(lang.getMessage(sender, "message.players-not-found", "Players not found"));
                                    return 0;
                                }

                                for (Player target : targets) {
                                    GravityGun.manager.giveTool(target);
                                    sender.sendMessage(lang.getMessage(sender, "message.gg.give.success", "Gravity Gun given: <player>", Placeholder.parsed("player", target.getName())));
                                }

                                return 1;
                            })))

                    // Fix player
                    .then(Commands.literal("fix")
                    .requires(source -> source.getSender().hasPermission("gravity-gun.fix"))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();

                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(lang.getMessage(lang.serverLanguage, "message.console-cant-use-command", "[GG] Console can`t use this command"));
                            return 0;
                        } else {
                            player.setGravity(true);
                            player.setCollidable(true);

                            sender.sendMessage(lang.getMessage(sender, "message.gg.fix.success", "[GG] <player> fixed!", Placeholder.parsed("player", player.getName())));
                            return 1;
                        }
                    })
                    .then(Commands.argument("target", ArgumentTypes.players())
                            .executes(ctx->{
                                PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                Collection<Player> targets = targetResolver.resolve(ctx.getSource());

                                CommandSender sender = ctx.getSource().getSender();

                                if (targets.isEmpty()) {
                                    sender.sendMessage(lang.getMessage(sender, "message.players-not-found", "Players not found"));
                                    return 0;
                                }

                                for (Player target : targets) {
                                    target.setGravity(true);
                                    target.setCollidable(true);

                                    sender.sendMessage(lang.getMessage(sender, "message.gg.fix.success", "[GG] <player> fixed!", Placeholder.parsed("player", target.getName())));
                                }

                                return 1;
                            })))
                    ;

            // Building main command
            LiteralCommandNode<CommandSourceStack> mainNode = commandBuilder.build();

            // Register main command
            commands.register(mainNode, "Main plugin command");

            // Register aliases
            for (int i = 1; i < commandNames.size(); i++) {
                commands.register(
                        Commands.literal(commandNames.get(i)).redirect(mainNode).build(),
                        "Alias for " + commandNames.getFirst()
                );
            }
        });
    }
}
