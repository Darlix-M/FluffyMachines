package io.ncbpfluffybear.fluffymachines.items.tools;

import io.github.thebusybiscuit.slimefun4.api.events.ExplosiveToolBreakBlocksEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.core.handlers.ToolUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.ExplosiveTool;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * This {@link SlimefunItem} is a super class for items like the {@link UpgradedExplosivePickaxe} or {@link
 * UpgradedExplosiveShovel}.
 *
 * @author TheBusyBiscuit, NCBPFluffyBear
 * @see UpgradedExplosivePickaxe
 * @see UpgradedExplosiveShovel
 */
class UpgradedExplosiveTool extends ExplosiveTool {

    private final ItemSetting<Boolean> damageOnUse;
    private final ItemSetting<Boolean> callExplosionEvent;
    private final ItemSetting<Boolean> breakFromCenter = new ItemSetting<>(this, "break-from-center", false);
    private final ItemSetting<Boolean> triggerOtherPlugins = new ItemSetting<>(this, "trigger-other-plugins", true);

    public UpgradedExplosiveTool(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        addItemSetting(breakFromCenter, triggerOtherPlugins);

        damageOnUse = getItemSetting("damage-on-use", Boolean.class).get();
        callExplosionEvent = getItemSetting("call-explosion-event", Boolean.class).get();
    }

    @Nonnull
    @Override
    public ToolUseHandler getItemHandler() {
        return (e, tool, fortune, drops) -> {
            if (e instanceof AlternateBreakEvent) {
                return;
            }

            Player p = e.getPlayer();
            Block b = e.getBlock();
            org.bukkit.World world = b.getWorld();
            org.bukkit.Location blockLoc = b.getLocation();

            world.createExplosion(blockLoc, 0.0F);
            world.playSound(blockLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.2F, 1F);

            BlockFace face = p.getFacing();
            float pitch = p.getLocation().getPitch();
            if (pitch > 67.5F) {
                face = BlockFace.DOWN;
            } else if (pitch < -67.5F) {
                face = BlockFace.UP;
            }
            
            List<Block> blocks = findBlocks(b, face);
            breakBlocks(p, tool, b, blocks, drops);
        };
    }

    private void breakBlocks(Player p, ItemStack item, Block b, List<Block> blocks, List<ItemStack> drops) {
        boolean shouldCallExplosionEvent = callExplosionEvent.getValue();
        List<Block> blocksToDestroy = new ArrayList<>(blocks.size());

        if (shouldCallExplosionEvent) {
            BlockExplodeEvent blockExplodeEvent = new BlockExplodeEvent(b, blocks, 0);
            Bukkit.getServer().getPluginManager().callEvent(blockExplodeEvent);

            if (!blockExplodeEvent.isCancelled()) {
                for (Block block : blockExplodeEvent.blockList()) {
                    if (canBreak(p, block)) {
                        blocksToDestroy.add(block);
                    }
                }
            }
        } else {
            for (Block block : blocks) {
                if (canBreak(p, block)) {
                    blocksToDestroy.add(block);
                }
            }
        }

        ExplosiveToolBreakBlocksEvent event = new ExplosiveToolBreakBlocksEvent(p, b, blocksToDestroy, item, this);
        Bukkit.getServer().getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            int bX = b.getX();
            int bY = b.getY();
            int bZ = b.getZ();
            
            if (canBreak(p, b)) {
                breakBlock(p, item, b, drops, true);
            }
            
            for (Block block : blocksToDestroy) {
                // Use coordinate comparison instead of location.equals() for better performance
                if (block.getX() != bX || block.getY() != bY || block.getZ() != bZ) {
                    breakBlock(p, item, block, drops, false);
                }
            }
        }
    }

    private List<Block> findBlocks(Block b, BlockFace face) {
        List<Block> blocks = new ArrayList<>(26);
        boolean breakFromCenterValue = breakFromCenter.getValue();
        
        Block center = breakFromCenterValue ? b : b.getRelative(face, 2);
        int bX = b.getX();
        int bY = b.getY();
        int bZ = b.getZ();

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block relative = center.getRelative(x, y, z);

                    // Use coordinate comparison instead of location.equals() for better performance
                    if (relative.getX() == bX && relative.getY() == bY && relative.getZ() == bZ) {
                        continue;
                    }

                    // Small check to reduce lag
                    if (relative.getType() != Material.AIR) {
                        blocks.add(relative);
                    }
                }
            }
        }
        return blocks;
    }

    @Override
    public boolean isDamageable() {
        return damageOnUse.getValue();
    }

    protected boolean canBreak(@Nonnull Player p, @Nonnull Block b) {
        if (b.isEmpty() || b.isLiquid()) {
            return false;
        }
        
        Material type = b.getType();
        if (SlimefunTag.UNBREAKABLE_MATERIALS.isTagged(type)) {
            return false;
        }
        
        org.bukkit.Location loc = b.getLocation();
        if (!b.getWorld().getWorldBorder().isInside(loc)) {
            return false;
        }
        
        if (Slimefun.getIntegrations().isCustomBlock(b)) {
            return false;
        }
        
        return Slimefun.getProtectionManager().hasPermission(p, loc, Interaction.BREAK_BLOCK);
    }

    private void breakBlock(Player p, ItemStack item, Block b, List<ItemStack> drops, boolean isOriginalBlock) {
        Slimefun.getProtectionManager().logAction(p, b, Interaction.BREAK_BLOCK);
        
        // Don't break SF blocks - check early to avoid unnecessary operations
        SlimefunItem sfItem = BlockStorage.check(b);
        if (sfItem != null) {
            return;
        }

        Material material = b.getType();
        org.bukkit.Location loc = b.getLocation();
        org.bukkit.World world = b.getWorld();
        
        world.playEffect(loc, Effect.STEP_SOUND, material);

        if (isOriginalBlock) {
            if (triggerOtherPlugins.getValue()) {
                AlternateBreakEvent breakEvent = new AlternateBreakEvent(b, p);
                Bukkit.getServer().getPluginManager().callEvent(breakEvent);
            }
            b.breakNaturally(item);
            damageItem(p, item);
        } else {
            for (ItemStack drop : b.getDrops(item, p)) {
                if (drop != null && !drop.getType().isAir()) {
                    world.dropItemNaturally(loc, drop);
                }
            }
            b.setType(Material.AIR, false);
        }
    }
}
