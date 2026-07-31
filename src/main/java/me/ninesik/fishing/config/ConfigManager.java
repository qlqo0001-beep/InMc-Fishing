package me.ninesik.fishing.config;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.util.Texts;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * config.yml / modifiers.yml 값을 읽어오는 게이트웨이.
 *
 * DECISION-NEEDED (신규): modifiers.yml의 weather 섹션에는 RAIN/CLEAR만 정의되어 있고 THUNDER 항목이
 * 없다 (FISHING_PLUGIN_PLAN.md 29.5는 THUNDER를 언급하지 않음). 우선 THUNDER를 RAIN과 동일하게 취급한다
 * (뇌우는 비의 상위 개념으로 간주). 별도의 THUNDER 배수가 필요하면 modifiers.yml의 weather 섹션에
 * "THUNDER" 키를 추가하고 아래 getWeatherModifier의 fallback을 제거할 것. PROGRESS.md에 기록됨.
 *
 * DECISION-NEEDED (신규): buff Modifier(포션 효과 기반 가중치 보정)는 버프 시스템 자체가 아직 없어
 * 이번 세션 범위에서 구현하지 않는다. WeightCalculator는 buff modifier를 항상 1.0으로 취급하며, 이는
 * "미구현" 상태를 정직하게 유지하는 것이다 (스텁을 감추듯 구현하지 말 것 — PROGRESS.md D항목 권장 수정 4번).
 */
public class ConfigManager {
    private final InMcFishing plugin;
    private FileConfiguration config;
    private FileConfiguration modifiers;

    public ConfigManager(InMcFishing plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        this.config = plugin.getConfig();

        File modifiersFile = new File(plugin.getDataFolder(), "modifiers.yml");
        if (!modifiersFile.exists()) {
            plugin.saveResource("modifiers.yml", false);
        }
        this.modifiers = YamlConfiguration.loadConfiguration(modifiersFile);
    }

    public double getBigFishChance() {
        return config.getDouble("rates.big-fish-chance", 1.0);
    }

    public double getDoubleChance() {
        return config.getDouble("rates.double-chance", 7.0);
    }

    public boolean isAllowUnregisteredVanillaRod() {
        return config.getBoolean("settings.allow-unregistered-vanilla-rod", true);
    }

    public boolean isDropOverflowItems() {
        return config.getBoolean("settings.drop-overflow-items", true);
    }

    public boolean isRequireEmptySlot() {
        return config.getBoolean("settings.require-empty-slot", true);
    }

    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    public boolean isDebug() {
        return config.getBoolean("settings.debug", false);
    }

    /**
     * messages.<key> 값을 읽어 '&' 색상 코드를 변환해서 반환한다. 없으면 빈 문자열.
     * placeholder 치환은 하지 않는다 — {@link #formatMessage(String, Map)} 사용.
     */
    public String getMessage(String key) {
        String raw = config.getString("messages." + key, "");
        return Texts.colorize(raw);
    }

    /**
     * messages.<key>를 읽고 placeholder 치환 후 색상 변환한다.
     * {prefix}는 messages.prefix 값으로 자동 치환된다.
     */
    public String formatMessage(String key, Map<String, String> placeholders) {
        String raw = config.getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String prefix = config.getString("messages.prefix", "");
        Map<String, String> merged = new java.util.HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        merged.putIfAbsent("prefix", prefix != null ? prefix : "");
        return Texts.colorize(Texts.apply(raw, merged));
    }

    public String formatMessage(String key) {
        return formatMessage(key, Collections.emptyMap());
    }

    /**
     * sounds.<key> 값을 반환 (예: entity.player.levelup). 없으면 빈 문자열.
     */
    public String getSound(String key) {
        return config.getString("sounds." + key, "");
    }

    // modifiers.yml의 실제 스키마는 "modifiers." 접두어 없이 world/biome/weather/time/permission이
    // 루트에 바로 있고, 각 조건 아래 grade-weight-multiplier.<GRADE> 형태로 등급별 배수를 가진다 (29.5).
    // gradeId는 Grade.getId()가 소문자(f,e,d,c,b,a,s)를 반환하므로 조회 시 대문자로 변환한다.

    public double getWorldModifier(String worldName, String gradeId) {
        if (worldName == null || gradeId == null) return 1.0;
        return modifiers.getDouble("world." + worldName + ".grade-weight-multiplier." + gradeId.toUpperCase(), 1.0);
    }

    public double getBiomeModifier(String biomeName, String gradeId) {
        if (biomeName == null || gradeId == null) return 1.0;
        return modifiers.getDouble("biome." + biomeName + ".grade-weight-multiplier." + gradeId.toUpperCase(), 1.0);
    }

    public double getWeatherModifier(String weather, String gradeId) {
        if (weather == null || gradeId == null) return 1.0;
        String path = "weather." + weather + ".grade-weight-multiplier." + gradeId.toUpperCase();
        if ("THUNDER".equalsIgnoreCase(weather) && !modifiers.contains("weather.THUNDER")) {
            // DECISION-NEEDED (위 클래스 주석 참고): THUNDER 항목이 없으면 RAIN으로 대체
            path = "weather.RAIN.grade-weight-multiplier." + gradeId.toUpperCase();
        }
        return modifiers.getDouble(path, 1.0);
    }

    public double getTimeModifier(String time, String gradeId) {
        if (time == null || gradeId == null) return 1.0;
        return modifiers.getDouble("time." + time + ".grade-weight-multiplier." + gradeId.toUpperCase(), 1.0);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * permission 섹션에 정의된 모든 권한 인덱스를 순회해서, 플레이어가 가진 권한의 배수를 모두 곱해 반환한다.
     * (여러 권한을 동시에 보유할 수 있으므로 누적 곱연산으로 처리 — 15.2의 "모든 Modifier는 곱연산으로 통일" 기준)
     */
    public double getPermissionModifier(Player player, String gradeId) {
        if (player == null || gradeId == null) return 1.0;
        ConfigurationSection permissionSection = modifiers.getConfigurationSection("permission");
        if (permissionSection == null) return 1.0;

        double result = 1.0;
        for (String permissionNode : permissionSection.getKeys(false)) {
            if (player.hasPermission(permissionNode)) {
                result *= modifiers.getDouble(
                        "permission." + permissionNode + ".grade-weight-multiplier." + gradeId.toUpperCase(), 1.0);
            }
        }
        return result;
    }

        if (event.getAction().toString().contains("RIGHT_CLICK")) {
            // 우클릭: 현재 틱 기록 후 정상 처리
            lastRightClickTick.put(player.getUniqueId(), currentTick);
            game.handleInput(player, MiniGame.InputType.RIGHT_CLICK);
            event.setCancelled(true);
        } else if (event.getAction().toString().contains("LEFT_CLICK")) {
            // 좌클릭: 같은 틱에 우클릭이 있었다면 유령 클릭으로 무시
            Integer lastTick = lastRightClickTick.get(player.getUniqueId());
            if (lastTick != null && lastTick == currentTick) {
                return; // 유령 좌클릭 무시
            }
            game.handleInput(player, MiniGame.InputType.LEFT_CLICK);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            cleanupPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    /**
     * 손에 든 아이템으로 등록된 낚싯대를 조회한다. (29.1)
     *
     * - MMOItems 아이템: rod.yml에 mmoitems-type: ROD로 등록되어 있으면 Matched, 아니면
     *   UnregisteredMmoItemRod (낚시 차단 — 미등록 MMOItems 낚싯대를 통한 우회 악용 방지).
     * - 바닐라 FISHING_ROD: rod.yml의 vanilla-name(색상 변환 후)과 실제 displayName이 일치하면
     *   Matched, 일치하는 게 없으면 UnregisteredVanilla (보너스 0으로 낚시 정상 진행 허용).
     * - 그 외: NotARod (낚시 차단 — BITE 상태는 이미 낚싯대를 든 상태에서만 발생하므로 실질적으로는
     *   거의 발생하지 않지만, 방어적으로 차단 처리한다).
     */
    private RodLookupResult lookupRod(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return new RodLookupResult.NotARod();
        }

        // MMOItems 아이템 확인
        if (dependencyManager.getMMOItems().isAvailable() &&
            dependencyManager.getMMOItems().isMMOItem(item)) {
            String mmoItemId = dependencyManager.getMMOItems().getMMOItemId(item);
            if (mmoItemId != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if ("mmoitems".equalsIgnoreCase(rod.getUseType()) && mmoItemId.equals(rod.getMmoitemsId())) {
                        return new RodLookupResult.Matched(rod);
                    }
                }
            }
            return new RodLookupResult.UnregisteredMmoItemRod();
        }

        // 바닐라 낚싯대 확인 (rod.yml의 vanilla-name을 실제로 조회해서 매칭 — 하드코딩 문자열 비교 금지)
        if (item.getType() == Material.FISHING_ROD) {
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;

            if (displayName != null) {
                for (Rod rod : rodRegistry.getAll().values()) {
                    if (!"vanilla".equalsIgnoreCase(rod.getUseType())) {
                        continue;
                    }
                    String configuredName = rod.getVanillaName();
                    if (configuredName == null || configuredName.isEmpty()) {
                        continue;
                    }
                    String translated = ChatColor.translateAlternateColorCodes('&', configuredName);
                    if (translated.equals(displayName)) {
                        return new RodLookupResult.Matched(rod);
                    }
                }
            }

            // 29.1: 이름/로어가 없거나(또는 등록된 이름과 매칭되지 않는) 일반 바닐라 FISHING_ROD →
            // rod.yml에 없어도 낚시는 정상 진행, 보너스만 0으로 취급
            return new RodLookupResult.UnregisteredVanilla();
        }

        return new RodLookupResult.NotARod();
    }

    private sealed interface RodLookupResult
            permits RodLookupResult.Matched, RodLookupResult.UnregisteredVanilla,
                    RodLookupResult.UnregisteredMmoItemRod, RodLookupResult.NotARod {
        record Matched(Rod rod) implements RodLookupResult {}
        record UnregisteredVanilla() implements RodLookupResult {}
        record UnregisteredMmoItemRod() implements RodLookupResult {}
        record NotARod() implements RodLookupResult {}
    }
}