package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;
import org.bukkit.entity.Player;

public class VaultHook {
    private final InMcFishing plugin;
    private boolean available = false;
    private Object economy = null;

    public VaultHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> vaultClass = Class.forName("net.milkbowl.vault.Vault");
            var vaultInstance = vaultClass.getField("economy").get(null);
            if (economyClass.isInstance(vaultInstance)) {
                this.economy = vaultInstance;
                this.available = true;
                plugin.getLogger().info("Vault detected and hooked successfully.");
            }
        } catch (Exception e) {
            this.available = false;
            plugin.getLogger().info("Vault not found. Economy features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean deposit(Player player, double amount) {
        if (!available || economy == null) {
            return false;
        }

        try {
            var depositMethod = economy.getClass().getMethod("depositPlayer", Player.class, double.class);
            var response = depositMethod.invoke(economy, player, amount);
            var transactionSuccessMethod = response.getClass().getMethod("transactionSuccess");
            return (Boolean) transactionSuccessMethod.invoke(response);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deposit " + amount + " to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!available || economy == null) {
            return false;
        }

        try {
            var withdrawMethod = economy.getClass().getMethod("withdrawPlayer", Player.class, double.class);
            var response = withdrawMethod.invoke(economy, player, amount);
            var transactionSuccessMethod = response.getClass().getMethod("transactionSuccess");
            return (Boolean) transactionSuccessMethod.invoke(response);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to withdraw " + amount + " from " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public double getBalance(Player player) {
        if (!available || economy == null) {
            return 0.0;
        }

        try {
            var getBalanceMethod = economy.getClass().getMethod("getBalance", Player.class);
            return (Double) getBalanceMethod.invoke(economy, player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get balance for " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    public boolean has(Player player, double amount) {
        if (!available || economy == null) {
            return false;
        }

        try {
            var hasMethod = economy.getClass().getMethod("has", Player.class, double.class);
            return (Boolean) hasMethod.invoke(economy, player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check balance for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
