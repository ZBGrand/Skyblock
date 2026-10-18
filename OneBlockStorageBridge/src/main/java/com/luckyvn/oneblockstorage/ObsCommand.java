package com.luckyvn.oneblockstorage;

import com.luckyvn.oneblockcore.storage.StorageFeature;
import com.luckyvn.oneblockcore.storage.service.WithdrawResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class ObsCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "obstorage.use";
    private static final String PERM_ADMIN = "obstorage.admin";
    private static final int MAX_WITHDRAW_REPEAT = 64;
    private static final int TAB_LIMIT = 50;

    private static final List<String> BLOCK_NAMES = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy() && m.isBlock() && m.isItem())
            .map(m -> m.name().toLowerCase(Locale.ROOT))
            .sorted()
            .toList();

    private final OneBlockStorageBridge bridge;

    public ObsCommand(OneBlockStorageBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            bridge.send(sender, "help");
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "balance", "bal" -> balance(sender, args);
                case "withdraw", "rut" -> withdraw(sender, args);
                case "add" -> add(sender, args);
                case "reload" -> reload(sender);
                default -> bridge.send(sender, "help");
            }
        } catch (Exception ex) {
            bridge.send(sender, "error");
            bridge.getLogger().log(Level.SEVERE, "Lỗi khi xử lý /obs " + String.join(" ", args), ex);
        }
        return true;
    }

    // ---------------------------------------------------------------- balance

    private void balance(CommandSender sender, String[] args) throws Exception {
        if (!sender.hasPermission(PERM_USE)) {
            bridge.send(sender, "no-permission");
            return;
        }
        String matArg = args.length > 1 ? args[1] : "all";
        Material material = null;
        if (!matArg.equalsIgnoreCase("all")) {
            material = Material.matchMaterial(matArg);
            if (material == null) {
                bridge.send(sender, "unknown-material", "input", matArg);
                return;
            }
        }

        OfflinePlayer target;
        if (args.length > 2) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                bridge.send(sender, "no-permission");
                return;
            }
            target = resolve(args[2]);
            if (target == null) {
                bridge.send(sender, "player-not-found", "input", args[2]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            bridge.send(sender, "only-player");
            return;
        }

        StorageFeature storage = bridge.storage();
        if (storage == null) {
            bridge.send(sender, "not-ready");
            return;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : uuid.toString();
        var store = storage.getStore();

        if (material != null) {
            bridge.send(sender, "balance-single",
                    "player", name,
                    "material", pretty(material),
                    "amount", String.valueOf(store.getBalance(uuid, material)));
            return;
        }

        bridge.send(sender, "balance-header",
                "player", name,
                "total", String.valueOf(store.getTotalBalance(uuid)),
                "capacity", String.valueOf(storage.getConfig().getCapacity()),
                "pending", String.valueOf(store.getPendingReservedAmount(uuid)));

        List<Map.Entry<Material, Long>> entries = new ArrayList<>(store.getBalances(uuid).entrySet());
        entries.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        entries.sort(Map.Entry.<Material, Long>comparingByValue().reversed());
        if (entries.isEmpty()) {
            bridge.send(sender, "balance-empty");
            return;
        }
        for (Map.Entry<Material, Long> entry : entries.stream().limit(10).toList()) {
            bridge.send(sender, "balance-entry",
                    "material", pretty(entry.getKey()),
                    "amount", String.valueOf(entry.getValue()));
        }
    }

    // --------------------------------------------------------------- withdraw

    private void withdraw(CommandSender sender, String[] args) throws Exception {
        if (!(sender instanceof Player player)) {
            bridge.send(sender, "only-player");
            return;
        }
        if (!sender.hasPermission(PERM_USE)) {
            bridge.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            bridge.send(sender, "help");
            return;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            bridge.send(sender, "unknown-material", "input", args[1]);
            return;
        }
        int times = 1;
        if (args.length > 2) {
            Integer parsed = parsePositiveInt(args[2]);
            if (parsed == null) {
                bridge.send(sender, "invalid-amount");
                return;
            }
            times = Math.min(parsed, MAX_WITHDRAW_REPEAT);
        }

        long total = 0;
        int done = 0;
        String lastStatus = "";
        for (int i = 0; i < times; i++) {
            // Lấy lại feature mỗi lần, không giữ tham chiếu lâu dài.
            StorageFeature storage = bridge.storage();
            if (storage == null) {
                bridge.send(sender, "not-ready");
                break;
            }
            // Service tự gửi thông báo cho người chơi.
            WithdrawResult result = storage.getService().withdraw(player, material);
            done++;
            lastStatus = result.status().name();
            if (lastStatus.equals("SUCCESS") || lastStatus.equals("PARTIAL")) {
                total += result.actualAmount();
            }
            // Chỉ lặp tiếp khi lần này thành công trọn vẹn; AMBIGUOUS/ERROR không retry.
            if (!lastStatus.equals("SUCCESS")) {
                break;
            }
        }

        bridge.invalidate(player.getUniqueId());

        if (lastStatus.equals("AMBIGUOUS")) {
            bridge.send(sender, "withdraw-ambiguous");
            bridge.getLogger().warning("Rút AMBIGUOUS: player=" + player.getName()
                    + " uuid=" + player.getUniqueId() + " material=" + material
                    + ". Cần đối soát journal, không tự hoàn/phát lại.");
        } else if (times > 1 && total > 0) {
            bridge.send(sender, "withdraw-summary",
                    "amount", String.valueOf(total),
                    "material", pretty(material),
                    "times", String.valueOf(done));
        }
    }

    // -------------------------------------------------------------------- add

    private void add(CommandSender sender, String[] args) throws Exception {
        if (!sender.hasPermission(PERM_ADMIN)) {
            bridge.send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            bridge.send(sender, "help");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            bridge.send(sender, "player-not-found", "input", args[1]);
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null) {
            bridge.send(sender, "unknown-material", "input", args[2]);
            return;
        }
        Integer amount = parsePositiveInt(args[3]);
        if (amount == null) {
            bridge.send(sender, "invalid-amount");
            return;
        }
        StorageFeature storage = bridge.storage();
        if (storage == null) {
            bridge.send(sender, "not-ready");
            return;
        }
        if (!storage.isAllowed(material)) {
            bridge.send(sender, "not-allowed", "material", pretty(material));
            return;
        }

        // reserve -> commit để tôn trọng dung lượng kho; không dùng addBalance().
        var ticket = storage.reserve(target.getUniqueId(), material, amount);
        if (ticket == null) {
            bridge.send(sender, "add-failed");
            return;
        }
        boolean committed = storage.commit(ticket);
        if (!committed) {
            bridge.send(sender, "add-commit-failed");
            bridge.getLogger().warning("Commit thất bại: target=" + target.getUniqueId()
                    + " material=" + material + " amount=" + amount + " by=" + sender.getName());
            return;
        }
        bridge.invalidate(target.getUniqueId());
        bridge.getLogger().info(sender.getName() + " đã thêm " + amount + " " + material
                + " vào kho của " + target.getName() + " (" + target.getUniqueId() + ")");
        bridge.send(sender, "add-success",
                "amount", String.valueOf(amount),
                "material", pretty(material),
                "player", target.getName() != null ? target.getName() : target.getUniqueId().toString());
    }

    // ----------------------------------------------------------------- reload

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            bridge.send(sender, "no-permission");
            return;
        }
        bridge.reload();
        bridge.send(sender, "reloaded");
    }

    // ---------------------------------------------------------------- helpers

    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached;
    }

    private static Integer parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("balance", "withdraw"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.add("add");
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(PERM_ADMIN);

        switch (sub) {
            case "balance", "bal" -> {
                if (args.length == 2) {
                    List<String> options = new ArrayList<>(ownedMaterials(sender));
                    options.add("all");
                    return filter(options, args[1]);
                }
                if (args.length == 3 && admin) {
                    return filter(onlineNames(), args[2]);
                }
            }
            case "withdraw", "rut" -> {
                if (args.length == 2) {
                    List<String> owned = ownedMaterials(sender);
                    return filter(owned.isEmpty() ? BLOCK_NAMES : owned, args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("1", "5", "10", "64"), args[2]);
                }
            }
            case "add" -> {
                if (!admin) {
                    return List.of();
                }
                if (args.length == 2) {
                    return filter(onlineNames(), args[1]);
                }
                if (args.length == 3) {
                    return filter(BLOCK_NAMES, args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("1", "64", "640"), args[3]);
                }
            }
            default -> {
            }
        }
        return List.of();
    }

    private List<String> ownedMaterials(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        StorageFeature storage = bridge.storage();
        if (storage == null) {
            return List.of();
        }
        try {
            return storage.getStore().getBalances(player.getUniqueId()).entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue() > 0)
                    .map(e -> pretty(e.getKey()))
                    .sorted()
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .limit(TAB_LIMIT)
                .toList();
    }
}
