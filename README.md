# Gravity Gun

A Minecraft plugin that recreates the Gravity Gun tool from Half-Life 2. The right mouse button can be used to pick up a creature or block, or to drop it. The left mouse button can be used to push a creature or launch it if the object is captured. 

| Command       | Permission         | Description                                                    |
|---------------|--------------------|----------------------------------------------------------------|
| help          |                    | Show this help                                                 |
| reload        | gravity-gun.reload | Reload the plugin configuration                                |
| give [target] | gravity-gun.give   | Give the gravity gun to yourself or another player             |
| fix [target]  | gravity-gun.fix    | Fix gravity and collision player after unlucky player grabs it |
| debug place_all_rotation_blocks | gravity-gun.debug | Place samples of block types whose BlockData changes under a 90° rotation |

## Configuration

All settings live in `config.yml`. Missing options are added from the bundled defaults while the user's file keeps all existing values; the file is updated on startup and on `/gg reload`.

- `version` - config file format version.
- `debug` - enables extra logging about held block displays and restored block entities.
- `language` - language code for plugin messages and tooltips (`en_us`, `en_gb`, `ru_ru`, `uk_ua`).
- `item.gravity-gun.name` - custom name of the gravity gun item; an empty value falls back to the default name from the language file.

Mechanics:

- `mechanics.grab.range` - maximum distance to grab a block or entity.
- `mechanics.grab.cooldown` - minimum delay between two grab attempts.
- `mechanics.pull.range` - maximum distance for pull.
- `mechanics.pull.force` - strength of the pull force.
- `mechanics.hold.max-time` - maximum time the grabbed object may be held before it is released; `0` disables this limit.
- `mechanics.hold.display-items.enabled` - holds dropped items as item displays and keeps projectiles on their native entity models.
- `mechanics.hold.distance.min` / `mechanics.hold.distance.max` / `mechanics.hold.distance.step` - minimum, maximum and step of the distance between the held object and the player's eye.
- `mechanics.launch.power` - launch speed that is applied when the held object is launched with the left mouse button or released.
- `mechanics.release.momentum.enabled` - inherits player movement and camera-turn momentum on release and launch.
- `mechanics.release.momentum.multiplier` - strength of the inherited momentum.
- `mechanics.release.momentum.max-speed` - maximum added momentum in blocks per tick.

Effects:

- `effects.enabled` - toggles gravity gun and block-impact particle effects.

Limits:

- `limits.entities.allow-grab` - whether entities can be grabbed at all.
- `limits.entities.allow-grab-players` - whether players can be grabbed.
- `limits.entities.list-mode` - `BLACKLIST`, `WHITELIST` or `DISABLED` for `limits.entities.list`.
- `limits.entities.list` - entity type names to apply the entity grab list to.
- `limits.blocks.allow-grab` - whether blocks can be grabbed at all.
- `limits.blocks.list-mode` - `BLACKLIST`, `WHITELIST` or `DISABLED` for `limits.blocks.list`.
- `limits.blocks.list` - block names to apply the block grab list to.

## Effects and release momentum

The `effects.enabled` option in `config.yml` controls the gravity gun's particle effects, including block-impact particles. Release momentum can be adjusted with `mechanics.release.momentum.multiplier` and `mechanics.release.momentum.max-speed`.

The `mechanics.hold.display-items.enabled` option is enabled by default. Dropped items are held as item displays, while projectiles retain and move their native entity models. Dropped items and arrows cannot be picked up while held or for half a second after release; their original pickup state is then restored. Projectiles are aimed along the player's view and given at least the configured launch speed when released. The left mouse button launches a captured projectile forward; it works even though the held projectile sits under the crosshair, because the arm swing event is used as a fallback. On startup and on reload the plugin fills in configuration options that are missing from the user's config while keeping existing values.

Held block displays follow the player's crosshair and rotate smoothly with the camera; on release, the rotation snaps to the nearest 90° before the blocks are placed. Directional blocks and multi-block structures rotate according to the player's horizontal camera turn since pickup. Doors and beds are carried and placed together. Double chests intentionally split: the grabbed half becomes a single chest, and the remaining half on the ground also becomes single while keeping its own inventory intact. Beds are shown by a single display, and fences and panes update their connections after placement.

## Block entities

Block-entity blocks use a `BlockDisplay` whenever the block has a real 3D block model, so furnaces, barrels, beehives, campfires, shelves, vaults, droppers, dispensers, and similar blocks keep their normal block appearance while held. Blocks whose visuals only exist in a block-entity renderer or whose block model is empty are shown with their item model instead: chests (including copper and ender chests), beds, signs and hanging signs, banners, skulls, shulker boxes, decorated pots, conduits, and copper golem statues. Wall signs, wall banners, wall skulls, and wall hanging signs are displayed using their standing item form.

Contents and metadata such as chest inventories, sign text, banner patterns, skull profiles, pot sherds, copper golem poses, and custom names are stored with the held block and restored when it is placed. Details rendered only by a block-entity renderer are not visible while holding a block: shelf and campfire contents, vault animations, and the animated parts of spawners, for example. The bell is shown as a flat item because its body is rendered exclusively by its block-entity renderer; showing the block model would display only the mount.

The gravity gun only intercepts actual left and right clicks. Physical interactions such as walking on a sculk sensor are ignored, so they are neither cancelled nor accompanied by the grab sound. Setting `debug: true` in `config.yml` logs extra details about held block displays and restored block entities.

## For resource packs

For resource packs, there is the option to attach a model and texture to a crossbow via:
```
{
    "model": {
        "type": "select",
        "property": "custom_model_data",
        "fallback": {
            "type": "model",
            "model": "item/crossbow"
        },
        "cases": [
            {
                "when": "gravity-gun",
                "model": {
                    "type": "model",
                    "model": "item/gravity_gun"
                }
            },
            {
                "when": "gravity-gun-blue",
                "model": {
                    "type": "model",
                    "model": "item/gravity_gun_firework"
                }
            }
        ]
    }
}
```

![bStats Servers](https://bstats.org/signatures/bukkit/GravityGun.svg)
