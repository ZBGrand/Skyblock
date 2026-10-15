package com.cupno.explosivepickaxe;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class BreakListener implements Listener {

    private final ExplosivePickaxe plugin;
    private final Random random = new Random();

    /** Nguoi choi dang xu ly no, de tranh de quy khi goi BlockBreakEvent gia. */
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID, Long> lastFullNotice = new HashMap<>();

    public BreakListener(ExplosivePickaxe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (processing.contains(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        int size = PickaxeItem.getSize(plugin, tool);
        if (size <= 0) return;
        if (!player.hasPermission("explosivepickaxe.use")) return;
        boolean sneakOff = player.isSneaking() && plugin.getConfig().getBoolean("mining.sneak-disables", true);

        Block origin = event.getBlock();
        boolean silk = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // Thu thap drop: storable (co the vao kho) va other (vao tui / roi)
        Map<ItemStack, Integer> storable = new LinkedHashMap<>();
        Map<ItemStack, Integer> other = new LinkedHashMap<>();

        // --- Block goc: tu xu ly drop de dua vao kho ---
        if (event.isDropItems()) {
            collectDrops(origin, tool, player, storable, other);
        }
        event.setDropItems(false);

        // --- Ti le no: quyet dinh co pha ca mat phang hay chi block goc ---
        // (block goc luon duoc dua vao kho ExtraStorage, du co no hay khong)
        boolean exploded = !sneakOff && rollExplosion(size);

        // --- Cac block phu tren mat phang ---
        processing.add(player.getUniqueId());
        try {
            List<Block> plane = exploded ? collectPlane(player, origin, size) : List.of();
            for (Block target : plane) {
                if (target.equals(origin)) continue;
                if (!canBreak(target)) continue;
                if (!hasDurabilityLeft(tool)) break;

                boolean dropItems = true;
                if (plugin.getConfig().getBoolean("mining.call-break-event", true)) {
                    BlockBreakEvent fake = new BlockBreakEvent(target, player);
                    plugin.getServer().getPluginManager().callEvent(fake);
                    if (fake.isCancelled()) continue;
                    dropItems = fake.isDropItems();
                }

                Material type = target.getType();
                if (dropItems) collectDrops(target, tool, player, storable, other);
                if (!silk) dropExperience(target, type);

                target.setType(Material.AIR);
                damageTool(tool);
            }
        } finally {
            processing.remove(player.getUniqueId());
        }

        deliver(player, origin, storable, other);
        if (exploded) {
            playEffects(origin);
            if (plugin.getConfig().getBoolean("explosion.notify", false)) {
                plugin.send(player, "exploded", "size", String.valueOf(size));
            }
        }
    }

    // ------------------------------------------------------------------ explosion chance

    /** Ti le no (%) theo kich thuoc; thieu thi dung explosion.default-chance. */
    private boolean rollExplosion(int size) {
        double chance = plugin.getConfig().getDouble("explosion.chances." + size,
                plugin.getConfig().getDouble("explosion.default-chance", 100.0));
        if (chance >= 100.0) return true;
        if (chance <= 0.0) return false;
        return random.nextDouble() * 100.0 < chance;
    }

    // ------------------------------------------------------------------ plane

    /** Tim mat cua block ma nguoi choi dang nhin vao, va toa do tia de chon o 2x2 / 10x10. */
    private List<Block> collectPlane(Player player, Block origin, int size) {
        BlockFace face = null;
        Vector hit = null;

        RayTraceResult ray = player.rayTraceBlocks(8.0);
        if (ray != null && origin.equals(ray.getHitBlock()) && ray.getHitBlockFace() != null) {
            face = ray.getHitBlockFace();
            hit = ray.getHitPosition();
        }
        if (face == null) {
            float pitch = player.getLocation().getPitch();
            if (pitch > 45) face = BlockFace.UP;
            else if (pitch < -45) face = BlockFace.DOWN;
            else face = player.getFacing().getOppositeFace();
        }

        // 2 truc nam tren mat phang vuong goc voi mat bi dap
        int[] u, v;
        double fu = 0.5, fv = 0.5;
        switch (face) {
            case UP, DOWN -> {          // mat phang ngang: X, Z
                u = new int[]{1, 0, 0};
                v = new int[]{0, 0, 1};
                if (hit != null) {
                    fu = hit.getX() - origin.getX();
                    fv = hit.getZ() - origin.getZ();
                }
            }
            case NORTH, SOUTH -> {      // mat phang dung: X, Y
                u = new int[]{1, 0, 0};
                v = new int[]{0, 1, 0};
                if (hit != null) {
                    fu = hit.getX() - origin.getX();
                    fv = hit.getY() - origin.getY();
                }
            }
            default -> {                // EAST, WEST: Z, Y
                u = new int[]{0, 0, 1};
                v = new int[]{0, 1, 0};
                if (hit != null) {
                    fu = hit.getZ() - origin.getZ();
                    fv = hit.getY() - origin.getY();
                }
            }
        }

        int[] ru = range(size, fu);
        int[] rv = range(size, fv);

        List<Block> blocks = new ArrayList<>(size * size);
        blocks.add(origin);
        for (int i = ru[0]; i <= ru[1]; i++) {
            for (int j = rv[0]; j <= rv[1]; j++) {
                if (i == 0 && j == 0) continue;
                blocks.add(origin.getRelative(
                        u[0] * i + v[0] * j,
                        u[1] * i + v[1] * j,
                        u[2] * i + v[2] * j));
            }
        }
        return blocks;
    }

    /**
     * Khoang offset [min, max] quanh block goc.
     * Kich thuoc le: doi xung. Kich thuoc chan (2, 10): lech ve phia diem dap (frac < 0.5 -> lech am).
     */
    private int[] range(int size, double frac) {
        if (size % 2 == 1) {
            int h = size / 2;
            return new int[]{-h, h};
        }
        int h = size / 2;
        return frac < 0.5 ? new int[]{-h, h - 1} : new int[]{-(h - 1), h};
    }

    // ------------------------------------------------------------------ rules

    private boolean canBreak(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (type.getHardness() < 0) return false;                 // bedrock, barrier...
        if (plugin.getBlacklist().contains(type)) return false;
        if (block.getState() instanceof Container) return false;  // khong pha ruong (mat do)
        if (plugin.getConfig().getBoolean("mining.only-pickaxe-blocks", true)
                && !Tag.MINEABLE_PICKAXE.isTagged(type)) return false;
        return true;
    }

    private boolean isOre(Material type) {
        String name = type.name();
        return name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS || plugin.getExtraOres().contains(type);
    }

    // ------------------------------------------------------------------ drops

    private void collectDrops(Block block, ItemStack tool, Player player,
                              Map<ItemStack, Integer> storable, Map<ItemStack, Integer> other) {
        boolean toStorage = plugin.getConfig().getString("storage.mode", "ALL").equalsIgnoreCase("ALL")
                || isOre(block.getType());
        Collection<ItemStack> drops = block.getDrops(tool, player);
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            ItemStack key = drop.clone();
            key.setAmount(1);
            (toStorage ? storable : other).merge(key, drop.getAmount(), Integer::sum);
        }
    }

    private void deliver(Player player, Block origin,
                         Map<ItemStack, Integer> storable, Map<ItemStack, Integer> other) {
        boolean full = false;

        for (Map.Entry<ItemStack, Integer> e : storable.entrySet()) {
            ItemStack stack = e.getKey().clone();
            stack.setAmount(e.getValue());

            ExtraStorageHook.Result result = plugin.getStorageHook().store(player, stack);
            if (result.full()) full = true;

            int left = e.getValue() - result.stored();
            if (left > 0) other.merge(e.getKey(), left, Integer::sum);
        }

        if (full && plugin.getConfig().getBoolean("storage.notify-full", true)) {
            long now = System.currentTimeMillis();
            Long last = lastFullNotice.get(player.getUniqueId());
            if (last == null || now - last > 5000) {
                lastFullNotice.put(player.getUniqueId(), now);
                plugin.send(player, "storage-full");
            }
        }

        boolean intoInventory = plugin.getConfig().getString("storage.overflow", "DROP")
                .equalsIgnoreCase("INVENTORY");
        Location dropLoc = origin.getLocation().add(0.5, 0.5, 0.5);

        for (Map.Entry<ItemStack, Integer> e : other.entrySet()) {
            int remaining = e.getValue();
            int max = Math.max(1, e.getKey().getMaxStackSize());
            while (remaining > 0) {
                ItemStack part = e.getKey().clone();
                part.setAmount(Math.min(max, remaining));
                remaining -= part.getAmount();

                if (intoInventory) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(part);
                    for (ItemStack rest : leftover.values()) {
                        origin.getWorld().dropItemNaturally(dropLoc, rest);
                    }
                } else {
                    origin.getWorld().dropItemNaturally(dropLoc, part);
                }
            }
        }
    }

    private void dropExperience(Block block, Material type) {
        int xp = switch (type) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> random.nextInt(3);
            case NETHER_GOLD_ORE -> random.nextInt(2);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 3 + random.nextInt(5);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> 2 + random.nextInt(4);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 1 + random.nextInt(5);
            default -> 0;
        };
        if (xp > 0) {
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
    }

    // ------------------------------------------------------------------ durability

    private boolean hasDurabilityLeft(ItemStack tool) {
        if (!plugin.getConfig().getBoolean("mining.durability-per-block", true)) return true;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) return true;
        // chua lai it nhat 1 do ben de cac co che mac dinh cua server tu xu ly viec vo cup
        return damageable.getDamage() + 2 < tool.getType().getMaxDurability();
    }

    private void damageTool(ItemStack tool) {
        if (!plugin.getConfig().getBoolean("mining.durability-per-block", true)) return;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) return;

        int unbreaking = tool.getEnchantmentLevel(Enchantment.DURABILITY);
        if (unbreaking > 0 && random.nextInt(unbreaking + 1) != 0) return;

        damageable.setDamage(damageable.getDamage() + 1);
        tool.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ effects

    private void playEffects(Block origin) {
        Location center = origin.getLocation().add(0.5, 0.5, 0.5);
        if (plugin.getConfig().getBoolean("effects.particle", true)) {
            origin.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        }
        if (plugin.getConfig().getBoolean("effects.sound", true)) {
            origin.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        }
    }
}
