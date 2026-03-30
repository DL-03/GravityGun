package me.dl.GravityGun;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.*;

public class Manager implements Listener {
    private final Material MATERIAL = Material.CROSSBOW;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final GravityGun plugin;
    private final LanguageManager lang;

    // Параметры механики
    private String configName;
    private double range, pull_range, pull_force, launch_power;
    private double hold_distance_min, hold_distance_max, hold_distance_step;
    private double grab_cooldown, hold_max_time;

    public enum ListMode { BLACKLIST, WHITELIST, DISABLED;
        public static ListMode safeValueOf(String name) {
            try { return valueOf(name.toUpperCase()); } catch (Exception e) { return DISABLED; }
        }
    }

    private ListMode list_entities_mode, list_blocks_mode;
    private List<EntityType> list_entity_types;
    private List<Material> list_block_types;
    private boolean allow_grab_player, allow_grab_entities, allow_grab_blocks;

    // Ключи данных
    private final NamespacedKey keyGravityGun = new NamespacedKey(GravityGun.getInstance(), "GravityGun");
    private final NamespacedKey keyGG_is_holding = new NamespacedKey(GravityGun.getInstance(), "GG_is_holding");
    private final NamespacedKey keyGG_vx = new NamespacedKey(GravityGun.getInstance(), "GG_vx");
    private final NamespacedKey keyGG_vy = new NamespacedKey(GravityGun.getInstance(), "GG_vy");
    private final NamespacedKey keyGG_vz = new NamespacedKey(GravityGun.getInstance(), "GG_vz");
    private final NamespacedKey keyIsBlock = new NamespacedKey(GravityGun.getInstance(), "isBlock");
    private final NamespacedKey keyBlockType = new NamespacedKey(GravityGun.getInstance(), "blockType");
    private final NamespacedKey keyBlockData = new NamespacedKey(GravityGun.getInstance(), "blockData");
    private final NamespacedKey keyInventoryData = new NamespacedKey(GravityGun.getInstance(), "inventoryData");

    private final Map<Player, Entity> playerEntityMap = new HashMap<>();
    private final Map<Player, Location> playerEntityLocationMap = new HashMap<>();
    private final Map<UUID, Boolean> originalGravity = new HashMap<>();
    private final Map<UUID, Boolean> originalCollectable = new HashMap<>();
    private final Map<Player, Long> playerLastGrabTime = new HashMap<>();
    private final Map<Player, Long> playerGrabTime = new HashMap<>();
    private final Map<UUID, Long> invulnerableEntities = new HashMap<>();

    private static final BukkitScheduler scheduler = Bukkit.getScheduler();
    int taskId;

    private float task_i = 0.0F;
    private float task_i_sound_amb = 0.0F;

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue(), value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Manager() {
        this.plugin = GravityGun.getInstance();
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.lang = GravityGun.langManager;
        reload();
        startTickTask();
    }

    public void reload() {
        var config = GravityGun.getInstance().getConfig();

        if (config.getString("item.name", "Gravity Gun") != "") {
            config.set("item.gravity-gun.name", config.getString("item.name", "").replace("Gravity Gun", ""));
            config.set("item.name", null);
            config.setComments("item.gravity-gun.name", Collections.singletonList("If empty, it will use the default name from the language file"));
            plugin.saveConfig();
        }

        this.configName = config.getString("item.gravity-gun.name", "");

        this.range = config.getDouble("mechanics.grab.range", 8.0);
        this.grab_cooldown = config.getDouble("mechanics.grab.cooldown", 0.3);
        this.pull_range = config.getDouble("mechanics.pull.range", 25.0);
        this.pull_force = config.getDouble("mechanics.pull.force", 0.6);
        this.launch_power = config.getDouble("mechanics.launch.power", 2.0);
        this.hold_distance_min = config.getDouble("mechanics.hold.distance.min", 1.0);
        this.hold_distance_max = config.getDouble("mechanics.hold.distance.max", 8.0);
        this.hold_distance_step = config.getDouble("mechanics.hold.distance.step", 1.0);
        this.hold_max_time = config.getDouble("mechanics.hold.max-time", 0.0);

        this.allow_grab_entities = config.getBoolean("limits.entities.allow-grab", true);
        this.allow_grab_player = config.getBoolean("limits.entities.allow-grab-players", true);
        this.list_entities_mode = ListMode.safeValueOf(config.getString("limits.entities.list-mode", "BLACKLIST"));
        this.list_entity_types = config.getStringList("limits.entities.list").stream().map(s -> {
            try { return EntityType.valueOf(s); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).toList();

        this.allow_grab_blocks = config.getBoolean("limits.blocks.allow-grab", true);
        this.list_blocks_mode = ListMode.safeValueOf(config.getString("limits.blocks.list-mode", "BLACKLIST"));
        this.list_block_types = config.getStringList("limits.blocks.list").stream().map(s -> {
            try { return Material.valueOf(s); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).toList();

        updateHotbarItems();
    }

    /**
     * Обновляет имена предметов во всех инвентарях согласно текущей локализации
     */
    private void updateHotbarItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHotbarItems(player);
        }
    }

    private void updateHotbarItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            updateGravityGun(item, player);
        }
    }

    private void updateGravityGun(ItemStack item, Player player) {
        if (isGravityGun(item)) {
            item.editMeta(meta -> {
                if (configName != "") {
                    meta.displayName(mm.deserialize(configName));
                } else {
                    // Если в конфиге пусто, берем из локализации игрока
                    meta.displayName(lang.getMessage(player, "item.gravity-gun.name", "<gold>Gravity Gun"));
                }
            });
        }
    }

    /**
     * Создает предмет Грави-пушки для конкретного игрока
     */
    public ItemStack makeGravityGun(Player player) {
        ItemStack itemStack = new ItemStack(MATERIAL, 1);
        itemStack.editMeta(im -> {
            if (im instanceof CrossbowMeta cm) {
                CustomModelDataComponent cmd = cm.getCustomModelDataComponent();
                cmd.setStrings(List.of("gravity-gun"));
                cm.setCustomModelDataComponent(cmd);
            }
            im.getPersistentDataContainer().set(this.keyGravityGun, PersistentDataType.BOOLEAN, true);
        });
        updateGravityGun(itemStack, player);
        return itemStack;
    }

    public void giveTool(Player player) {
        player.getInventory().addItem(makeGravityGun(player));
    }

    private boolean isGravityGun(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyGravityGun, PersistentDataType.BOOLEAN);
    }

    private void startTickTask() {
        taskId = scheduler.runTaskTimer(GravityGun.getInstance(), () -> {
            task_i = (task_i >= 5.0F) ? 0.0F : task_i + 1.0F;
            task_i_sound_amb = (task_i_sound_amb >= 20.0F) ? 0.0F : task_i_sound_amb + 1.0F;

            playerEntityMap.forEach((player, entity) -> {
                if (entity == null || !entity.isValid()) return;

                Location offset = this.playerEntityLocationMap.get(player);
                Location eye = player.getEyeLocation();

                double radius = (new Location(player.getWorld(), 0, 0, 0)).distance(offset);
                double yawRad = Math.toRadians(90.0 + eye.getYaw());
                double pitchRad = -Math.toRadians(eye.getPitch());

                double ox = radius * Math.cos(yawRad) * Math.cos(pitchRad);
                double oy = radius * Math.sin(pitchRad);
                double oz = radius * Math.sin(yawRad) * Math.cos(pitchRad);

                float yaw = (entity instanceof Player p) ? p.getEyeLocation().getYaw() : eye.getYaw();
                float pitch = (entity instanceof Player p) ? p.getEyeLocation().getPitch() : 0;

                Location targetLoc = new Location(player.getWorld(), eye.getX() + ox, eye.getY() + oy, eye.getZ() + oz, yaw, pitch);

                if (!canEntityTeleportSafely(entity, targetLoc)) {
                    double safeRad = Math.max(hold_distance_min, radius - 0.5);
                    targetLoc = eye.clone().add(eye.getDirection().multiply(safeRad));
                    if (!canEntityTeleportSafely(entity, targetLoc)) return;
                }

                if (targetLoc.distance(entity.getLocation()) > 2.0) {
                    invulnerableEntities.put(entity.getUniqueId(), System.currentTimeMillis() + 1000);
                }

                entity.teleportAsync(targetLoc);

                if (task_i == 0.0F) {
                    PersistentDataContainer pdc = entity.getPersistentDataContainer();
                    entity.setVelocity(new Vector(ox - pdc.getOrDefault(keyGG_vx, PersistentDataType.FLOAT, (float)ox),
                            oy - pdc.getOrDefault(keyGG_vy, PersistentDataType.FLOAT, (float)oy),
                            oz - pdc.getOrDefault(keyGG_vz, PersistentDataType.FLOAT, (float)oz)));
                    pdc.set(keyGG_vx, PersistentDataType.FLOAT, (float)ox);
                    pdc.set(keyGG_vy, PersistentDataType.FLOAT, (float)oy);
                    pdc.set(keyGG_vz, PersistentDataType.FLOAT, (float)oz);
                }

                if (hold_max_time > 0 && (System.currentTimeMillis() - playerGrabTime.get(player)) > hold_max_time * 1000) {
                    release(player);
                }

                if (task_i_sound_amb == 0) player.getWorld().playSound(player, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.5f);
                if (player.getLocation().distance(entity.getLocation()) > hold_distance_max + 5.0) release(player);
            });
        }, 1L, 1L).getTaskId();
    }

    private boolean canEntityTeleportSafely(Entity entity, Location location) {
        if (entity == null || location == null) return false;
        double w = entity.getWidth(), h = entity.getHeight();
        for (double x = -w/2; x <= w/2; x += 0.5) {
            for (double y = 0; y <= h; y += 0.5) {
                for (double z = -w/2; z <= w/2; z += 0.5) {
                    if (location.clone().add(x, y, z).getBlock().isSolid()) return false;
                }
            }
        }
        return true;
    }

    // --- Обработка событий ---

    // Прокрутка колесом мыши
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!playerEntityMap.containsKey(player)) return;

        int diff = event.getNewSlot() - event.getPreviousSlot();
        if (diff == 0) return;
        // Обработка прокрутки через край (0 -> 8 или 8 -> 0)
        if (diff > 5) diff = -1; else if (diff < -5) diff = 1;

        event.setCancelled(true);
        Location offset = playerEntityLocationMap.get(player);
        double dist = new Location(player.getWorld(), 0,0,0).distance(offset);
        double newDist = Math.max(hold_distance_min, Math.min(hold_distance_max, dist + (diff > 0 ? -1 : 1) * hold_distance_step));

        offset.multiply(newDist / dist);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGravityGun(item)) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        if (playerLastGrabTime.getOrDefault(player, 0L) + 200 > now) return;

        boolean left_key = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction().isLeftClick();
        boolean right_key = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction().isRightClick();

        if (playerEntityMap.containsKey(player)) {
            if (left_key) launch(player); else release(player);
            playerLastGrabTime.put(player, now);
            return;
        }

        if (right_key) {
            RayTraceResult res = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true, 0.5, e -> e != player);
            if (res != null) {
                if (res.getHitEntity() != null) {
                    Entity hit = res.getHitEntity();
                    if (isValidTarget(hit)) grab(player, hit, item);
                } else if (res.getHitBlock() != null && allow_grab_blocks && isAllowMaterial(res.getHitBlock().getType())) {
                    grabBlock(player, res.getHitBlock(), item);
                    return;
                }
            } else {
                // Попытка притягивания (Pull)
                RayTraceResult pullRes = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), pull_range, 1.0, e -> e != player && isValidTarget(e));
                if (pullRes != null && pullRes.getHitEntity() != null) {
                    pull(pullRes.getHitEntity(), player);
                    return;
                } else {
                    RayTraceResult entityRes = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 1.0, e -> e != player && isValidTarget(e));
                    if (entityRes != null && entityRes.getHitEntity() != null) {
                        grab(player, entityRes.getHitEntity(), item);
                        playerLastGrabTime.put(player, now);
                        return;
                    }
                }

            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0f);
    }

    private boolean isValidTarget(Entity e) {
        if (e instanceof Player && !allow_grab_player) return false;
        if (!allow_grab_entities) return false;
        return isAllowEntityType(e.getType());
    }

    void grab(Player player, Entity entity, ItemStack gravityGun) {
        long now = System.currentTimeMillis();
        if (playerLastGrabTime.containsKey(player) && (now - playerLastGrabTime.get(player)) < grab_cooldown * 1000) {
            return;
        }
        playerLastGrabTime.put(player, now);
        playerGrabTime.put(player, now);

        if (entity.getPersistentDataContainer().has(keyIsBlock, PersistentDataType.BOOLEAN)) {
            if (entity.getPersistentDataContainer().get(keyIsBlock, PersistentDataType.BOOLEAN)) {
                if (entity instanceof FallingBlock fallingBlock) {
                    PersistentDataContainer pdc_fb = fallingBlock.getPersistentDataContainer();

                    BlockDisplay blockDisplay = fallingBlock.getWorld().spawn(fallingBlock.getLocation().toCenterLocation(), BlockDisplay.class, d -> {
                        d.setBlock(fallingBlock.getBlockData());
                        Transformation t = d.getTransformation();
                        t.getTranslation().set(-0.5, -0.5, -0.5);
                        d.setTransformation(t);
                    });
                    blockDisplay.setInterpolationDelay(0);
                    blockDisplay.setInterpolationDuration(999999);

                    PersistentDataContainer pdc = blockDisplay.getPersistentDataContainer();
                    pdc.set(keyIsBlock, PersistentDataType.BOOLEAN, true);
                    pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, true);
                    pdc.set(keyBlockType, PersistentDataType.STRING, pdc_fb.get(keyBlockType, PersistentDataType.STRING));
                    pdc.set(keyBlockData, PersistentDataType.STRING, pdc_fb.get(keyBlockData, PersistentDataType.STRING));
                    if (pdc_fb.has(keyInventoryData, PersistentDataType.STRING)) {
                        if (pdc_fb.get(keyInventoryData, PersistentDataType.STRING) != null) {
                            pdc.set(keyInventoryData, PersistentDataType.STRING, pdc_fb.get(keyInventoryData, PersistentDataType.STRING));
                        }
                    }

                    fallingBlock.remove();
                    entity = blockDisplay;
                }
            }
        }

        Location eyeLocation = player.getEyeLocation();
        Location entityLocation = player.getLocation();
        playerEntityLocationMap.put(player, new Location(player.getWorld(), entityLocation.getX() - eyeLocation.getX(), entityLocation.getY() - eyeLocation.getY(), entityLocation.getZ() - eyeLocation.getZ()));
        PersistentDataContainer persistentDataContainer = entity.getPersistentDataContainer();
        persistentDataContainer.set(this.keyGG_vx, PersistentDataType.FLOAT, 0.0F);
        persistentDataContainer.set(this.keyGG_vy, PersistentDataType.FLOAT, 0.0F);
        persistentDataContainer.set(this.keyGG_vz, PersistentDataType.FLOAT, 0.0F);
        this.playerEntityMap.put(player, entity);
        originalGravity.put(entity.getUniqueId(), entity.hasGravity());
        if (entity instanceof LivingEntity le) {
            originalCollectable.put(le.getUniqueId(), le.isCollidable());
            le.setCollidable(false);
            le.setFallDistance(0);
        }
        entity.setGravity(false);
        entity.setVelocity(new Vector(0, 0, 0));

        gravityGun.editMeta(im -> {
            if (im instanceof CrossbowMeta cm) {
                cm.addChargedProjectile(new ItemStack(Material.FIREWORK_ROCKET));
                CustomModelDataComponent cmd = cm.getCustomModelDataComponent();
                cmd.setStrings(List.of("gravity-gun-blue"));
                cm.setCustomModelDataComponent(cmd);
            }
        });
        gravityGun.editPersistentDataContainer(pdc -> {
            pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, true);
        });
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.5f);
    }

    void pull(Entity entity, Player player) {
        Vector vec = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(pull_force);
        entity.setVelocity(vec);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1f, 2f);
    }

    private void grabBlock(Player player, Block block, ItemStack gravityGun) {
        BlockState state = block.getState();
        String inventoryData = null;
        if (state instanceof Container) {
            Inventory inv = ((Container) state).getInventory();
            YamlConfiguration config = new YamlConfiguration();
            config.set("inventory", Arrays.asList(inv.getContents()));
            inventoryData = config.saveToString();
        }

        BlockData data = block.getBlockData();
        BlockDisplay blockDisplay = block.getWorld().spawn(block.getLocation().toCenterLocation(), BlockDisplay.class, d -> {
            d.setBlock(data);
            Transformation t = d.getTransformation();
            t.getTranslation().set(-0.5, -0.5, -0.5);
            d.setTransformation(t);
        });
        blockDisplay.setInterpolationDelay(0);
        blockDisplay.setInterpolationDuration(999999);

        PersistentDataContainer pdc = blockDisplay.getPersistentDataContainer();
        pdc.set(keyIsBlock, PersistentDataType.BOOLEAN, true);
        pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, true);
        pdc.set(keyBlockType, PersistentDataType.STRING, block.getType().name());
        pdc.set(keyBlockData, PersistentDataType.STRING, data.getAsString());
        if (inventoryData != null) {
            pdc.set(keyInventoryData, PersistentDataType.STRING, inventoryData);
        }

        block.setType(Material.AIR);
        grab(player, blockDisplay, gravityGun);
    }

    private void launch(Player player) {
        Entity e = release(player);
        if (e != null) {
            e.setVelocity(player.getEyeLocation().getDirection().multiply(launch_power));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f);
        }
    }
    private void launch(Player player, Entity entity) {
        if (entity != null) {
            entity.setVelocity(player.getEyeLocation().getDirection().multiply(launch_power));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f);
        }
    }

    Entity release(Player player) {
        if (!this.playerEntityMap.containsKey(player)) return null;
        ItemStack item = player.getInventory().getItemInMainHand();

        Entity entity = this.playerEntityMap.remove(player);
        if (entity == null) return null;

        playerEntityLocationMap.remove(player);
        entity.setGravity(originalGravity.getOrDefault(entity.getUniqueId(), true));
        if (entity instanceof LivingEntity le) le.setCollidable(originalCollectable.getOrDefault(le.getUniqueId(), true));
        invulnerableEntities.remove(entity.getUniqueId()); // Удаляем из списка неуязвимых

        if (isGravityGun(item)) {
            item.editMeta(im -> {
                if (im instanceof CrossbowMeta cm) {
                    cm.setChargedProjectiles(null);
                    CustomModelDataComponent cmd = cm.getCustomModelDataComponent();
                    cmd.setStrings(List.of("gravity-gun"));
                    cm.setCustomModelDataComponent(cmd);
                }
            });
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, false);

        if (entity instanceof BlockDisplay && Boolean.TRUE.equals(pdc.get(keyIsBlock, PersistentDataType.BOOLEAN))) {
            Material type = Material.valueOf(pdc.get(keyBlockType, PersistentDataType.STRING));
            BlockData data = Bukkit.createBlockData(pdc.get(keyBlockData, PersistentDataType.STRING));

            // Используем современный метод spawn вместо устаревшего конструктора FallingBlock
            FallingBlock fb = entity.getWorld().spawn(entity.getLocation().toCenterLocation(), FallingBlock.class, fallingBlock -> {
                fallingBlock.setBlockData(data);
                fallingBlock.setDropItem(true);
            });

            PersistentDataContainer pdc_fb = fb.getPersistentDataContainer();
            pdc_fb.set(keyIsBlock, PersistentDataType.BOOLEAN, true);
            pdc_fb.set(keyBlockType, PersistentDataType.STRING, type.name());
            pdc_fb.set(keyBlockData, PersistentDataType.STRING, data.getAsString());

            if (pdc.has(keyInventoryData, PersistentDataType.STRING)) {
                pdc_fb.set(keyInventoryData, PersistentDataType.STRING, pdc.get(keyInventoryData, PersistentDataType.STRING));
            }

            entity.remove();
            entity = fb;
        }

        entity.setVelocity(player.getVelocity());

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 2f);
        return entity;
    }
    public void releaseAll() { new HashSet<>(playerEntityMap.keySet()).forEach(this::release); }

    private boolean isAllowEntityType(EntityType type) {
        if (list_entities_mode == ListMode.DISABLED) return true;
        boolean contains = list_entity_types.contains(type);
        return list_entities_mode == ListMode.WHITELIST ? contains : !contains;
    }

    private boolean isAllowMaterial(Material mat) {
        if (list_blocks_mode == ListMode.DISABLED) return true;
        boolean contains = list_block_types.contains(mat);
        return list_blocks_mode == ListMode.WHITELIST ? contains : !contains;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player player) {
            if (isGravityGun(player.getInventory().getItemInMainHand())) {
                if (isAllowEntityType(e.getEntity().getType()) && (allow_grab_player || !(e.getEntity() instanceof Player))) {
                    if (this.playerEntityMap.containsKey(player) && e.getEntity().equals(this.playerEntityMap.get(player))) {
                        e.setCancelled(true);
                        launch(player);
                    } else if (!this.playerEntityMap.containsValue(e.getEntity())) {
                        e.setCancelled(true);
                        launch(player, e.getEntity());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        Long invulnerabilityEnd = invulnerableEntities.get(e.getEntity().getUniqueId());
        if (invulnerabilityEnd != null) {
            long now = System.currentTimeMillis();
            if (now < invulnerabilityEnd) {
                e.setCancelled(true);
            } else {
                invulnerableEntities.remove(e.getEntity().getUniqueId());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (playerEntityMap.containsValue(e.getEntity())) {
            for (Map.Entry<Player, Entity> entry : playerEntityMap.entrySet()) {
                if (entry.getValue().equals(e.getEntity())) {
                    release(entry.getKey());
                    break;
                }
            }
        }
    }

    @EventHandler void onQuit(PlayerQuitEvent e) { release(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        if (playerEntityMap.containsValue(e.getEntity())) {
            for (Map.Entry<Player, Entity> entry : playerEntityMap.entrySet()) {
                if (entry.getValue().equals(e.getEntity())) {
                    release(entry.getKey());
                    break;
                }
            }
        } else if (playerEntityMap.containsKey(e.getEntity())) {
            release(e.getEntity());
        }
    }

    private void restoreBlock(FallingBlock fb, Block block) {
        PersistentDataContainer pdc = fb.getPersistentDataContainer();
        String rawData = pdc.get(keyBlockData, PersistentDataType.STRING);
        String invData = pdc.get(keyInventoryData, PersistentDataType.STRING);

        if (rawData != null) {
            BlockData data = Bukkit.createBlockData(rawData);

            fb.remove();

            int attempts = 0;
            while (attempts < 5) {
                Material type = block.getType();
                if (type.isAir() || block.isReplaceable()) break;
                block = block.getRelative(0, 1, 0);
                attempts++;
            }

            block.setBlockData(data, true);

            if (invData != null) {
                final Block finalBlock = block;
                Bukkit.getScheduler().runTask(GravityGun.getInstance(), () -> {
                    if (finalBlock.getState() instanceof Container container) {
                        try {
                            YamlConfiguration config = new YamlConfiguration();
                            config.loadFromString(invData);
                            List<?> list = config.getList("inventory");
                            if (list != null) container.getInventory().setContents(list.toArray(new ItemStack[0]));
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.getPersistentDataContainer().has(keyIsBlock, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true);
        restoreBlock(fb, event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.getPersistentDataContainer().has(keyIsBlock, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true);
        restoreBlock(fb, fb.getLocation().getBlock());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateHotbarItems(event.getPlayer());
    }

    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateHotbarItems(event.getPlayer());
        }, 3L);
    }

    @EventHandler
    public void onPlayerInventorySlotChangeEvent(PlayerInventorySlotChangeEvent event) {
        updateGravityGun(event.getNewItemStack(), event.getPlayer());
        updateGravityGun(event.getOldItemStack(), event.getPlayer());
    }

    @EventHandler
    public void onEntityPickupItemEvent(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateGravityGun(event.getItem().getItemStack(), player);
        }
    }
}