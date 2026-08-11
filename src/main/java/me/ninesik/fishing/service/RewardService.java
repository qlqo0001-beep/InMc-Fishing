package me.ninesik.fishing.service;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.event.FishCatchEvent;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.net.NetManager;
import me.ninesik.fishing.util.InventoryUtil;
import me.ninesik.fishing.util.Sounds;
import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 미니게임 성공 시 보상 지급, 메시지/사운드, Fish.commands 실행을 담당한다.
 * 아이템 지급·메시지·사운드는 반드시 메인 스레드에서 호출해야 한다 (CLAUDE.md 비동기 경계).
 */
public class RewardService {
    private final JavaPlugin plugin;
    private final DependencyManager dependencyManager;
    private final ConfigManager configManager;
    private final Logger logger;

    private double trophyThreshold = 1.5;
    private double rareTrophyThreshold = 0.9;
    private String trophyLore = "&e🏆 트로피";
    private String rareTrophyLore = "&c🏆 레어 트로피";

    /** 어망 시스템 (선택적 — null이면 어망 저장 없이 기존 인벤토리 지급) */
    private NetManager netManager;

    public RewardService(JavaPlugin plugin, DependencyManager dependencyManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    /**
     * 트로피 Lore 표시에 사용할 임계값과 텍스트를 설정한다.
     * CollectionRewardService와 동일한 collections.yml 값을 공유하기 위해 사용.
     */
    public void setTrophyConfig(double trophyThreshold, double rareTrophyThreshold,
                                String trophyLore, String rareTrophyLore) {
        this.trophyThreshold = trophyThreshold;
        this.rareTrophyThreshold = rareTrophyThreshold;
        if (trophyLore != null) this.trophyLore = trophyLore;
        if (rareTrophyLore != null) this.rareTrophyLore = rareTrophyLore;
    }

    public double getTrophyThreshold() {
        return trophyThreshold;
    }

    public double getRareTrophyThreshold() {
        return rareTrophyThreshold;
    }

    /**
     * 어망 시스템을 연결한다. (InMcFishing.onEnable에서 호출)
     */
    public void setNetManager(NetManager netManager) {
        this.netManager = netManager;
    }

    /**
     * 미니게임 성공 시 호출. 아이템 지급 + 메시지 + 사운드 + commands.
     *
     * @return 아이템 지급에 성공(또는 overflow 드롭)했으면 true, 인벤 부족으로 거부되면 false
     */
    public boolean giveReward(Player player, RewardEntry reward) {
        if (player == null || reward == null || reward.getFish() == null) {
            return false;
        }

        Fish fish = reward.getFish();
        int amount = reward.getAmount();

        // 어망 시스템이 연결되어 있으면 우선 어망에 저장한다.
        // 어망이 꽉 차면 기존 인벤토리 지급으로 폴백한다.
        if (netManager != null && netManager.addFish(player, reward)) {
            // 표시명 (메시지용, 색상 포함)
            ItemStack tempItem = createItemStack(fish, 1, reward.getSize(), reward.isTrophy(), reward.isRareTrophy());
            announceCatch(player, reward, Texts.colorize(resolveDisplayName(fish, tempItem)));
            return true;
        }

        ItemStack item = createItemStack(fish, amount, reward.getSize(), reward.isTrophy(), reward.isRareTrophy());
        if (item == null) {
            logger.warning("Failed to create reward item for fish id=" + fish.getId()
                    + " use-type=" + fish.getUseType() + " player=" + player.getName());
            String msg = configManager.formatMessage("fail.no-rewards", placeholders(player, reward, ""));
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }
            return false;
        }

        // 인벤토리 여유 확인 (29.9)
        boolean canFit = InventoryUtil.canFit(player.getInventory(), item);
        if (!canFit) {
            if (!configManager.isDropOverflowItems()) {
                String msg = configManager.formatMessage("fail.no-empty-slot");
                if (!msg.isEmpty()) {
                    player.sendMessage(msg);
                }
                return false;
            }
        }

        // 실제 지급
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
        if (!remaining.isEmpty() && configManager.isDropOverflowItems()) {
            for (ItemStack drop : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        } else if (!remaining.isEmpty()) {
            // drop-overflow-items=false인데 일부만 들어간 경우 — 이미 들어간 건 되돌리기 어려우므로
            // 사전 canFit이 false였어야 함. 방어적으로 no-empty-slot 메시지만 출력.
            String msg = configManager.formatMessage("fail.no-empty-slot");
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }
            return false;
        }

        // 표시명 (메시지용, 색상 포함) — 8장 fallback 순서
        announceCatch(player, reward, Texts.colorize(resolveDisplayName(fish, item)));
        return true;
    }

    /**
     * 낚기 성공 후처리: 대어/성공/더블 메시지 + 트로피 공지 + 사운드 + Fish.commands +
     * FishCatchEvent 발행.
     *
     * <p>어망 저장 경로와 인벤토리 지급 경로가 이 블록을 그대로 복붙해서 갖고 있었다.
     * 한쪽만 고치는 사고를 막기 위해 한 곳으로 모았다 (순서·내용은 기존과 동일).</p>
     *
     * @param itemDisplay 색상이 적용된 표시명
     */
    private void announceCatch(Player player, RewardEntry reward, String itemDisplay) {
        Fish fish = reward.getFish();

        // 대어 메시지 (S 등급 승급 없음은 RollEngine에서 isBigFish=false로 처리됨)
        if (reward.isBigFish()) {
            String bigFishMsg = configManager.formatMessage("catch.big-fish", placeholders(player, reward, itemDisplay));
            if (!bigFishMsg.isEmpty()) {
                player.sendMessage(bigFishMsg);
            }
            Sounds.play(player, configManager.getSound("big-fish"));
        }

        // 성공/더블 메시지
        boolean effectiveDouble = reward.isDouble() && fish.isDoubleEnabled();
        String catchMsg = configManager.formatMessage(
                effectiveDouble ? "catch.caught-double" : "catch.caught",
                placeholders(player, reward, itemDisplay));
        if (!catchMsg.isEmpty()) {
            player.sendMessage(catchMsg);
        }

        // 트로피/레어 트로피 서버 공지 (피드백)
        announceTrophy(player, reward, itemDisplay);

        // 사운드
        Sounds.play(player, configManager.getSound("success"));
        if (effectiveDouble) {
            Sounds.play(player, configManager.getSound("double"));
        }

        // Fish.commands 콘솔 실행 (CLAUDE.md: 결과 로그 남김)
        runCommands(player, reward, itemDisplay);

        // 이벤트 발행 — 도감/랭킹/대회 등이 수신
        Bukkit.getPluginManager().callEvent(new FishCatchEvent(player, fish, reward));
    }

    /**
     * 미니게임 실패/타임아웃 시 호출. 일반 실패 메시지({@code messages.fail})를 사용한다.
     */
    public void handleFail(Player player) {
        handleFail(player, "fail.fail");
    }

    /**
     * 실패 원인별로 다른 메시지를 보여주기 위한 오버로드 (Trophy Fight 전용).
     * 피드백: "파이트시, 물고기가 도망가는 원인을 나눴으면 좋겠어."
     *
     * @param messageKey {@code messages.<messageKey>} 설정 키 (예: "fail-tension").
     *                   해당 키가 config.yml에 없거나 빈 문자열이면 일반 "fail" 메시지로
     *                   대체한다 — 서버 운영자가 config.yml을 갱신하지 않아도 메시지가
     *                   아예 사라지지 않도록 하는 안전장치다.
     */
    public void handleFail(Player player, String messageKey) {
        if (player == null) {
            return;
        }
        String key = (messageKey == null || messageKey.isEmpty()) ? "fail.fail" : messageKey;
        String msg = configManager.formatMessage(key);
        if (msg.isEmpty() && !"fail.fail".equals(key)) {
            msg = configManager.formatMessage("fail.fail");
        }
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }
        Sounds.play(player, configManager.getSound("fail"));
    }

    /**
     * 8장 명세의 display name fallback 순서를 구현한다:
     * 1. Fish.vanillaName (vanilla-name) — config에 명시된 표시명
     * 2. 실제 생성된 ItemStack의 메타 displayName
     * 3. MMOItems/ItemStack 자체 display name (이미 메타에 있음)
     * 4. Texts.humanize(Fish.id) — SNAKE_CASE → Title Case
     */
    public String resolveDisplayName(Fish fish, ItemStack item) {
        // 1. vanilla-name이 설정되어 있으면 최우선
        if (fish.getVanillaName() != null && !fish.getVanillaName().isEmpty()) {
            return fish.getVanillaName();
        }

        // 2. 생성된 ItemStack의 메타 displayName
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // 3-4. MMOItems/ItemStack 자체 타입 이름 → Texts.humanize fallback
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            return Texts.humanize(item.getType().name());
        }

        // 최종 fallback: fish.getId()를 humanize
        return Texts.humanize(fish.getId());
    }

    /**
     * 등급 접두사를 결합한 표시명을 반환한다. 예: "[F] 생대구"
     */
    public String formatDisplayNameWithGrade(Fish fish, String displayName) {
        Grade grade = fish.getGrade();
        String gradeColor = grade != null ? grade.getColor() : "&f";
        String gradeId = grade != null ? grade.getId().toUpperCase() : "?";
        return Texts.colorize(gradeColor + "[" + gradeId + "] " + "&f" + displayName);
    }

    /**
     * size 기반으로 트로피 등급을 판정한다.
     */
    private TrophyType evaluateTrophyType(Fish fish, double size) {
        if (!fish.hasSize() || size <= 0) return TrophyType.NONE;
        if (size >= fish.getMaxSize() * rareTrophyThreshold) return TrophyType.RARE;
        if (size >= fish.getAvgSize() * trophyThreshold) return TrophyType.NORMAL;
        return TrophyType.NONE;
    }

    private enum TrophyType {
        NONE, NORMAL, RARE
    }

    /**
     * 물고기 아이템(PDC)에서 추출한 정보.
     * 인벤→어망 이동, /fishing show 등에서 사용한다 (피드백).
     *
     * @param fishId      물고기 ID (없으면 null → 물고기 아이템이 아님)
     * @param size        이 아이템에 부여된 실제 크기(cm)
     * @param gradeId     등급 ID (저장돼 있을 때만)
     * @param isTrophy    일반 트로피 여부
     * @param isRareTrophy 레어 트로피 여부
     */
    public record FishItemData(String fishId, double size, String gradeId,
                               boolean isTrophy, boolean isRareTrophy) {
    }

    /**
     * 물고기 아이템(PDC)에서 fish_id/size/grade/is_trophy/is_rare_trophy를 읽는다.
     * PDC에 fish_id가 없으면(물고기 아이템이 아니면) null을 반환한다.
     */
    public FishItemData readFishItemData(ItemStack item) {
        // 표시명 유무는 판정 조건이 아니다 — PDC의 fish_id만으로 충분하다.
        // (hasDisplayName() 조건은 이름이 지워진 아이템을 물고기가 아닌 것으로 오판했다)
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String fishId = pdc.get(new NamespacedKey(plugin, "fish_id"), PersistentDataType.STRING);
        if (fishId == null || fishId.isEmpty()) {
            return null;
        }
        String gradeId = pdc.get(new NamespacedKey(plugin, "grade_id"), PersistentDataType.STRING);
        Double size = pdc.get(new NamespacedKey(plugin, "size"), PersistentDataType.DOUBLE);
        Byte trophy = pdc.get(new NamespacedKey(plugin, "is_trophy"), PersistentDataType.BYTE);
        Byte rare = pdc.get(new NamespacedKey(plugin, "is_rare_trophy"), PersistentDataType.BYTE);
        return new FishItemData(
                fishId,
                size != null ? size : 0.0,
                gradeId != null ? gradeId : "",
                trophy != null && trophy != 0,
                rare != null && rare != 0);
    }

    /**
     * size 기반 일반 트로피 여부를 공개한다 (어망의 기존 데이터 트로피 폴백용).
     */
    public boolean isTrophyBySize(Fish fish, double size) {
        return evaluateTrophyType(fish, size) == TrophyType.NORMAL;
    }

    /**
     * size 기반 레어 트로피 여부를 공개한다 (어망의 기존 데이터 트로피 폴백용).
     */
    public boolean isRareTrophyBySize(Fish fish, double size) {
        return evaluateTrophyType(fish, size) == TrophyType.RARE;
    }

    public ItemStack createItemStack(Fish fish, int amount) {
        return createItemStack(fish, amount, 0.0);
    }

    /**
     * Trophy Fight 시스템(패치예정.md): 사전 판정된 트로피 여부를 포함하여 아이템을 생성한다.
     * RewardEntry의 isTrophy/isRareTrophy 값을 사용하여 Lore에 트로피 문구를 표시한다.
     */
    public ItemStack createItemStack(Fish fish, int amount, double size, boolean isTrophy, boolean isRareTrophy) {
        String useType = fish.getUseType() != null ? fish.getUseType().toLowerCase() : "vanilla";

        if ("vanilla".equals(useType)) {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(fish.getVanillaMaterial());
            if (material == null) {
                logger.warning("Unknown vanilla material: " + fish.getVanillaMaterial() + " (fish=" + fish.getId() + ")");
                return null;
            }

            ItemStack item = new ItemStack(material, amount);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String baseName = resolveDisplayName(fish, item);
                meta.setDisplayName(formatDisplayNameWithGrade(fish, baseName));

                List<String> lore = new java.util.ArrayList<>();
                if (fish.getVanillaLore() != null && !fish.getVanillaLore().isEmpty()) {
                    lore.addAll(fish.getVanillaLore().stream()
                            .map(Texts::colorize)
                            .toList());
                }
                // 인벤토리 아이템에는 사이즈를 표시하지 않는다 (피드백: 어망에서만 사이즈 표시).
                // 사이즈는 PDC에만 저장된다 (인벤→어망 이동/샵/자랑용).
                appendTrophyLore(lore, isTrophy, isRareTrophy);
                if (fish.isFatiguePotion()) {
                    lore.add("§7피로도 회복: §a+" + fish.getFatigueRecovery());
                }
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                }

                if (fish.getCustomModelData() > 0) {
                    meta.setCustomModelData(fish.getCustomModelData());
                }

                applyPdc(meta, fish, size, isTrophy, isRareTrophy);
                item.setItemMeta(meta);
            }
            return item;

        } else if ("mmoitems".equals(useType)) {
            if (!dependencyManager.getMMOItems().isAvailable()) {
                logger.warning("MMOItems not available, cannot create item: "
                        + fish.getMmoitemsType() + ":" + fish.getMmoitemsId());
                return null;
            }
            ItemStack base = dependencyManager.getMMOItems()
                    .getMMOItem(fish.getMmoitemsType(), fish.getMmoitemsId());
            if (base == null) {
                logger.warning("MMOItems returned null for: "
                        + fish.getMmoitemsType() + ":" + fish.getMmoitemsId());
                return null;
            }
            ItemStack result = base.clone();
            result.setAmount(amount);

            org.bukkit.inventory.meta.ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                String baseName = resolveDisplayName(fish, result);
                meta.setDisplayName(formatDisplayNameWithGrade(fish, baseName));

                List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
                if (lore == null) lore = new java.util.ArrayList<>();
                // 인벤토리 아이템에는 사이즈를 표시하지 않는다 (피드백: 어망에서만 사이즈 표시).
                appendTrophyLore(lore, isTrophy, isRareTrophy);
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                }

                applyPdc(meta, fish, size, isTrophy, isRareTrophy);
                result.setItemMeta(meta);
            }
            return result;
        }

        logger.warning("Unknown use-type: " + useType + " (fish=" + fish.getId() + ")");
        return null;
    }

    /**
     * 세션 18: 사이즈 정보를 포함하여 아이템 생성.
     *
     * <p>사전 판정된 트로피 정보가 없는 호출 경로용이다. size로 트로피 여부를 판정한 뒤
     * 5인자 버전에 위임한다. 예전에는 5인자 버전과 거의 같은 코드가 통째로 복제돼 있었고
     * (약 85줄), 실제 차이는 "트로피 판정을 어디서 받느냐"뿐이었다.</p>
     *
     * @param size 물고기 사이즈(cm), 사이즈 없는 아이템은 0.0
     */
    public ItemStack createItemStack(Fish fish, int amount, double size) {
        TrophyType trophyType = evaluateTrophyType(fish, size);
        return createItemStack(fish, amount, size,
                trophyType == TrophyType.NORMAL, trophyType == TrophyType.RARE);
    }

    private void runCommands(Player player, RewardEntry reward, String itemDisplay) {
        List<String> commands = reward.getFish().getCommands();
        if (commands == null || commands.isEmpty()) {
            return;
        }

        Map<String, String> ph = placeholders(player, reward, itemDisplay);
        // commands 치환 변수는 색상 코드 없는 plain 값이 더 안전
        ph.put("item", stripColor(itemDisplay));
        me.ninesik.fishing.util.CommandRunner.execute(plugin, player, commands, "Reward", ph);
    }

    private Map<String, String> placeholders(Player player, RewardEntry reward, String itemDisplay) {
        Map<String, String> map = new HashMap<>();
        map.put("player", player.getName());
        map.put("uuid", player.getUniqueId().toString());
        map.put("item", itemDisplay != null ? itemDisplay : "");
        Grade grade = reward.getGrade();
        Grade original = reward.getOriginalGrade();
        map.put("grade", grade != null ? grade.getId().toUpperCase() : "");
        map.put("original_grade", original != null ? original.getId().toUpperCase() : "");
        map.put("double", String.valueOf(reward.isDouble() && reward.getFish().isDoubleEnabled()));
        map.put("big_fish", String.valueOf(reward.isBigFish()));
        // 트로피/레어 트로피 문구 (비트로피면 빈 문자열)
        String trophyText = reward.isRareTrophy() ? rareTrophyLore
                : reward.isTrophy() ? trophyLore : "";
        map.put("trophy", trophyText);
        // 등급 접두사가 붙은 표시명 (예: &a[F] 생대구)
        map.put("graded_item", formatDisplayNameWithGrade(reward.getFish(), itemDisplay));
        // 사이즈 (cm)
        map.put("size", String.format("%.1f", reward.getSize()));
        return map;
    }

    /**
     * 트로피/레어 트로피를 낚았을 때 서버 전체에 공지한다 (피드백).
     * 비트로피면 아무것도 하지 않는다.
     */
    private void announceTrophy(Player player, RewardEntry reward, String itemDisplay) {
        if (!reward.isTrophy() && !reward.isRareTrophy()) {
            return;
        }
        String msg = configManager.formatMessage("catch.trophy-announce", placeholders(player, reward, itemDisplay));
        if (!msg.isEmpty()) {
            Bukkit.broadcastMessage(Texts.colorize(msg));
        }
    }

    private static String stripColor(String input) {
        if (input == null) return "";
        return org.bukkit.ChatColor.stripColor(input);
    }

    /**
     * Lore에 트로피 정보를 추가한다 (사전 판정 결과 사용).
     * 패치예정.md: 트로피 판정은 RollEngine에서 사전 수행되므로, 여기서는 결과만 표시한다.
     */
    private void appendTrophyLore(List<String> lore, boolean isTrophy, boolean isRareTrophy) {
        if (isRareTrophy) {
            lore.add(Texts.colorize(rareTrophyLore));
        } else if (isTrophy) {
            lore.add(Texts.colorize(trophyLore));
        }
    }

    /**
     * 패치예정.md §1246-1257: 아이템에 inmcfishing 네임스페이스의 PDC를 저장한다.
     * fish_id, grade_id, is_trophy, is_rare_trophy는 개별 키로 저장하고,
     * 나머지 상세 정보는 fish_snapshot JSON 하나로 저장한다.
     */
    private void applyPdc(ItemMeta meta, Fish fish, double size, boolean isTrophy, boolean isRareTrophy) {
        NamespacedKey fishIdKey = new NamespacedKey(plugin, "fish_id");
        NamespacedKey gradeIdKey = new NamespacedKey(plugin, "grade_id");
        NamespacedKey trophyKey = new NamespacedKey(plugin, "is_trophy");
        NamespacedKey rareKey = new NamespacedKey(plugin, "is_rare_trophy");
        NamespacedKey sizeKey = new NamespacedKey(plugin, "size");
        NamespacedKey snapshotKey = new NamespacedKey(plugin, "fish_snapshot");

        meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, fish.getId());
        meta.getPersistentDataContainer().set(gradeIdKey, PersistentDataType.STRING, fish.getGrade() != null ? fish.getGrade().getId() : "");
        meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.BYTE, (byte) (isTrophy ? 1 : 0));
        meta.getPersistentDataContainer().set(rareKey, PersistentDataType.BYTE, (byte) (isRareTrophy ? 1 : 0));
        meta.getPersistentDataContainer().set(sizeKey, PersistentDataType.DOUBLE, size);
        meta.getPersistentDataContainer().set(snapshotKey, PersistentDataType.STRING, buildSnapshotJson(fish));
    }

    /**
     * Fish 정보를 JSON 문자열로 직렬화한다 (PDC fish_snapshot 용도).
     */
    private String buildSnapshotJson(Fish fish) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(escapeJson(fish.getId())).append("\"");
        sb.append(",\"useType\":\"").append(escapeJson(fish.getUseType())).append("\"");
        sb.append(",\"minSize\":").append(fish.getMinSize());
        sb.append(",\"maxSize\":").append(fish.getMaxSize());
        sb.append(",\"avgSize\":").append(fish.getAvgSize());
        sb.append(",\"customModelData\":").append(fish.getCustomModelData());
        sb.append(",\"doubleEnabled\":").append(fish.isDoubleEnabled());
        if (fish.getGrade() != null) {
            sb.append(",\"grade\":\"").append(escapeJson(fish.getGrade().getId())).append("\"");
        }
        if (fish.getVanillaName() != null) {
            sb.append(",\"vanillaName\":\"").append(escapeJson(fish.getVanillaName())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}