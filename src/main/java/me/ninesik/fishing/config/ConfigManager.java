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
    private FileConfiguration messages;
    private FileConfiguration fight;
    private FileConfiguration fatigue;
    private FileConfiguration potions;
    private ItemFormatConfig itemFormat;
    /**
     * 플러그인이 기대하는 config.yml 구조 버전.
     * config.yml의 구조를 바꿀 때(키 추가/삭제/의미 변경) 이 값을 올린다.
     */
    private static final int CONFIG_VERSION = 1;

    /** fight.yml을 파싱한 결과. load() 시점에만 만들고 그 뒤로는 재사용한다. */
    private FightConfig fightConfig;
    /** settings.allowed-worlds. 조회가 잦아 로드 시점에 한 번만 읽는다. */
    private List<String> allowedWorlds = List.of();
    private java.util.Set<String> allowedWorldSet = java.util.Set.of();

    public ConfigManager(InMcFishing plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * 모든 설정 파일(config/modifiers/messages/fight/fatigue/items-potions/item-format)을 (재)로드한다.
     * /fishing reload 및 서버 리로드 시 호출된다 (피드백: "리로드가 지금 yml의 모든 값을 리로드해야 함").
     */
    public void load() {
        this.config = plugin.getConfig();
        this.messages = loadResource("messages.yml");
        this.fight = loadResource("fight.yml");
        this.fatigue = loadResource("fatigue.yml");
        this.potions = loadResource("items/potions.yml");
        this.modifiers = loadResource("modifiers.yml");
        this.itemFormat = new ItemFormatConfig(loadResource("item-format.yml"));
        // FightConfig는 여기서 한 번만 만든다 — getFightConfig() 주석 참고.
        this.fightConfig = new FightConfig(this.fight);
        this.allowedWorlds = List.copyOf(config.getStringList("settings.allowed-worlds"));
        this.allowedWorldSet = java.util.Set.copyOf(this.allowedWorlds);

        checkConfigVersion();
    }

    /**
     * config.yml의 구조 버전이 플러그인이 기대하는 값과 같은지 확인한다.
     *
     * <p>{@code saveResource(..., false)}는 기존 파일을 덮어쓰지 않으므로, 플러그인을
     * 업데이트해도 어드민의 config.yml은 예전 구조 그대로 남는다. 새 키가 추가되거나
     * 의미가 바뀌었을 때 이를 알려줄 유일한 신호가 이 버전이다.
     * (예전에는 키만 있고 읽는 코드가 없어 아무 역할도 하지 않았다)</p>
     */
    private void checkConfigVersion() {
        int found = config.getInt("config-version", -1);
        if (found == CONFIG_VERSION) {
            return;
        }
        if (found < 0) {
            plugin.getLogger().warning("config.yml에 config-version이 없습니다. "
                    + "예전 버전의 설정 파일일 수 있습니다 (기대값: " + CONFIG_VERSION + ").");
        } else {
            plugin.getLogger().warning("config.yml의 config-version이 " + found
                    + "입니다 (기대값: " + CONFIG_VERSION + "). 설정 구조가 바뀌었을 수 있으니 "
                    + "RELEASES.md의 변경 사항을 확인하세요.");
        }
    }

    /** 리소스를 데이터 폴더에 최초 1회 저장하고 로드해서 반환한다. */
    private FileConfiguration loadResource(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public double getBigFishChance() {
        return config.getDouble("rates.big-fish-chance", 1.0);
    }

    /** 낚싯대 아이템 로어 포맷 설정을 반환한다 (item-format.yml). */
    public ItemFormatConfig getItemFormat() {
        return itemFormat;
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
     * messages.yml 의 메시지&lt;key&gt; 값을 읽어 '&amp;' 색상 코드를 변환해서 반환한다. 없으면 빈 문자열.
     * key 는 카테고리가 포함된 전체 경로다 (예: "catch.caught", "fail.tension", "minigame.on").
     * placeholder 치환은 하지 않는다 — {@link #formatMessage(String, Map)} 사용.
     */
    public String getMessage(String key) {
        if (messages.isConfigurationSection(key)) return "";
        String raw = messages.getString(key, "");
        return Texts.colorize(raw);
    }

    /** messages.yml 에서 key(전체 경로)로 읽되, 없으면 default 값을 색상 변환하여 반환한다. */
    public String getMessage(String key, String def) {
        if (messages.isConfigurationSection(key)) return Texts.colorize(def);
        String raw = messages.getString(key, def);
        return Texts.colorize(raw);
    }

    /**
     * messages.yml 에서 문자열 리스트를 읽어 색상 변환해 반환한다.
     * 키가 없거나 리스트가 아니면 빈 리스트 — 호출측이 그냥 아무것도 출력하지 않게 된다.
     *
     * <p>도움말처럼 "줄 수 자체를 어드민이 정하는" 텍스트에 쓴다. 줄마다 키를 따로
     * 두면 어드민이 줄을 추가·삭제할 수 없어서 리스트로 받는다.</p>
     */
    public List<String> getMessageList(String key) {
        List<String> raw = messages.getStringList(key);
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> colorized = new java.util.ArrayList<>(raw.size());
        for (String line : raw) {
            colorized.add(Texts.colorize(line));
        }
        return colorized;
    }

    /** messages.yml 의 raw 문자열을 그대로 반환한다 (색상 변환 안 함). 없으면 def 반환. */
    public String getMessageRaw(String key, String def) {
        if (messages.isConfigurationSection(key)) return def;
        return messages.getString(key, def);
    }

    /** messages.yml FileConfiguration 을 반환한다 (타이틀 수치 등 직접 조회용). */
    public FileConfiguration getMessagesConfig() {
        return messages;
    }

    /**
     * messages.yml 메시지를 읽고 placeholder 치환 후 색상 변환한다.
     * {prefix}는 messages.yml의 prefix 값으로 자동 치환된다.
     */
    public String formatMessage(String key, Map<String, String> placeholders) {
        if (messages.isConfigurationSection(key)) return "";
        String raw = messages.getString(key, "");
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String prefix = messages.getString("prefix", "");
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
     *
     * <p>예전에는 호출할 때마다 {@code new FightConfig(fight)}로 트리를 통째로 다시 만들었다.
     * 생성자가 서브 config 8개와 HashMap 약 14개를 만들고 YAML 경로를 80회 가까이 조회하는데,
     * {@code TrophyFightManager.tick()}이 <b>파이트가 0건이어도 매 틱(초당 20회)</b> 이걸
     * 불렀고 사운드 재생 경로는 세션 루프 안에서 또 불렀다. 순수 낭비였다.</p>
     *
     * <p>이제 {@link #load()}에서 한 번만 만들고 재사용한다. /fishing reload가 load()를
     * 거치므로 무효화는 자동이며, 반환 타입이 그대로라 호출처는 손댈 필요가 없다.</p>
     *
     * @return FightConfig 객체 (AI/HUD/Sound/Stats/General 카테고리 포함)
     */
    public FightConfig getFightConfig() {
        return fightConfig;
    }

    // ===================== fatigue (유저 피드백: 자동 낚시 피로도 시스템) =====================
    // 피로도 설정은 fatigue.yml (fatigue: 루트), 물약 아이템 정의는 items/potions.yml (potions: 루트)에서 읽는다.

    /** 신규 플레이어의 기본 최대 피로도 (fatigue.default). */
    public int getFatigueDefaultMax() {
        return fatigue.getInt("fatigue.default", 2000);
    }

    /** 낚싯대 보너스를 포함해도 넘을 수 없는 절대 상한 (fatigue.max). */
    public int getFatigueAbsoluteMax() {
        return fatigue.getInt("fatigue.max", 20000);
    }

    /** 자연 회복 1회당 회복량 (fatigue.recovery.amount). */
    public int getFatigueRecoveryAmount() {
        return fatigue.getInt("fatigue.recovery.amount", 100);
    }

    /** 자연 회복 주기(초), 최소 1초 (fatigue.recovery.interval). */
    public int getFatigueRecoveryIntervalSeconds() {
        return Math.max(1, fatigue.getInt("fatigue.recovery.interval", 300));
    }

    /** 자동 낚시 성공 시 등급별 피로도 소모량 (fatigue.consume.<grade>). */
    public int getFatigueConsume(String gradeId) {
        if (gradeId == null) return 0;
        return fatigue.getInt("fatigue.consume." + gradeId.toLowerCase(), 0);
    }

    /** 피로도가 이 값 이하이면 미니게임이 강제 ON되고 OFF 전환이 잠긴다 (fatigue.auto-minigame-lock-threshold). */
    public int getFatigueLockThreshold() {
        return fatigue.getInt("fatigue.auto-minigame-lock-threshold", 0);
    }

    /** 잠긴 상태에서 이 값 이상 회복되면 다시 OFF 전환이 가능해진다 (fatigue.auto-minigame-unlock-threshold). */
    public int getFatigueUnlockThreshold() {
        return fatigue.getInt("fatigue.auto-minigame-unlock-threshold", 100);
    }

    /** 등급별 회복 물약의 회복량 (potions.<grade>.amount) — items/potions.yml. */
    public int getFatiguePotionAmount(String gradeId) {
        if (gradeId == null) return 0;
        return potions.getInt("potions." + gradeId.toLowerCase() + ".amount", 0);
    }

    /** 등급별 회복 물약의 표시 이름 (potions.<grade>.name), '&' 색상 코드 미변환 원본 — items/potions.yml. */
    public String getFatiguePotionName(String gradeId) {
        if (gradeId == null) return "&b피로회복 물약";
        return potions.getString("potions." + gradeId.toLowerCase() + ".name", "&b피로회복 물약");
    }

    /** 등급별 회복 물약의 Lore (potions.<grade>.lore), '&' 색상 코드 미변환 원본 — items/potions.yml. */
    public List<String> getFatiguePotionLore(String gradeId) {
        if (gradeId == null) return Collections.emptyList();
        return potions.getStringList("potions." + gradeId.toLowerCase() + ".lore");
    }

    /** 등급별 회복 물약의 베이스 Material (potions.<grade>.material) — items/potions.yml. */
    public String getFatiguePotionMaterial(String gradeId) {
        if (gradeId == null) return "POTION";
        return potions.getString("potions." + gradeId.toLowerCase() + ".material", "POTION");
    }

    public List<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    /**
     * 해당 월드에서 낚시가 허용되는지. 목록이 비어 있으면 모든 월드를 허용한다.
     *
     * <p>{@code getStringList()}는 호출할 때마다 새 ArrayList를 만든다. 이 판정은 입질과
     * 캐스트 상태 액션바(찌를 던진 플레이어당 초당 1회)에서 불리므로, 로드 시점에 Set으로
     * 캐시해 매번 리스트를 새로 만들지 않게 한다.</p>
     */
    public boolean isWorldAllowed(String worldName) {
        return allowedWorldSet.isEmpty() || allowedWorldSet.contains(worldName);
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

    /** (messages.yml cast-status.<key> — 찌 액션바 표시 텍스트, 색상 미변환 원본) */
    public String getCastStatusMessage(String key) {
        return messages.getString("cast-status." + key, "");
    }

    public int getCastStatusRefreshIntervalTicks() {
        return Math.max(1, config.getInt("cast-status.refresh-interval-ticks", 20));
    }

    /** (messages.yml auto-catch.actionbar-format — 자동 낚시 액션바, 색상 미변환 원본) */
    public String getAutoCatchActionBarFormat() {
        return messages.getString("auto-catch.actionbar-format", "&e{time}s &7후 낚음...");
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
