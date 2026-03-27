package me.dl.GravityGun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.*;

public class Manager implements Listener {
    Material MATERIAL = Material.CROSSBOW;
    String NAME;
    Component DISPLAY_NAME;

    List<EntityType> list_entity_types;
    List<Material> list_block_types;

    double range;
    double pull_range;
    double pull_force;
    double launch_power;
    double hold_distance_min;
    double hold_distance_max;
    double hold_distance_step;
    double grab_cooldown;
    double hold_max_time;

    public enum ListMode {
        BLACKLIST,
        WHITELIST,
        DISABLED;

        public static ListMode safeValueOf(String name) {
            if (name == null) {
                return ListMode.DISABLED;
            }
            try {
                return ListMode.valueOf(name);
            } catch (IllegalArgumentException e) {
                return ListMode.DISABLED;
            }
        }
    }

    ListMode list_entities_mode;
    ListMode list_blocks_mode;

    boolean allow_grab_player;

    boolean allow_grab_entities;
    boolean allow_grab_blocks;

    NamespacedKey keyGravityGun = new NamespacedKey(GravityGun.getInstance(), "GravityGun");
    NamespacedKey keyGG_is_holding = new NamespacedKey(GravityGun.getInstance(), "GG_is_holding");
    NamespacedKey keyGG_vx = new NamespacedKey(GravityGun.getInstance(), "GG_vx");
    NamespacedKey keyGG_vy = new NamespacedKey(GravityGun.getInstance(), "GG_vy");
    NamespacedKey keyGG_vz = new NamespacedKey(GravityGun.getInstance(), "GG_vz");
    NamespacedKey keyIsBlock = new NamespacedKey(GravityGun.getInstance(), "isBlock");
    NamespacedKey keyBlockType = new NamespacedKey(GravityGun.getInstance(), "blockType");
    NamespacedKey keyBlockData = new NamespacedKey(GravityGun.getInstance(), "blockData");
    NamespacedKey keyInventoryData = new NamespacedKey(GravityGun.getInstance(), "inventoryData");

    NamespacedKey keyGG_playerInteractBlock = new NamespacedKey(GravityGun.getInstance(), "GG_playerInteractBlock");

    private final Map<Player, Entity> playerEntityMap = new HashMap<>();
    private final Map<Player, Location> playerEntityLocationMap = new HashMap<>();
    private final Map<UUID, Boolean> originalGravity = new HashMap<>();
    private final Map<UUID, Boolean> originalCollectable = new HashMap<>();
    private final Map<Player, Long> playerLastGrabTime = new HashMap<>();
    private final Map<Player, Long> playerGrabTime = new HashMap<>();
    private final Map<UUID, Long> invulnerableEntities = new HashMap<>();

    static BukkitScheduler scheduler = Bukkit.getScheduler();
    int taskId;

    float task_i = 0.0F;
    float task_i_sound_amb = 0.0F;

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue(), value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void reload() {
        this.NAME = GravityGun.getInstance().getConfig().getString("item.name", "Gravity Gun");
        this.DISPLAY_NAME = Component.text(NAME).color(TextColor.fromHexString("#FFAA00"));

        this.range = GravityGun.getInstance().getConfig().getDouble("mechanics.grab.range", 8.0D);
        this.grab_cooldown = GravityGun.getInstance().getConfig().getDouble("mechanics.grab.cooldown", 0.3D);

        this.pull_range = GravityGun.getInstance().getConfig().getDouble("mechanics.pull.range", 25.0D);
        this.pull_force = GravityGun.getInstance().getConfig().getDouble("mechanics.pull.force", 0.6D);

        this.launch_power = GravityGun.getInstance().getConfig().getDouble("mechanics.launch.power", 2.0D);

        this.hold_distance_min = GravityGun.getInstance().getConfig().getDouble("mechanics.hold.distance.min", 1.0D);
        this.hold_distance_max = GravityGun.getInstance().getConfig().getDouble("mechanics.hold.distance.max", 8.0D);
        this.hold_distance_step = GravityGun.getInstance().getConfig().getDouble("mechanics.hold.distance.step", 1.0D);
        this.hold_max_time = GravityGun.getInstance().getConfig().getDouble("mechanics.hold.max-time", 0.0D);

        this.allow_grab_entities = GravityGun.getInstance().getConfig().getBoolean("limits.entities.allow-grab", true);
        this.allow_grab_player = GravityGun.getInstance().getConfig().getBoolean("limits.entities.allow-grab-players", true);
        this.list_entities_mode = ListMode.safeValueOf(GravityGun.getInstance().getConfig().getString("limits.entities.list-mode", "BLACKLIST").toUpperCase());
        this.list_entity_types = GravityGun.getInstance().getConfig().getList("limits.entities.list", List.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME, EntityType.PAINTING)).stream().filter(e -> e instanceof EntityType).map(e -> (EntityType)e).toList();

        this.allow_grab_blocks = GravityGun.getInstance().getConfig().getBoolean("limits.blocks.allow-grab", false);
        this.list_blocks_mode = ListMode.safeValueOf(GravityGun.getInstance().getConfig().getString("limits.blocks.list-mode", "BLACKLIST").toUpperCase());
        this.list_block_types = GravityGun.getInstance().getConfig().getList("limits.blocks.list", List.of(Material.BEDROCK)).stream().filter(e -> e instanceof Material).map(e -> (Material)e).toList();
    }

    public Manager() {
        reload();
        this.taskId = scheduler.runTaskTimer(GravityGun.getInstance(), () -> {
            if (this.task_i > 5.0F) {
                this.task_i = 0.0F;
            } else {
                ++this.task_i;
            }

            if (this.task_i_sound_amb > 20.0F) {
                this.task_i_sound_amb = 0.0F;
            } else {
                ++this.task_i_sound_amb;
            }


            for (Player player : Bukkit.getOnlinePlayers()) {
                if (this.playerEntityMap.containsKey(player)) {
                    Location entityLocation = this.playerEntityLocationMap.get(player);
                    Location eyeLocation = player.getEyeLocation();
                    double radius = (new Location(player.getWorld(), 0.0D, 0.0D, 0.0D)).distance(entityLocation);
                    double yawAngle = Math.toRadians((90.0F + eyeLocation.getYaw()));
                    double pitchAngle = -Math.toRadians(eyeLocation.getPitch());
                    double offsetX = radius * Math.cos(yawAngle) * Math.cos(pitchAngle);
                    double offsetY = radius * Math.sin(pitchAngle);
                    double offsetZ = radius * Math.sin(yawAngle) * Math.cos(pitchAngle);
                    double newX = eyeLocation.getX() + offsetX;
                    double newY = eyeLocation.getY() + offsetY;
                    double newZ = eyeLocation.getZ() + offsetZ;

                    float yaw = eyeLocation.getYaw();
                    float pitch = entityLocation.getPitch();

                    Entity entity = this.playerEntityMap.get(player);

                    if (entity instanceof Player) {
                        yaw = ((Player) entity).getEyeLocation().getYaw();
                        pitch = ((Player) entity).getEyeLocation().getPitch();
                    }
                    if (entity instanceof BlockDisplay) {
                        yaw = 0;
                        pitch = 0;
                    }
                    Location newLocation = new Location(player.getWorld(), newX, newY, newZ, yaw, pitch);

                    if (!canEntityTeleportSafely(entity, newLocation)) {
                        double adjustedRadius = Math.max(hold_distance_min, radius - 0.5);
                        double adjustedOffsetX = adjustedRadius * Math.cos(yawAngle) * Math.cos(pitchAngle);
                        double adjustedOffsetY = adjustedRadius * Math.sin(pitchAngle);
                        double adjustedOffsetZ = adjustedRadius * Math.sin(yawAngle) * Math.cos(pitchAngle);
                        newLocation = new Location(player.getWorld(), eyeLocation.getX() + adjustedOffsetX, eyeLocation.getY() + adjustedOffsetY, eyeLocation.getZ() + adjustedOffsetZ, yaw, pitch);

                        if (!canEntityTeleportSafely(entity, newLocation)) {
                            continue;
                        }
                    }

                    Location oldLocation = entity.getLocation();
                    double teleportDistance = newLocation.distance(oldLocation);


                    if (teleportDistance > 2.0) {
                        invulnerableEntities.put(entity.getUniqueId(), System.currentTimeMillis() + 1000);
                    }

                    (this.playerEntityMap.get(player)).teleportAsync(newLocation);
                    PersistentDataContainer persistentDataContainer = (this.playerEntityMap.get(player)).getPersistentDataContainer();
                    if (this.task_i == 0.0F) {
                        (this.playerEntityMap.get(player)).setVelocity(new Vector(offsetX - (double)(Float)persistentDataContainer.get(this.keyGG_vx, PersistentDataType.FLOAT), offsetY - (double)(Float)persistentDataContainer.get(this.keyGG_vy, PersistentDataType.FLOAT), offsetZ - (double)(Float)persistentDataContainer.get(this.keyGG_vz, PersistentDataType.FLOAT)));
                        persistentDataContainer.set(this.keyGG_vx, PersistentDataType.FLOAT, (float)offsetX);
                        persistentDataContainer.set(this.keyGG_vy, PersistentDataType.FLOAT, (float)offsetY);
                        persistentDataContainer.set(this.keyGG_vz, PersistentDataType.FLOAT, (float)offsetZ);
                    }

                    if (hold_max_time > 0) {
                        long now = System.currentTimeMillis();
                        long grabTime = playerGrabTime.get(player);
                        if ((now - grabTime) > hold_max_time * 1000) {
                            release(player);
                        }
                    }

                    if (this.task_i_sound_amb == 0f) {
                        player.getWorld().playSound(player, Sound.BLOCK_BEACON_AMBIENT, 1f, 1f);
                    }

                    double maxHoldDistance = hold_distance_max+5D;
                    if (player.getLocation().distance(entity.getLocation()) > maxHoldDistance) {
                        release(player);
                        continue;
                    }

                }
            }

        }, 1L, 1L).getTaskId();
    }

    public boolean isAllowEntityType(EntityType type) {
        if (list_entities_mode == ListMode.DISABLED) return true;
        boolean contains = list_entity_types.contains(type);
        if (list_entities_mode == ListMode.WHITELIST && contains) return true;
        return list_entities_mode == ListMode.BLACKLIST && !contains;
    }

    public boolean isAllowMaterial(Material material) {
        if (list_blocks_mode == ListMode.DISABLED) return true;
        boolean contains = list_block_types.contains(material);
        if (list_blocks_mode == ListMode.WHITELIST && contains) return true;
        return list_blocks_mode == ListMode.BLACKLIST && !contains;
    }

    public ItemStack makeGravityGun() {
        ItemStack itemStack = new ItemStack(MATERIAL, 1);
        itemStack.editMeta(im -> {
            CrossbowMeta cm = (CrossbowMeta)im;
            CustomModelDataComponent customModelDataComponent = cm.getCustomModelDataComponent();
            customModelDataComponent.setStrings(List.of("gravity-gun"));
            cm.setCustomModelDataComponent(customModelDataComponent);
            cm.displayName(DISPLAY_NAME);
        });
        itemStack.editPersistentDataContainer(pdc -> {
            pdc.set(this.keyGravityGun, PersistentDataType.BOOLEAN, true);
        });
        return itemStack;
    }

    public void giveTool(Player player) {
        player.getInventory().addItem(makeGravityGun());
    }

    private boolean isGravityGun(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keyGravityGun, PersistentDataType.BOOLEAN);
    }

    @EventHandler
    void PlayerItemHeldEvent(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (this.playerEntityMap.containsKey(player)) {
            boolean left_key = event.getPreviousSlot() > event.getNewSlot();
            boolean right_key = event.getPreviousSlot() < event.getNewSlot();
            if (Math.abs(event.getPreviousSlot() - event.getNewSlot()) > 4) {
                boolean temp = left_key;
                left_key = right_key;
                right_key = temp;
            }
            ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());
            if (oldItem != null) {
                if ((left_key || right_key)) {
                    if (isGravityGun(oldItem)) {
                        event.setCancelled(true);
                        Location entityLocation = this.playerEntityLocationMap.get(player);
                        double current_radius = new Location(player.getWorld(), 0.0D, 0.0D, 0.0D).distance(entityLocation);
                        double delta = left_key ? 1.0 : -1.0;
                        double step = hold_distance_step;
                        double new_radius = current_radius + delta * step;
                        new_radius = Math.max(hold_distance_min, Math.min(hold_distance_max, new_radius));

                        // Проверяем коллизию для новой позиции
                        Location eyeLocation = player.getEyeLocation();
                        double yawAngle = Math.toRadians((90.0F + eyeLocation.getYaw()));
                        double pitchAngle = -Math.toRadians(eyeLocation.getPitch());
                        double offsetX = new_radius * Math.cos(yawAngle) * Math.cos(pitchAngle);
                        double offsetY = new_radius * Math.sin(pitchAngle);
                        double offsetZ = new_radius * Math.sin(yawAngle) * Math.cos(pitchAngle);
                        Location newLocation = new Location(player.getWorld(), eyeLocation.getX() + offsetX, eyeLocation.getY() + offsetY, eyeLocation.getZ() + offsetZ, eyeLocation.getYaw(), entityLocation.getPitch());

                        Entity entity = this.playerEntityMap.get(player);
                        if (!canEntityTeleportSafely(entity, newLocation)) {
                            return;
                        }

                        double scale = new_radius / current_radius;
                        if (current_radius != 0) {
                            entityLocation.setX(entityLocation.getX() * scale);
                            entityLocation.setY(entityLocation.getY() * scale);
                            entityLocation.setZ(entityLocation.getZ() * scale);
                        }
                    }
                }

            }
        }
    }

    void grab(Player player, Entity entity, ItemStack gravityGun) {
        long now = System.currentTimeMillis();
        if (playerLastGrabTime.containsKey(player) && (now - playerLastGrabTime.get(player)) < grab_cooldown * 1000) {
            return;
        }
        playerLastGrabTime.put(player, now);
        playerGrabTime.put(player, now);

        Location eyeLocation = player.getEyeLocation();
        this.playerEntityLocationMap.put(player, new Location(player.getWorld(), eyeLocation.getX() - entity.getX(), eyeLocation.getY() - entity.getY(), eyeLocation.getZ() - entity.getZ(), eyeLocation.getYaw() - entity.getYaw(), eyeLocation.getPitch() - entity.getPitch()));
        PersistentDataContainer persistentDataContainer = entity.getPersistentDataContainer();
        persistentDataContainer.set(this.keyGG_vx, PersistentDataType.FLOAT, 0.0F);
        persistentDataContainer.set(this.keyGG_vy, PersistentDataType.FLOAT, 0.0F);
        persistentDataContainer.set(this.keyGG_vz, PersistentDataType.FLOAT, 0.0F);
        this.playerEntityMap.put(player, entity);
        originalGravity.put(entity.getUniqueId(), entity.hasGravity());
        if (entity instanceof LivingEntity) {
            originalCollectable.put(entity.getUniqueId(), ((LivingEntity) entity).isCollidable());
            ((LivingEntity) entity).setCollidable(false);
            entity.setFallDistance(0);
        }
        entity.setGravity(false);
        entity.setVelocity(new Vector(0, 0, 0));

        gravityGun.editMeta(im -> {
            CrossbowMeta cm = (CrossbowMeta)im;
            cm.addChargedProjectile(ItemStack.of(Material.FIREWORK_ROCKET));
            CustomModelDataComponent customModelDataComponent = cm.getCustomModelDataComponent();
            customModelDataComponent.setStrings(List.of("gravity-gun-blue"));
            cm.setCustomModelDataComponent(customModelDataComponent);
        });
        gravityGun.editPersistentDataContainer(pdc -> {
            pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, true);
        });

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.1f);
    }

    void pull(Entity entity, Player player) {
        Vector direction = player.getLocation().subtract(entity.getLocation()).toVector().normalize();
        entity.setVelocity(direction.multiply(pull_force));
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
        BlockDisplay blockDisplay = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, bd -> {
            bd.setBlock(block.getBlockData());
            Transformation transformation = bd.getTransformation();
            transformation.getTranslation().set(-0.5f, -0.5f, -0.5f);
            bd.setTransformation(transformation);
        });
        blockDisplay.setInterpolationDelay(0);
        blockDisplay.setInterpolationDuration(999999);

        PersistentDataContainer pdc = blockDisplay.getPersistentDataContainer();
        pdc.set(keyIsBlock, PersistentDataType.BOOLEAN, true);
        pdc.set(keyGG_is_holding, PersistentDataType.BOOLEAN, true);
        pdc.set(keyBlockType, PersistentDataType.STRING, block.getType().name());
        pdc.set(keyBlockData, PersistentDataType.STRING, block.getBlockData().getAsString());
        if (inventoryData != null) {
            pdc.set(keyInventoryData, PersistentDataType.STRING, inventoryData);
        }
        block.setType(Material.AIR);

        grab(player, blockDisplay, gravityGun);
    }


    private boolean canEntityTeleportSafely(Entity entity, Location location) {
        if (entity == null || location == null || location.getWorld() == null) return false;

        // Создаем bounding box entity в новой позиции
        double width = entity.getWidth();
        double height = entity.getHeight();

        // Проверяем блоки в области bounding box
        for (double x = -width/2; x <= width/2; x += 0.5) {
            for (double y = 0; y <= height; y += 0.5) {
                for (double z = -width/2; z <= width/2; z += 0.5) {
                    Location checkLoc = location.clone().add(x, y, z);
                    if (checkLoc.getBlock().isSolid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    Entity release(Player player) {
        if (!this.playerEntityMap.containsKey(player)) return null;
        ItemStack item = player.getInventory().getItemInMainHand();

        Entity entity = this.playerEntityMap.get(player);

        this.playerEntityMap.remove(player);
        this.playerEntityLocationMap.remove(player);
        playerGrabTime.remove(player);
        Boolean g = originalGravity.remove(entity.getUniqueId());
        Boolean c = originalCollectable.remove(entity.getUniqueId());
        invulnerableEntities.remove(entity.getUniqueId()); // Удаляем из списка неуязвимых

        if (g != null) entity.setGravity(g);
        if (c != null && entity instanceof LivingEntity) ((LivingEntity) entity).setCollidable(c);
        if (entity instanceof LivingEntity) {
            entity.setFallDistance(0f);
        }

        item.editMeta(im -> {
            CrossbowMeta cm = (CrossbowMeta)im;
            cm.setChargedProjectiles(null);
            CustomModelDataComponent customModelDataComponent = cm.getCustomModelDataComponent();
            customModelDataComponent.setStrings(List.of("gravity-gun"));
            cm.setCustomModelDataComponent(customModelDataComponent);
        });
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

    void releaseAll() {
        for (Player player : playerEntityMap.keySet()) {
            release(player);
        }
    }

    void launch(Player player) {
        if (!this.playerEntityMap.containsKey(player)) return;
        Entity entity = release(player);
        entity.setVelocity(player.getLocation().getDirection().multiply(this.launch_power));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0f);
    }

    void launch(Player player, Entity entity) {
        entity.setVelocity(player.getLocation().getDirection().multiply(this.launch_power));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0f);
    }

    @EventHandler
    void onPlayerInteractEvent(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        boolean holdingGG = isGravityGun(player.getInventory().getItemInMainHand());
        if (!holdingGG) return;

        if (this.playerEntityMap.containsKey(player)) {
            event.setCancelled(true);
        }
        if (!player.getPersistentDataContainer().has(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN)) { player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, false); }
        if (player.getPersistentDataContainer().get(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN)) {
            return;
        }



        long now = System.currentTimeMillis();
        if (playerLastGrabTime.containsKey(player) && (now - playerLastGrabTime.get(player)) < 150) return;

        player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, true);
        boolean left_key = event.getAction().equals(Action.LEFT_CLICK_AIR) || event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().isLeftClick();
        boolean right_key = event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || event.getAction().isRightClick();
        if ((left_key || right_key) && isGravityGun(player.getInventory().getItemInMainHand())) {
            ItemStack item = player.getInventory().getItemInMainHand();
            event.setCancelled(true);
            if (this.playerEntityMap.containsKey(player)) {
                if (left_key) {
                    launch(player);
                } else {
                    release(player);
                }
            } else {
                Vector direction = player.getLocation().getDirection();
                if (right_key) {
                    // Сначала проверить сущности в range
                    RayTraceResult entityResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), direction, this.range, 1.0D, (entityx) -> {
                        return entityx != player && (entityx instanceof LivingEntity || entityx instanceof Projectile || entityx instanceof Item || entityx instanceof FallingBlock || entityx instanceof ItemDisplay);
                    });
                    if (entityResult != null) {
                        Entity hitEntity = entityResult.getHitEntity();
                        assert hitEntity != null;
                        boolean isCustomBlock = hitEntity instanceof FallingBlock && hitEntity.getPersistentDataContainer().has(keyIsBlock, PersistentDataType.BOOLEAN);
                        if (isCustomBlock) {
                            FallingBlock fb = (FallingBlock) hitEntity;
                            PersistentDataContainer pdc = fb.getPersistentDataContainer();
                            String rawData = pdc.get(keyBlockData, PersistentDataType.STRING);

                            if (rawData != null) {
                                BlockData data = Bukkit.createBlockData(rawData);
                                BlockDisplay blockDisplay = hitEntity.getWorld().spawn(hitEntity.getLocation(), BlockDisplay.class, bd -> {
                                    bd.setBlock(data);
                                    Transformation transformation = bd.getTransformation();
                                    transformation.getTranslation().set(-0.5f, -0.5f, -0.5f);
                                    bd.setTransformation(transformation);
                                });

                                PersistentDataContainer newPdc = blockDisplay.getPersistentDataContainer();
                                newPdc.set(keyIsBlock, PersistentDataType.BOOLEAN, true);
                                newPdc.set(keyBlockType, PersistentDataType.STRING, pdc.get(keyBlockType, PersistentDataType.STRING));
                                newPdc.set(keyBlockData, PersistentDataType.STRING, rawData);
                                if (pdc.has(keyInventoryData, PersistentDataType.STRING)) {
                                    newPdc.set(keyInventoryData, PersistentDataType.STRING, pdc.get(keyInventoryData, PersistentDataType.STRING));
                                }

                                hitEntity.remove();
                                grab(player, blockDisplay, player.getInventory().getItemInMainHand());
                            }
                        } else if (isAllowEntityType(hitEntity.getType()) && (allow_grab_player || !(hitEntity instanceof Player))) {
                            grab(player, hitEntity, item);
                            player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, false);
                            return;
                        }
                    }

                    // Проверить блоки
                    RayTraceResult blockResult = player.getWorld().rayTraceBlocks(player.getEyeLocation(), direction, this.range);
                    if (blockResult != null) {
                        Block hitBlock = blockResult.getHitBlock();
                        assert hitBlock != null;
                        if (isAllowMaterial(hitBlock.getType()) && allow_grab_blocks) {
                            grabBlock(player, hitBlock, item);
                            player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, false);
                            return;
                        }
                    }

                    // Если в основном радиусе ничего нет, проверяем расширенный радиус для потягивания
                    RayTraceResult pullResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), direction, this.pull_range, 1.0D, (entityx) -> {
                        return entityx != player && (entityx instanceof LivingEntity || entityx instanceof Projectile || entityx instanceof Item || entityx instanceof FallingBlock || entityx instanceof ItemDisplay);
                    });
                    if (pullResult != null) {
                        Entity hitEntity = pullResult.getHitEntity();
                        assert hitEntity != null;
                        if (isAllowEntityType(hitEntity.getType()) && (allow_grab_player || !(hitEntity instanceof Player))) {
                            pull(hitEntity, player);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.5f);
                            player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, false);
                            return;
                        }
                    }

                    player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0f);
                }
            }
        }
        player.getPersistentDataContainer().set(keyGG_playerInteractBlock, PersistentDataType.BOOLEAN, false);
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

    @EventHandler public void onQuit(PlayerQuitEvent e)  { release(e.getPlayer()); }
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
}
