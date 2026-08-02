package me.ninesik.fishing.config;

import me.ninesik.fishing.InMcFishing;
import me.ninesik.fishing.fight.FightConfig;
import me.ninesik.fishing.util.Texts;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.List;
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
     * Trophy Fight 설정을 구조화된 {@link FightConfig} 객체로 반환한다.
     * 개별 getter가 아닌 객체 자체를 반환하여 이후 유지보수를 용이하게 한다.
     *
     * @return FightConfig 객체 (AI/HUD/Sound/Stats/General 카테고리 포함)
     */
    public FightConfig getFightConfig() {
        return new FightConfig(config);
    }

    // ===================== fatigue (유저 피드백: 자동 낚시 피로도 시스템) =====================

    /** 신규 플레이어의 기본 최대 피로도 (fatigue.default). */
    public int getFatigueDefaultMax() {
        return config.getInt("fatigue.default", 1000);
    }

    /** 낚싯대 보너스를 포함해도 넘을 수 없는 절대 상한 (fatigue.max). */
    public int getFatigueAbsoluteMax() {
        return config.getInt("fatigue.max", 20000);
    }

    /** 자연 회복 1회당 회복량 (fatigue.recovery.amount). */
    public int getFatigueRecoveryAmount() {
        return config.getInt("fatigue.recovery.amount", 200);
    }

    /** 자연 회복 주기(초), 최소 1초 (fatigue.recovery.interval). */
    public int getFatigueRecoveryIntervalSeconds() {
        return Math.max(1, config.getInt("fatigue.recovery.interval", 600));
    }

    /** 자동 낚시 성공 시 등급별 피로도 소모량 (fatigue.consume.<grade>). */
    public int getFatigueConsume(String gradeId) {
        if (gradeId == null) return 0;
        return config.getInt("fatigue.consume." + gradeId.toLowerCase(), 0);
    }

    /** 피로도가 이 값 이하이면 미니게임이 강제 ON되고 OFF 전환이 잠긴다 (fatigue.auto-minigame-lock-threshold). */
    public int getFatigueLockThreshold() {
        return config.getInt("fatigue.auto-minigame-lock-threshold", 0);
    }

    /** 잠긴 상태에서 이 값 이상 회복되면 다시 OFF 전환이 가능해진다 (fatigue.auto-minigame-unlock-threshold). */
    public int getFatigueUnlockThreshold() {
        return config.getInt("fatigue.auto-minigame-unlock-threshold", 100);
    }

    /** 등급별 회복 물약의 회복량 (fatigue.potions.<grade>.amount). */
    public int getFatiguePotionAmount(String gradeId) {
        if (gradeId == null) return 0;
        return config.getInt("fatigue.potions." + gradeId.toLowerCase() + ".amount", 0);
    }

    /** 등급별 회복 물약의 표시 이름 (fatigue.potions.<grade>.name), '&' 색상 코드 미변환 원본. */
    public String getFatiguePotionName(String gradeId) {
        if (gradeId == null) return "&b피로회복 물약";
        return config.getString("fatigue.potions." + gradeId.toLowerCase() + ".name", "&b피로회복 물약");
    }

    /** 등급별 회복 물약의 Lore (fatigue.potions.<grade>.lore), '&' 색상 코드 미변환 원본. */
    public List<String> getFatiguePotionLore(String gradeId) {
        if (gradeId == null) return Collections.emptyList();
        return config.getStringList("fatigue.potions." + gradeId.toLowerCase() + ".lore");
    }

    /** 등급별 회복 물약의 베이스 Material (fatigue.potions.<grade>.material). */
    public String getFatiguePotionMaterial(String gradeId) {
        if (gradeId == null) return "POTION";
        return config.getString("fatigue.potions." + gradeId.toLowerCase() + ".material", "POTION");
    }

    public List<String> getAllowedWorlds() {
        return config.getStringList("settings.allowed-worlds");
    }

    public boolean isMinigameOffToggleAllowed() {
        return config.getBoolean("minigame-off.allow-player-toggle", true);
    }

    public int getAutoCatchDelaySeconds(String gradeId) {
        if (gradeId == null) return 5;
        return Math.max(1, config.getInt("minigame-off.catch-delay-seconds." + gradeId.toLowerCase(), 5));
    }

    /**
     * 미니게임 OFF 시 허용 등급 목록. 비어 있으면 모든 등급 허용.
     */
    public java.util.Set<String> getMinigameOffAllowedGrades() {
        java.util.List<String> grades = config.getStringList("minigame-off.allowed-grades");
        if (grades == null || grades.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String grade : grades) {
            if (grade != null && !grade.isBlank()) {
                result.add(grade.toLowerCase());
            }
        }
        return result;
    }

    public boolean isCastStatusEnabled() {
        return config.getBoolean("cast-status.enabled", true);
    }

    public String getCastStatusMessage(String key) {
        return config.getString("cast-status." + key, "");
    }

    public int getCastStatusRefreshIntervalTicks() {
        return Math.max(1, config.getInt("cast-status.refresh-interval-ticks", 20));
    }

    public String getAutoCatchActionBarFormat() {
        return config.getString("auto-catch.actionbar-format", "&e{time}s &7후 낚음...");
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
}
