package io.ncbpfluffybear.fluffymachines.items;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.ncbpfluffybear.fluffymachines.objects.DoubleHologramOwner;
import io.ncbpfluffybear.fluffymachines.objects.NonHopperableBlock;
import io.ncbpfluffybear.fluffymachines.utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * A Remake of Barrels by John000708
 *
 * @author NCBPFluffyBear
 */

public class Barrel extends NonHopperableBlock implements DoubleHologramOwner {

    private final int[] inputBorder = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private final int[] outputBorder = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};
    private final int[] plainBorder = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 36, 37, 38, 39, 40, 41, 42, 43, 44};

    protected final int[] INPUT_SLOTS = {19, 20};
    protected final int[] OUTPUT_SLOTS = {24, 25};

    private final int STATUS_SLOT = 22;
    private final int DISPLAY_SLOT = 31;
    private final int HOLOGRAM_TOGGLE_SLOT = 36;
    private final int TRASH_TOGGLE_SLOT = 37;

    private final int OVERFLOW_AMOUNT = 3240;
    public static final DecimalFormat STORAGE_INDICATOR_FORMAT = new DecimalFormat("###,###.####",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final ItemStack HOLOGRAM_OFF_ITEM = CustomItemStack.create(Material.QUARTZ_SLAB, "&3Toggle Hologram &c(Off)");
    private final ItemStack HOLOGRAM_ON_ITEM = CustomItemStack.create(Material.QUARTZ_SLAB, "&3Toggle Hologram &a(On)");
    private final ItemStack TRASH_ON_ITEM = CustomItemStack.create(SlimefunItems.TRASH_CAN.item(), "&3Toggle Overfill Trash &a(On)",
            "&7Turn on to delete unstorable items");
    private final ItemStack TRASH_OFF_ITEM = CustomItemStack.create(SlimefunItems.TRASH_CAN.item(), "&3Toggle Overfill Trash &c(Off)",
            "&7Turn on to delete unstorable items"
    );

    private final ItemSetting<Boolean> showHologram = new ItemSetting<>(this, "show-hologram", true);
    private final ItemSetting<Boolean> breakOnlyWhenEmpty = new ItemSetting<>(this, "break-only-when-empty", false);

    protected final ItemSetting<Integer> barrelCapacity;

    // BlockStorage keys for strict NBT matching
    private static final String STORED_ITEM_B64_KEY = "stored_item_b64";
    private static final String STORED_AMOUNT_KEY = "stored_amount";

    // InfinityExpansion 2 storage tag key - items with this tag must be rejected
    // Note: Using deprecated constructor as it's the most reliable way for external plugin keys
    @SuppressWarnings("deprecation")
    private static final NamespacedKey INFINITY_EXPANSION_STORAGE_KEY = new NamespacedKey("infinityexpansion2", "storage");

    /**
     * Serializes an ItemStack to a Base64 string
     *
     * @param item The ItemStack to serialize
     * @return Base64 string representation of the ItemStack, or null if serialization fails
     */
    @Nullable
    private String serializeItemStackToBase64(@Nonnull ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Deserializes an ItemStack from a Base64 string
     *
     * @param base64 The Base64 string to deserialize
     * @return The deserialized ItemStack, or null if deserialization fails
     */
    @Nullable
    private ItemStack deserializeItemStackFromBase64(@Nonnull String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Strictly compares two ItemStacks including all NBT data
     * This method serializes both items to Base64 and compares the strings
     * to ensure 100% NBT matching. This prevents item duplication/transmutation exploits.
     *
     * @param item1 First item to compare (input item)
     * @param item2 Second item to compare (stored prototype)
     * @return true if items are identical including all NBT data, false otherwise
     */
    private boolean strictNBTMatch(@Nonnull ItemStack item1, @Nonnull ItemStack item2) {
        // First check basic properties - type must match
        if (item1.getType() != item2.getType()) {
            return false;
        }

        // Create clones with amount = 1 for fair comparison (amount doesn't affect NBT)
        ItemStack clone1 = item1.clone();
        clone1.setAmount(1);
        ItemStack clone2 = item2.clone();
        clone2.setAmount(1);

        // Serialize both to Base64 - this captures ALL NBT data including custom tags
        String base64_1 = serializeItemStackToBase64(clone1);
        String base64_2 = serializeItemStackToBase64(clone2);

        // If serialization fails for either item, reject the match (fail-safe)
        // This prevents exploits if serialization is somehow bypassed
        if (base64_1 == null || base64_2 == null) {
            return false; // Reject on serialization failure - be strict
        }

        // Compare Base64 strings byte-by-byte
        // If they match exactly, items are 100% identical including all NBT data
        // If they don't match, items have different NBT and should NOT stack
        return base64_1.equals(base64_2);
    }

    public Barrel(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                  int MAX_STORAGE) {
        super(category, item, recipeType, recipe);

        this.barrelCapacity = new IntRangeSetting(this, "capacity", 0, MAX_STORAGE, Integer.MAX_VALUE);

        addItemSetting(barrelCapacity);

        new BlockMenuPreset(getId(), getItemName()) {

            @Override
            public void init() {
                constructMenu(this);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                buildMenu(menu, b);
            }

            @Override
            public boolean canOpen(@Nonnull Block b, @Nonnull Player p) {
                if (Utils.canOpen(b, p)) {
                    updateMenu(b, BlockStorage.getInventory(b), true, getCapacity(b));
                    return true;
                }

                return false;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow itemTransportFlow) {
                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.INSERT) {
                    return INPUT_SLOTS;
                } else if (flow == ItemTransportFlow.WITHDRAW) {
                    return OUTPUT_SLOTS;
                } else {
                    return new int[0];
                }
            }
        };

        addItemHandler(onBreak());
        addItemSetting(showHologram, breakOnlyWhenEmpty);

    }

    private ItemHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                Block b = e.getBlock();
                Player p = e.getPlayer();
                BlockMenu inv = BlockStorage.getInventory(b);
                int capacity = getCapacity(b);
                int stored = getStored(b);

                if (inv != null) {

                    int itemCount = 0;

                    if (breakOnlyWhenEmpty.getValue() && stored != 0) {
                        Utils.send(p, "&cThis barrel can't be broken since it has items inside it!");
                        e.setCancelled(true);
                        return;
                    }

                    for (Entity en : p.getNearbyEntities(5, 5, 5)) {
                        if (en instanceof Item) {
                            itemCount++;
                        }
                    }

                    if (itemCount > 5) {
                        Utils.send(p, "&cPlease remove nearby items before breaking this barrel!");
                        e.setCancelled(true);
                        return;
                    }

                    inv.dropItems(b.getLocation(), INPUT_SLOTS);
                    inv.dropItems(b.getLocation(), OUTPUT_SLOTS);

                    if (stored > 0) {
                        ItemStack storedItem = getStoredItem(b);

                        if (storedItem.getType() == Material.BARRIER) {
                            setStored(b, 0);
                            updateMenu(b, inv, true, capacity);
                            return;
                        }

                        int stackSize = storedItem.getMaxStackSize();

                        if (stored > OVERFLOW_AMOUNT) {

                            Utils.send(p, "&eThere are more than " + OVERFLOW_AMOUNT + " items in this barrel! " +
                                    "Dropping " + OVERFLOW_AMOUNT + " items instead!");
                            int toRemove = OVERFLOW_AMOUNT;
                            while (toRemove >= stackSize) {
                                // Create a fresh clone to ensure NBT is properly copied
                                ItemStack clone = storedItem.clone();
                                clone.setAmount(stackSize);
                                b.getWorld().dropItemNaturally(b.getLocation(), clone);
                                toRemove = toRemove - stackSize;
                            }

                            if (toRemove > 0) {
                                // Create a fresh clone to ensure NBT is properly copied
                                ItemStack clone = storedItem.clone();
                                clone.setAmount(toRemove);
                                b.getWorld().dropItemNaturally(b.getLocation(), clone);
                            }

                            setStored(b, stored - OVERFLOW_AMOUNT);
                            updateMenu(b, inv, true, capacity);

                            e.setCancelled(true);
                        } else {

                            // Everything greater than 1 stack
                            while (stored >= stackSize) {
                                // Create a fresh clone to ensure NBT is properly copied
                                ItemStack clone = storedItem.clone();
                                clone.setAmount(stackSize);
                                b.getWorld().dropItemNaturally(b.getLocation(), clone);
                                stored = stored - stackSize;
                            }

                            // Drop remaining, if there is any
                            if (stored > 0) {
                                // Create a fresh clone to ensure NBT is properly copied
                                ItemStack clone = storedItem.clone();
                                clone.setAmount(stored);
                                b.getWorld().dropItemNaturally(b.getLocation(), clone);
                            }

                            // In case they use an explosive pick
                            setStored(b, 0);
                            updateMenu(b, inv, true, capacity);
                            removeHologram(b);
                        }
                    } else {
                        removeHologram(b);
                    }

                }
            }
        };
    }

    private void constructMenu(BlockMenuPreset preset) {
        Utils.createBorder(preset, ChestMenuUtils.getOutputSlotTexture(), outputBorder);
        Utils.createBorder(preset, ChestMenuUtils.getInputSlotTexture(), inputBorder);
        ChestMenuUtils.drawBackground(preset, plainBorder);
    }

    protected void buildMenu(BlockMenu menu, Block b) {
        int capacity = getCapacity(b);

        // Initialize an empty barrel (check both old and new keys for backward compatibility)
        String storedAmount = BlockStorage.getLocationInfo(b.getLocation(), STORED_AMOUNT_KEY);
        String oldStored = BlockStorage.getLocationInfo(b.getLocation(), "stored");
        
        if (storedAmount == null && oldStored == null) {

            menu.replaceExistingItem(STATUS_SLOT, CustomItemStack.create(
                    Material.LIME_STAINED_GLASS_PANE, "&6Items Stored: &e0" + " / " + capacity, "&70%"));
            menu.replaceExistingItem(DISPLAY_SLOT, CustomItemStack.create(Material.BARRIER, "&cEmpty"));

            setStored(b, 0);

            if (showHologram.getValue()) {
                updateHologram(b, null, "&cEmpty");
            }

            // Change hologram settings
        } else if (!showHologram.getValue()) {
            removeHologram(b);
        }

        // Every time setup
        menu.addMenuClickHandler(STATUS_SLOT, ChestMenuUtils.getEmptyClickHandler());
        menu.addMenuClickHandler(DISPLAY_SLOT, ChestMenuUtils.getEmptyClickHandler());

        // Toggle hologram (Dynamic button)
        String holo = BlockStorage.getLocationInfo(b.getLocation(), "holo");
        if (holo == null || holo.equals("true")) {
            menu.replaceExistingItem(HOLOGRAM_TOGGLE_SLOT, HOLOGRAM_ON_ITEM);
        } else {
            menu.replaceExistingItem(HOLOGRAM_TOGGLE_SLOT, HOLOGRAM_OFF_ITEM);
        }
        menu.addMenuClickHandler(HOLOGRAM_TOGGLE_SLOT, (pl, slot, item, action) -> {
            toggleHolo(b, capacity);
            return false;
        });

        // Toggle trash (Dynamic button)
        String trash = BlockStorage.getLocationInfo(b.getLocation(), "trash");
        if (trash == null || trash.equals("false")) {
            menu.replaceExistingItem(TRASH_TOGGLE_SLOT, TRASH_OFF_ITEM);
        } else {
            menu.replaceExistingItem(TRASH_TOGGLE_SLOT, TRASH_ON_ITEM);
        }
        menu.addMenuClickHandler(TRASH_TOGGLE_SLOT, (pl, slot, item, action) -> {
            toggleTrash(b);
            return false;
        });

        // Insert all
        int INSERT_ALL_SLOT = 43;
        menu.replaceExistingItem(INSERT_ALL_SLOT,
                CustomItemStack.create(Material.LIME_STAINED_GLASS_PANE, "&bInsert All",
                        "&7> Click here to insert all", "&7compatible items into the barrel"));
        menu.addMenuClickHandler(INSERT_ALL_SLOT, (pl, slot, item, action) -> {
            insertAll(pl, menu, b);
            return false;
        });

        // Extract all
        int EXTRACT_SLOT = 44;
        menu.replaceExistingItem(EXTRACT_SLOT,
                CustomItemStack.create(Material.RED_STAINED_GLASS_PANE, "&6Extract All",
                        "&7> Left click to extract", "&7all items to your inventory",
                        "&7> Right click to extract 1 item"
                ));
        menu.addMenuClickHandler(EXTRACT_SLOT, (pl, slot, item, action) -> {
            extract(pl, menu, b, action);
            return false;
        });
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, Config data) {
                Barrel.this.tick(b);
            }

            @Override
            public boolean isSynchronized() {
                return true;
            }
        });
    }

    protected void tick(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b);
        int capacity = getCapacity(b);

        for (int slot : INPUT_SLOTS) {
            acceptInput(inv, b, slot, capacity);
        }

        for (int ignored : OUTPUT_SLOTS) {
            pushOutput(inv, b, capacity);
        }
    }

    void acceptInput(BlockMenu inv, Block b, int slot, int capacity) {
        ItemStack item = inv.getItemInSlot(slot);
        if (item == null) {
            return;
        }

        // IMMEDIATE REJECTION: Block InfinityExpansion 2 storage items to prevent conflicts/exploits
        // This check must be the FIRST logic step - before any other processing
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            // Check if the PDC contains the InfinityExpansion storage key
            // We check all keys to see if our target key exists
            for (NamespacedKey key : pdc.getKeys()) {
                if (key.equals(INFINITY_EXPANSION_STORAGE_KEY)) {
                    // Item contains infinityexpansion2:storage tag - REJECT immediately
                    // Do not consume the item, do not modify barrel storage
                    return;
                }
            }
        }

        int stored = getStored(b);

        if (stored == 0) {
            // Barrel is empty: first item becomes the prototype
            // This item's full NBT (including all custom tags) will be stored as the prototype
            registerItem(b, inv, slot, item, capacity, stored);
            return;
        }

        // Barrel has items: MUST use strict NBT matching to prevent exploits
        ItemStack prototype = getStoredItem(b);
        
        if (prototype.getType() == Material.BARRIER) {
            // Prototype is missing/corrupted, treat as empty and register new item
            registerItem(b, inv, slot, item, capacity, stored);
            return;
        }
        
        // CRITICAL: Use strict NBT matching - if this returns false, REJECT the item
        // Items with same display/lore but different NBT must NOT stack
        if (!strictNBTMatch(item, prototype)) {
            // NBT doesn't match - REJECT insertion to prevent duplication exploit
            // Item stays in slot, no modification occurs
            return;
        }

        // Only reach here if strictNBTMatch returned true
        // Now we can safely store the item since NBT matches exactly
        if (stored < capacity) {
            // Can fit entire itemstack
            if (stored + item.getAmount() <= capacity) {
                // Store entire stack
                storeItem(b, inv, slot, item, capacity, stored);
            } else {
                // Partial insertion - only store what fits
                int amount = capacity - stored;
                inv.consumeItem(slot, amount);
                setStored(b, stored + amount);
                updateMenu(b, inv, false, capacity);
            }
        } else {
            // Barrel is full
            String useTrash = BlockStorage.getLocationInfo(b.getLocation(), "trash");
            if (useTrash != null && useTrash.equals("true")) {
                inv.replaceExistingItem(slot, null);
            }
            // If trash is off, item stays in slot
        }
    }

    void pushOutput(BlockMenu inv, Block b, int capacity) {
        ItemStack storedItem = getStoredItem(b);
        if (storedItem.getType() == Material.BARRIER) {
            return;
        }

        int stored = getStored(b);
        if (stored == 0) {
            return;
        }

        // Output stack
        if (stored > storedItem.getMaxStackSize()) {
            // Create a fresh clone to ensure NBT is properly copied
            ItemStack clone = storedItem.clone();
            clone.setAmount(storedItem.getMaxStackSize());

            if (inv.fits(clone, OUTPUT_SLOTS)) {
                int amount = clone.getMaxStackSize();
                setStored(b, stored - amount);
                inv.pushItem(clone, OUTPUT_SLOTS);
                updateMenu(b, inv, false, capacity);
            }
        } else {
            // Output remaining - create a fresh clone to ensure NBT is properly copied
            ItemStack clone = storedItem.clone();
            clone.setAmount(stored);

            if (inv.fits(clone, OUTPUT_SLOTS)) {
                setStored(b, 0);
                inv.pushItem(clone, OUTPUT_SLOTS);
                updateMenu(b, inv, false, capacity);
            }
        }
    }

    private void registerItem(Block b, BlockMenu inv, int slot, ItemStack item, int capacity, int stored) {
        if (item == null) {
            return;
        }

        int amount = item.getAmount();
        
        // CRITICAL: Create prototype from the FULL item including ALL NBT data
        // Clone the item to preserve all NBT, then set amount to 1 for storage
        ItemStack prototype = item.clone();
        prototype.setAmount(1);
        
        // Serialize the FULL prototype (with all NBT) to Base64
        // This captures everything: ItemMeta, custom NBT tags, enchantments, etc.
        String base64 = serializeItemStackToBase64(prototype);
        
        if (base64 == null || base64.isEmpty()) {
            // Serialization failed - don't store to prevent corruption
            // Item stays in slot
            return;
        }
        
        // Save the complete prototype (with all NBT) to BlockStorage
        // This becomes the reference for all future comparisons
        BlockStorage.addBlockInfo(b.getLocation(), STORED_ITEM_B64_KEY, base64);
        
        // Update display slot (visual only - not used for data)
        inv.replaceExistingItem(DISPLAY_SLOT, CustomItemStack.create(Utils.keyItem(item), 1));

        // Store the item amount
        if (amount <= capacity) {
            // Can fit entire stack
            storeItem(b, inv, slot, item, capacity, stored);
        } else {
            // Only fit partial amount
            int amountToStore = capacity;
            inv.consumeItem(slot, amountToStore);
            setStored(b, stored + amountToStore);
            updateMenu(b, inv, false, capacity);
        }
    }

    private void storeItem(Block b, BlockMenu inv, int slot, ItemStack item, int capacity, int stored) {
        int amount = item.getAmount();
        inv.consumeItem(slot, amount);

        setStored(b, stored + amount);
        updateMenu(b, inv, false, capacity);
    }


    /**
     * This method updates the barrel's menu and hologram displays
     *
     * @param b   is the barrel block
     * @param inv is the barrel's inventory
     */
    public void updateMenu(Block b, BlockMenu inv, boolean force, int capacity) {
        String hasHolo = BlockStorage.getLocationInfo(b.getLocation(), "holo");
        int stored = getStored(b);
        ItemStack storedItem = getStoredItem(b);
        String itemName;

        String storedPercent = doubleRoundAndFade((double) stored / (double) capacity * 100);
        
        // Calculate stacks based on stored item's max stack size
        int maxStackSize = storedItem.getType() != Material.BARRIER ? storedItem.getMaxStackSize() : 64;
        String storedStacks = doubleRoundAndFade((double) stored / (double) maxStackSize);

        // This helps a bit with lag, but may have visual impacts
        if (inv.hasViewer() || force) {
            inv.replaceExistingItem(STATUS_SLOT, CustomItemStack.create(
                    Material.LIME_STAINED_GLASS_PANE, "&6Items Stored: &e" + stored + " / " + capacity,
                    "&b" + storedStacks + " Stacks &8| &7" + storedPercent + "&7%"));
        }

        if (stored == 0) {
            // Barrel is empty: update display to barrier
            inv.replaceExistingItem(DISPLAY_SLOT, CustomItemStack.create(Material.BARRIER, "&cEmpty"));
            if (showHologram.getValue() && (hasHolo == null || hasHolo.equals("true"))) {
                updateHologram(b, null, "&cEmpty");
            }
        } else {
            // Barrel has items: update display slot (visual only) from stored prototype
            ItemStack displayItem = CustomItemStack.create(Utils.keyItem(storedItem), 1);
            inv.replaceExistingItem(DISPLAY_SLOT, displayItem);
            
            // Get item name for hologram
            if (storedItem.hasItemMeta() && storedItem.getItemMeta().hasDisplayName()) {
                itemName = storedItem.getItemMeta().getDisplayName();
            } else {
                itemName = WordUtils.capitalizeFully(storedItem.getType().name().replace("_", " "));
            }
            
            if (showHologram.getValue() && (hasHolo == null || hasHolo.equals("true"))) {
                updateHologram(b, itemName, " &9x" + stored + " &7(" + storedPercent + "&7%)");
            }
        }
    }

    /**
     * This method toggles if a hologram is present above the barrel.
     *
     * @param b is the block the hologram is linked to
     */
    private void toggleHolo(Block b, int capacity) {
        String toggle = BlockStorage.getLocationInfo(b.getLocation(), "holo");
        if (toggle == null || toggle.equals("true")) {
            putBlockData(b, HOLOGRAM_TOGGLE_SLOT, "holo", HOLOGRAM_OFF_ITEM, false);
            removeHologram(b);
        } else {
            putBlockData(b, HOLOGRAM_TOGGLE_SLOT, "holo", HOLOGRAM_ON_ITEM, true);
            updateMenu(b, BlockStorage.getInventory(b), false, capacity);
        }
    }

    /**
     * Toggle auto dispose status of barrel
     */
    private void toggleTrash(Block b) {
        String toggle = BlockStorage.getLocationInfo(b.getLocation(), "trash");
        if (toggle == null || toggle.equals("false")) {
            putBlockData(b, TRASH_TOGGLE_SLOT, "trash", TRASH_ON_ITEM, true);
        } else {
            putBlockData(b, TRASH_TOGGLE_SLOT, "trash", TRASH_OFF_ITEM, false);
        }
    }

    /**
     * Sets a key in BlockStorage and replaces an item
     */
    private void putBlockData(Block b, int slot, String key, ItemStack displayItem, boolean data) {
        BlockStorage.addBlockInfo(b.getLocation(), key, String.valueOf(data));
        BlockStorage.getInventory(b).replaceExistingItem(slot, displayItem);
    }

    public void insertAll(Player p, BlockMenu menu, Block b) {
        ItemStack prototype = getStoredItem(b);
        PlayerInventory inv = p.getInventory();
        int capacity = getCapacity(b);
        int stored = getStored(b);

        // If barrel is empty, we can't insert all without a prototype
        if (prototype.getType() == Material.BARRIER) {
            Utils.send(p, "&cBarrel is empty! Insert an item first to set the prototype.");
            return;
        }

        // Iterate through player inventory and only insert items that match prototype exactly
        for (int i = 0; i < inv.getContents().length; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) {
                continue;
            }
            
            // CRITICAL: Use strict NBT matching - if NBT doesn't match, SKIP this item
            // Items with same display/lore but different NBT must NOT be inserted
            if (!strictNBTMatch(item, prototype)) {
                // NBT doesn't match - skip this item to prevent exploit
                continue;
            }

            // Only reach here if strictNBTMatch returned true
            // Now we can safely insert the item since NBT matches exactly
            int amount = item.getAmount();
            if (stored + amount <= capacity) {
                // Can fit entire stack
                inv.setItem(i, null);
                stored += amount;
            } else {
                // Partial insertion if over capacity
                int canFit = capacity - stored;
                if (canFit > 0) {
                    item.setAmount(item.getAmount() - canFit);
                    stored += canFit;
                }
                // Barrel is now full, stop processing
                break;
            }
        }

        setStored(b, stored);
        updateMenu(b, menu, false, capacity);
    }

    public void extract(Player p, BlockMenu menu, Block b, ClickAction action) {
        ItemStack storedItem = getStoredItem(b);
        int capacity = getCapacity(b);

        PlayerInventory inv = p.getInventory();
        int stored = getStored(b);

        // Extract single
        if (action.isRightClicked()) {
            if (stored > 0) { // Extract from stored
                // Create a fresh clone to ensure NBT is properly copied
                ItemStack clone = storedItem.clone();
                clone.setAmount(1);
                Utils.giveOrDropItem(p, clone);
                setStored(b, --stored);
                updateMenu(b, menu, false, capacity);
                return;
            } else {
                for (int slot : OUTPUT_SLOTS) { // Extract from slot
                    if (menu.getItemInSlot(slot) != null) {
                        Utils.giveOrDropItem(p, CustomItemStack.create(menu.getItemInSlot(slot), 1));
                        menu.consumeItem(slot);
                        return;
                    }
                }
            }
            Utils.send(p, "&cThis barrel is empty!");
            return;
        }

        if (storedItem.getType() == Material.BARRIER) {
            Utils.send(p, "&cThis barrel is empty!");
            return;
        }

        // Extract all
        ItemStack[] contents = inv.getStorageContents().clone();
        int maxStackSize = storedItem.getMaxStackSize();
        int outI = 0;

        for (int i = 0; i < contents.length; i++) {

            if (contents[i] == null) {
                if (stored >= maxStackSize) {
                    // Create a fresh clone to ensure NBT is properly copied
                    ItemStack clone = storedItem.clone();
                    clone.setAmount(maxStackSize);
                    inv.setItem(i, clone);
                    stored -= maxStackSize;
                } else if (stored > 0) {
                    // Create a fresh clone to ensure NBT is properly copied
                    ItemStack clone = storedItem.clone();
                    clone.setAmount(stored);
                    inv.setItem(i, clone);
                    stored = 0;
                } else {
                    if (outI > 1) {
                        break;
                    }

                    ItemStack item = menu.getItemInSlot(OUTPUT_SLOTS[outI]);

                    if (item == null) {
                        continue;
                    }

                    inv.setItem(i, item.clone());
                    menu.replaceExistingItem(OUTPUT_SLOTS[outI], null);

                    outI++;
                }
            }
        }

        setStored(b, stored);
        updateMenu(b, menu, false, capacity);
    }

    public static String doubleRoundAndFade(double num) {
        // Using same format that is used on lore power
        String formattedString = STORAGE_INDICATOR_FORMAT.format(num);
        if (formattedString.indexOf('.') != -1) {
            return formattedString.substring(0, formattedString.indexOf('.')) + ChatColor.DARK_GRAY
                    + formattedString.substring(formattedString.indexOf('.')) + ChatColor.GRAY;
        } else {
            return formattedString;
        }
    }

    public int getStored(Block b) {
        String storedStr = BlockStorage.getLocationInfo(b.getLocation(), STORED_AMOUNT_KEY);
        // Backward compatibility: check old "stored" key if new key doesn't exist
        if (storedStr == null) {
            storedStr = BlockStorage.getLocationInfo(b.getLocation(), "stored");
            if (storedStr != null) {
                // Migrate to new key
                BlockStorage.addBlockInfo(b.getLocation(), STORED_AMOUNT_KEY, storedStr);
            }
        }
        if (storedStr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(storedStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setStored(Block b, int amount) {
        BlockStorage.addBlockInfo(b.getLocation(), STORED_AMOUNT_KEY, String.valueOf(amount));
        // Also clear stored item if amount is 0
        if (amount == 0) {
            BlockStorage.addBlockInfo(b.getLocation(), STORED_ITEM_B64_KEY, null);
        }
    }

    /**
     * Gets the stored item prototype from BlockStorage
     * Returns a barrier item if no item is stored or deserialization fails
     */
    @Nonnull
    public ItemStack getStoredItem(Block b) {
        String base64 = BlockStorage.getLocationInfo(b.getLocation(), STORED_ITEM_B64_KEY);
        if (base64 == null || base64.isEmpty()) {
            return CustomItemStack.create(Material.BARRIER, "&cEmpty");
        }
        
        ItemStack item = deserializeItemStackFromBase64(base64);
        if (item == null) {
            // Deserialization failed, clear the corrupted data
            BlockStorage.addBlockInfo(b.getLocation(), STORED_ITEM_B64_KEY, null);
            BlockStorage.addBlockInfo(b.getLocation(), STORED_AMOUNT_KEY, "0");
            return CustomItemStack.create(Material.BARRIER, "&cEmpty");
        }
        
        return item;
    }

    /**
     * Gets capacity of barrel
     * Includes Block parameter for MiniBarrel
     */
    public int getCapacity(Block b) {
        return barrelCapacity.getValue();
    }

    public static int getDisplayCapacity(Barrel.BarrelType barrel) {
        int capacity = Slimefun.getItemCfg().getInt(barrel.getKey() + ".capacity");
        if (capacity == 0) {
            capacity = barrel.getDefaultSize();
        }

        return capacity;
    }

    @Nonnull
    @Override
    public Vector getHologramOffset(@Nonnull Block b) {
        return new Vector(0.5, 0.9, 0.5);
    }

    public enum BarrelType {

        SMALL(17280000, "&eSmall Fluffy Barrel", Material.BEEHIVE, SlimefunItems.REINFORCED_PLATE.item(), new ItemStack(Material.OAK_LOG)),
        MEDIUM(34560000, "&6Medium Fluffy Barrel", Material.BARREL, SlimefunItems.REINFORCED_PLATE.item(), new ItemStack(Material.SMOOTH_STONE)),
        BIG(69120000, "&bBig Fluffy Barrel", Material.SMOKER, SlimefunItems.REINFORCED_PLATE.item(), new ItemStack(Material.BRICKS)),
        LARGE(138240000, "&aLarge Fluffy Barrel", Material.LODESTONE, SlimefunItems.REINFORCED_PLATE.item(), new ItemStack(Material.IRON_BLOCK)),
        MASSIVE(276480000, "&5Massive Fluffy Barrel", Material.CRYING_OBSIDIAN, SlimefunItems.REINFORCED_PLATE.item(), new ItemStack(Material.OBSIDIAN)),
        BOTTOMLESS(1728000000, "&cBottomless Fluffy Barrel", Material.RESPAWN_ANCHOR, SlimefunItems.BLISTERING_INGOT_3.item(), SlimefunItems.REINFORCED_PLATE.item());

        private final int defaultSize;
        private final String displayName;
        private final Material itemMaterial;
        private final ItemStack reinforcement;
        private final ItemStack border;

        BarrelType(int defaultSize, String displayName, Material itemMaterial, ItemStack reinforcement, ItemStack border) {
            this.defaultSize = defaultSize;
            this.displayName = displayName;
            this.itemMaterial = itemMaterial;
            this.reinforcement = reinforcement;
            this.border = border;
        }

        public int getDefaultSize() {
            return defaultSize;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getType() {
            return itemMaterial;
        }

        public String getKey() {
            return this.name().toUpperCase() + "_FLUFFY_BARREL";
        }

        public ItemStack getReinforcement() {
            return reinforcement;
        }

        public ItemStack getBorder() {
            return border;
        }
    }

}
