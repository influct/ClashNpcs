package de.oliver.commands;

import de.oliver.Npc;
import de.oliver.NpcPlugin;
import de.oliver.utils.SkinFetcher;
import de.oliver.utils.UUIDFetcher;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class NpcCMD implements CommandExecutor, TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if(args.length == 1){
            return Arrays.asList("create", "remove", "skin", "movehere");
        } else if(args.length == 2 && !args[0].equalsIgnoreCase("create")){
            return NpcPlugin.getInstance().getNpcManager().getAllNpcs().stream().map(Npc::getName).toList();
        }

        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if(!(sender instanceof Player p)){
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can execute this command</red>"));
            return false;
        }

        /*
            /npc create <name>
            /npc remove <name>
            /npc skin <name> <skin>
            /npc movehere <name>

         */

        if (args.length < 2){
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Wrong usage: /npc help</red>"));
            return false;
        }

        String subcommand = args[0];
        String name = args[1];

        switch (subcommand.toLowerCase()){
            case "create" -> {
                Npc npc = new Npc(name, p.getLocation());
                npc.create();
                npc.spawnForAll();

                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Created new npc</green>"));
            }

            case "remove" -> {
                Npc npc = NpcPlugin.getInstance().getNpcManager().getNpc(name);
                if(npc == null){
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not find npc</red>"));
                    return false;
                }

                npc.removeForAll();
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Removed npc</green>"));
            }

            case "movehere" -> {
                Npc npc = NpcPlugin.getInstance().getNpcManager().getNpc(name);
                if(npc == null){
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not find npc</red>"));
                    return false;
                }

                npc.moveForAll(p.getLocation());
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Moved npc to your location</green>"));
            }

            case "skin" -> {
                if(args.length < 3){
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Wrong usage: /npc help</red>"));
                    return false;
                }

                String skinName = args[2];

                Npc npc = NpcPlugin.getInstance().getNpcManager().getNpc(name);
                if(npc == null){
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not find npc</red>"));
                    return false;
                }

                SkinFetcher skinFetcher = new SkinFetcher(UUIDFetcher.getUUID(skinName).toString());
                npc.updateSkin(skinFetcher);
            }

            default -> {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Wrong usage: /npc help</red>"));
                return false;
            }
        }

        return false;
    }
}
