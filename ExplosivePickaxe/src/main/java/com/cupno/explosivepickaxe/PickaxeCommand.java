package com.cupno.explosivepickaxe;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PickaxeCommand implements CommandExecutor, TabCompleter {

    private final ExplosivePickaxe plugin;

    public PickaxeCommand(ExplosivePickaxe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("explosivepickaxe.admin")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.sendList(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enchant" -> {
                if (!(sender instanceof Player player)) { plugin.send(sender, "player-not-found"); return true; }
                if (args.length < 2) { plugin.sendList(sender, "usage"); return true; }
                Integer size = parseSize(sender, args[1]);
                if (size == null) return true;

                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!PickaxeItem.enchant(plugin, hand, size)) {
                    plugin.send(sender, "not-pickaxe");
                    return true;
                }
                player.getInventory().setItemInMainHand(hand);
                plugin.send(sender, "enchanted", "size", String.valueOf(size));
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) { plugin.send(sender, "player-not-found"); return true; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!PickaxeItem.isPickaxe(hand)) { plugin.send(sender, "not-pickaxe"); return true; }
                if (!PickaxeItem.remove(plugin, hand)) { plugin.send(sender, "not-explosive"); return true; }
                player.getInventory().setItemInMainHand(hand);
                plugin.send(sender, "removed");
            }
            case "book" -> {
                if (args.length < 3) { plugin.sendList(sender, "usage"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { plugin.send(sender, "player-not-found"); return true; }
                Integer size = parseSize(sender, args[2]);
                if (size == null) return true;

                Map<Integer, ItemStack> left = target.getInventory().addItem(PickaxeItem.createBook(plugin, size));
                left.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));

                plugin.send(sender, "book-given", "player", target.getName(), "size", String.valueOf(size));
                plugin.send(target, "book-received", "size", String.valueOf(size));
            }
            case "reload" -> {
                plugin.loadSettings();
                plugin.send(sender, "reloaded");
            }
            default -> plugin.sendList(sender, "usage");
        }
        return true;
    }

    private Integer parseSize(CommandSender sender, String input) {
        try {
            int size = Integer.parseInt(input);
            if (plugin.getSizes().contains(size)) return size;
        } catch (NumberFormatException ignored) {
        }
        plugin.send(sender, "invalid-size", "sizes",
                plugin.getSizes().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("explosivepickaxe.admin")) return out;

        if (args.length == 1) {
            out.addAll(List.of("enchant", "remove", "book", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("book"))
                || (args.length == 2 && args[0].equalsIgnoreCase("enchant"))) {
            plugin.getSizes().forEach(s -> out.add(String.valueOf(s)));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
