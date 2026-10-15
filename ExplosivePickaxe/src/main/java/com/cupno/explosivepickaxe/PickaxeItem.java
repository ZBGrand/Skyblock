package com.cupno.explosivepickaxe;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PickaxeItem {

    private PickaxeItem() {}

    public static boolean isPickaxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    // ------------------------------------------------------------ enchant cup

    /** Kich thuoc no cua cup, hoac 0 neu khong phai cup no. */
    public static int getSize(ExplosivePickaxe plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        Integer size = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getSizeKey(), PersistentDataType.INTEGER);
        return size == null ? 0 : size;
    }

    /** Enchant cup co san thanh cup no (giu nguyen ten, enchant, do ben...). */
    public static boolean enchant(ExplosivePickaxe plugin, ItemStack pickaxe, int size) {
        if (!isPickaxe(pickaxe)) return false;
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) return false;

        meta.getPersistentDataContainer().set(plugin.getSizeKey(), PersistentDataType.INTEGER, size);

        List<String> lore = stripExplosiveLore(plugin, meta.getLore());
        lore.add(ExplosivePickaxe.color(loreLine(plugin).replace("{size}", String.valueOf(size))));
        meta.setLore(lore);

        pickaxe.setItemMeta(meta);
        return true;
    }

    /** Go enchant no khoi cup. */
    public static boolean remove(ExplosivePickaxe plugin, ItemStack pickaxe) {
        if (getSize(plugin, pickaxe) <= 0) return false;
        ItemMeta meta = pickaxe.getItemMeta();
        meta.getPersistentDataContainer().remove(plugin.getSizeKey());

        List<String> lore = stripExplosiveLore(plugin, meta.getLore());
        meta.setLore(lore.isEmpty() ? null : lore);
        pickaxe.setItemMeta(meta);
        return true;
    }

    private static String loreLine(ExplosivePickaxe plugin) {
        return plugin.getConfig().getString("enchant.lore", "&c⚡ Nổ &e{size}x{size}");
    }

    /** Bo dong lore "Nổ NxN" cu (nhan ra bang phan chu truoc {size}). */
    private static List<String> stripExplosiveLore(ExplosivePickaxe plugin, List<String> lore) {
        List<String> out = new ArrayList<>();
        if (lore == null) return out;

        String template = ChatColor.stripColor(ExplosivePickaxe.color(loreLine(plugin)));
        int idx = template.indexOf("{size}");
        String head = (idx > 0 ? template.substring(0, idx) : template).trim();

        for (String line : lore) {
            String plain = ChatColor.stripColor(line);
            if (!head.isEmpty() && plain != null && plain.trim().startsWith(head)) continue;
            out.add(line);
        }
        return out;
    }

    // ------------------------------------------------------------ sach enchant

    public static ItemStack createBook(ExplosivePickaxe plugin, int size) {
        Material material = Material.matchMaterial(
                plugin.getConfig().getString("book.material", "ENCHANTED_BOOK").toUpperCase(Locale.ROOT));
        if (material == null) material = Material.ENCHANTED_BOOK;

        ItemStack book = new ItemStack(material);
        ItemMeta meta = book.getItemMeta();
        String s = String.valueOf(size);

        meta.setDisplayName(ExplosivePickaxe.color(
                plugin.getConfig().getString("book.name", "&cSách Cúp Nổ {size}x{size}").replace("{size}", s)));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("book.lore")) {
            lore.add(ExplosivePickaxe.color(line.replace("{size}", s)));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(plugin.getBookKey(), PersistentDataType.INTEGER, size);
        book.setItemMeta(meta);
        return book;
    }

    /** Kich thuoc ghi tren sach, hoac 0 neu khong phai sach cup no. */
    public static int getBookSize(ExplosivePickaxe plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        Integer size = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getBookKey(), PersistentDataType.INTEGER);
        return size == null ? 0 : size;
    }
}
