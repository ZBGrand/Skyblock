package com.cupno.explosivepickaxe;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ExplosivePickaxe extends JavaPlugin {

    private NamespacedKey sizeKey;
    private NamespacedKey bookKey;
    private ExtraStorageHook storageHook;

    // ---- cache config ----
    private List<Integer> sizes = new ArrayList<>();
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);
    private final Set<Material> extraOres = EnumSet.noneOf(Material.class);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.sizeKey = new NamespacedKey(this, "explosive_size");
        this.bookKey = new NamespacedKey(this, "explosive_book");
        this.storageHook = new ExtraStorageHook(this);
        loadSettings();

        getServer().getPluginManager().registerEvents(new BreakListener(this), this);
        getServer().getPluginManager().registerEvents(new BookListener(this), this);

        PickaxeCommand command = new PickaxeCommand(this);
        if (getCommand("cupno") != null) {
            getCommand("cupno").setExecutor(command);
            getCommand("cupno").setTabCompleter(command);
        }

        if (storageHook.init()) {
            getLogger().info("Da ket noi ExtraStorage - khoang san se vao thang kho.");
        } else {
            getLogger().warning("Khong tim thay ExtraStorage! Vat pham se vao tui do thay vi kho.");
        }
    }

    public void loadSettings() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        sizes = new ArrayList<>();
        for (int s : cfg.getIntegerList("sizes")) {
            if (s >= 1 && s <= 25) sizes.add(s);
        }
        if (sizes.isEmpty()) sizes = List.of(2, 3, 5, 7, 10);

        fillMaterials(blacklist, cfg.getStringList("mining.blacklist"));
        fillMaterials(extraOres, cfg.getStringList("storage.extra-ores"));
    }

    private void fillMaterials(Set<Material> target, List<String> names) {
        target.clear();
        for (String name : names) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m != null) target.add(m);
            else getLogger().warning("Material khong hop le trong config: " + name);
        }
    }

    // ---- getters ----
    public NamespacedKey getSizeKey() { return sizeKey; }
    public NamespacedKey getBookKey() { return bookKey; }
    public ExtraStorageHook getStorageHook() { return storageHook; }
    public List<Integer> getSizes() { return sizes; }
    public Set<Material> getBlacklist() { return blacklist; }
    public Set<Material> getExtraOres() { return extraOres; }

    // ---- messages ----
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public String raw(String key) {
        return getConfig().getString("messages." + key, key);
    }

    /** replacements: key1, value1, key2, value2 ... (key khong co dau {}) */
    public void send(CommandSender to, String key, String... replacements) {
        String msg = raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        to.sendMessage(color(getConfig().getString("messages.prefix", "") + msg));
    }

    public void sendList(CommandSender to, String key) {
        for (String line : getConfig().getStringList("messages." + key)) {
            to.sendMessage(color(line));
        }
    }
}
