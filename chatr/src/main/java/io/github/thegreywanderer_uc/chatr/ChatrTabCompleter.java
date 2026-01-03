package io.github.thegreywanderer_uc.chatr;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tab completion for the /chatr command.
 */
public class ChatrTabCompleter implements TabCompleter {
    
    private final Chatr plugin;
    
    // Static subcommands
    private static final List<String> BASE_COMMANDS = Arrays.asList(
            "version", "reload", "create", "remove", "skin", "color", "info", "r", "reply",
            "reload-npc", "clear", "stats", "metrics", "cache", "serverai"
    );
    
    // Subcommands that take NPC name as second argument
    private static final List<String> NPC_COMMANDS = Arrays.asList(
            "remove", "skin", "color", "reload-npc", "clear"
    );
    
    public ChatrTabCompleter(Chatr plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - show subcommands and NPC names
            String partial = args[0].toLowerCase();
            
            // Add base commands
            for (String cmd : BASE_COMMANDS) {
                if (cmd.startsWith(partial) && hasPermissionFor(sender, cmd)) {
                    completions.add(cmd);
                }
            }
            
            // Add NPC names for direct AI chat (/chatr <npc> ai <message>)
            if (sender.hasPermission("chatr.admin")) {
                for (String npcName : plugin.getNpcNames()) {
                    if (npcName.toLowerCase().startsWith(partial)) {
                        completions.add(npcName);
                    }
                }
            }
            
        } else if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            
            // Commands that take NPC name as second argument
            if (NPC_COMMANDS.contains(firstArg)) {
                for (String npcName : plugin.getNpcNames()) {
                    if (npcName.toLowerCase().startsWith(partial)) {
                        completions.add(npcName);
                    }
                }
            }
            
            // If first arg is an NPC name, suggest "ai"
            if (plugin.getNpcNames().contains(firstArg)) {
                if ("ai".startsWith(partial)) {
                    completions.add("ai");
                }
            }
            
            // stats subcommands
            if (firstArg.equals("stats") || firstArg.equals("metrics")) {
                List<String> statsOptions = Arrays.asList("summary", "npcs", "players", "npc");
                for (String opt : statsOptions) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
            
            // cache subcommands
            if (firstArg.equals("cache")) {
                List<String> cacheOptions = Arrays.asList("stats", "clear");
                for (String opt : cacheOptions) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
            
            // serverai subcommands
            if (firstArg.equals("serverai") || firstArg.equals("ai")) {
                List<String> serverAiOptions = Arrays.asList("status", "refresh", "clear");
                for (String opt : serverAiOptions) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
            
        } else if (args.length == 3) {
            String firstArg = args[0].toLowerCase();
            String secondArg = args[1].toLowerCase();
            String partial = args[2].toLowerCase();
            
            // /chatr skin <npc> <player>
            if (firstArg.equals("skin") && plugin.getNpcNames().stream()
                    .anyMatch(n -> n.equalsIgnoreCase(secondArg))) {
                // Suggest online player names for skin
                return null; // Return null for default player completion
            }
            
            // /chatr color <npc> <type>
            if (firstArg.equals("color") && plugin.getNpcNames().stream()
                    .anyMatch(n -> n.equalsIgnoreCase(secondArg))) {
                List<String> colorTypes = Arrays.asList("npc-name", "instruction", "cancel");
                for (String type : colorTypes) {
                    if (type.startsWith(partial)) {
                        completions.add(type);
                    }
                }
            }
            
            // /chatr stats npc <npcname>
            if ((firstArg.equals("stats") || firstArg.equals("metrics")) && secondArg.equals("npc")) {
                for (String npcName : plugin.getNpcNames()) {
                    if (npcName.toLowerCase().startsWith(partial)) {
                        completions.add(npcName);
                    }
                }
            }
            
            // /chatr cache clear <npcname>
            if (firstArg.equals("cache") && secondArg.equals("clear")) {
                if ("all".startsWith(partial)) {
                    completions.add("all");
                }
                for (String npcName : plugin.getNpcNames()) {
                    if (npcName.toLowerCase().startsWith(partial)) {
                        completions.add(npcName);
                    }
                }
            }
        } else if (args.length == 4) {
            String firstArg = args[0].toLowerCase();
            String secondArg = args[1].toLowerCase();
            String thirdArg = args[2].toLowerCase();
            String partial = args[3].toLowerCase();
            
            // /chatr color <npc> <type> <color_code>
            if (firstArg.equals("color") && plugin.getNpcNames().stream()
                    .anyMatch(n -> n.equalsIgnoreCase(secondArg))) {
                // Suggest common color codes and "default"
                List<String> colorCodes = Arrays.asList("default", "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f", "&l", "&o", "&n", "&m", "&k");
                for (String code : colorCodes) {
                    if (code.startsWith(partial)) {
                        completions.add(code);
                    }
                }
            }
        }
        
        return completions;
    }
    
    /**
     * Check if sender has permission for a specific subcommand
     */
    private boolean hasPermissionFor(CommandSender sender, String subCommand) {
        switch (subCommand) {
            case "version":
                return true; // Everyone can see version
            case "reload":
            case "reload-npc":
                return sender.hasPermission("chatr.reload");
            case "create":
                return sender.hasPermission("chatr.create");
            case "remove":
                return sender.hasPermission("chatr.remove");
            case "skin":
                return sender.hasPermission("chatr.skin");
            case "color":
                return sender.hasPermission("chatr.color");
            case "r":
            case "reply":
                return sender.hasPermission("chatr.ai");
            case "clear":
                return sender.hasPermission("chatr.ai");
            case "stats":
            case "metrics":
            case "info":
                return sender.hasPermission("chatr.admin");
            case "cache":
                return sender.hasPermission("chatr.admin");
            case "serverai":
                return sender.hasPermission("chatr.admin");
            default:
                return true;
        }
    }
}
