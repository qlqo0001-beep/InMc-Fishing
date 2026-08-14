package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;

public class DependencyManager {
    private final InMcFishing plugin;
    private MMOItemsHook mmoItemsHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private VaultHook vaultHook;
    private WorldGuardHook worldGuardHook;

    public DependencyManager(InMcFishing plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        this.mmoItemsHook = new MMOItemsHook(plugin);
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin);
        this.vaultHook = new VaultHook(plugin);
        this.worldGuardHook = new WorldGuardHook(plugin);
    }

    public MMOItemsHook getMMOItems() {
        return mmoItemsHook;
    }

    public PlaceholderAPIHook getPlaceholderAPI() {
        return placeholderAPIHook;
    }

    public VaultHook getVault() {
        return vaultHook;
    }

    public WorldGuardHook getWorldGuard() {
        return worldGuardHook;
    }

    public void shutdown() {
        if (placeholderAPIHook != null) {
            placeholderAPIHook.unregister();
        }
    }
}