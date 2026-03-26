# Gravity Gun

A Minecraft plugin that recreates the Gravity Gun tool from Half-Life 2. The right mouse button can be used to pick up a creature or block, or to drop it. The left mouse button can be used to push a creature or launch it if the object is captured. 

| Command       | Premission         | Description                                        |
|---------------|--------------------|----------------------------------------------------|
| help          |                    | Show this help                                     |
| reload        | gravity-gun.reload | Reload the plugin configuration                    |
| give [player] | gravity-gun.give   | Give the gravity gun to yourself or another player |

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
