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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
            ItemStack tempItem = createItemStack(fish, 1, reward.getSize());
            String itemDisplay = resolveDisplayName(fish, tempItem);
            itemDisplay = Texts.colorize(itemDisplay);

            // 대어 메시지
            if (reward.isBigFish()) {
                String bigFishMsg = configManager.formatMessage("big-fish", placeholders(player, reward, itemDisplay));
                if (!bigFishMsg.isEmpty()) {
                    player.sendMessage(bigFishMsg);
                }
                Sounds.play(player, configManager.getSound("big-fish"));
            }

            // 성공/더블 메시지
            boolean effectiveDouble = reward.isDouble() && fish.isDoubleEnabled();
            String catchKey = effectiveDouble ? "caught-double" : "caught";
            String catchMsg = configManager.formatMessage(catchKey, placeholders(player, reward, itemDisplay));
            if (!catchMsg.isEmpty()) {
                player.sendMessage(catchMsg);
            }

            // 사운드
            Sounds.play(player, configManager.getSound("success"));
            if (effectiveDouble) {
                Sounds.play(player, configManager.getSound("double"));
            }

            // Fish.commands 콘솔 실행
            runCommands(player, reward, itemDisplay);

            // 이벤트 발행 — 도감/랭킹/대회 등이 수신
            Bukkit.getPluginManager().callEvent(new FishCatchEvent(player, fish, reward));

            return true;
        }

        ItemStack item = createItemStack(fish, amount, reward.getSize());
        if (item == null) {
            logger.warning("Failed to create reward item for fish id=" + fish.getId()
                    + " use-type=" + fish.getUseType() + " player=" + player.getName());
            String msg = configManager.formatMessage("no-rewards", placeholders(player, reward, ""));
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }
            return false;
        }

        // 인벤토리 여유 확인 (29.9)
        boolean canFit = InventoryUtil.canFit(player.getInventory(), item);
        if (!canFit) {
            if (!configManager.isDropOverflowItems()) {
                String msg = configManager.formatMessage("no-empty-slot");
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
            String msg = configManager.formatMessage("no-empty-slot");
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }
            return false;
        }

        // 표시명 (메시지용, 색상 포함) — 8장 fallback 순서
        String itemDisplay = resolveDisplayName(fish, item);
        itemDisplay = Texts.colorize(itemDisplay);

        // 대어 메시지 (S 등급 승급 없음은 RollEngine에서 isBigFish=false로 처리됨)
        if (reward.isBigFish()) {
            String bigFishMsg = configManager.formatMessage("big-fish", placeholders(player, reward, itemDisplay));
            if (!bigFishMsg.isEmpty()) {
                player.sendMessage(bigFishMsg);
            }
            Sounds.play(player, configManager.getSound("big-fish"));
        }

        // 성공/더블 메시지
        boolean effectiveDouble = reward.isDouble() && fish.isDoubleEnabled();
        String catchKey = effectiveDouble ? "caught-double" : "caught";
        String catchMsg = configManager.formatMessage(catchKey, placeholders(player, reward, itemDisplay));
        if (!catchMsg.isEmpty()) {
            player.sendMessage(catchMsg);
        }

        // 사운드
        Sounds.play(player, configManager.getSound("success"));
        if (effectiveDouble) {
            Sounds.play(player, configManager.getSound("double"));
        }

        // Fish.commands 콘솔 실행 (CLAUDE.md: 결과 로그 남김)
        runCommands(player, reward, itemDisplay);

        // 이벤트 발행 — 도감/랭킹/대회 등이 수신
        Bukkit.getPluginManager().callEvent(new FishCatchEvent(player, fish, reward));

        return true;
    }

    /**
     * 미니게임 실패/타임아웃 시 호출.
     */
    public void handleFail(Player player) {
        if (player == null) {
            return;
        }
        String msg = configManager.formatMessage("fail");
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

    public ItemStack createItemStack(Fish fish, int amount) {
        return createItemStack(fish, amount, 0.0);
    }

    /**
     * 세션 18: 사이즈 정보를 포함하여 아이템 생성.
     * @param size 물고기 사이즈(cm), 사이즈 없는 아이템은 0.0
     */
    public ItemStack createItemStack(Fish fish, int amount, double size) {
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
                // 표시명: [F] 생대구 형식
                String baseName = resolveDisplayName(fish, item);
                meta.setDisplayName(formatDisplayNameWithGrade(fish, baseName));

                // 기존 vanilla-lore + 사이즈 정보 + 트로피 정보 추가
                List<String> lore = new java.util.ArrayList<>();
                if (fish.getVanillaLore() != null && !fish.getVanillaLore().isEmpty()) {
                    lore.addAll(fish.getVanillaLore().stream()
                            .map(Texts::colorize)
                            .toList());
                }
                appendSizeLore(fish, lore, size);
                appendTrophyLore(fish, lore, size);
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                }

                // CustomModelData 적용
                if (fish.getCustomModelData() > 0) {
                    meta.setCustomModelData(fish.getCustomModelData());
                }

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
            // amount 적용 (MMOItems는 보통 1개 반환)
            ItemStack result = base.clone();
            result.setAmount(amount);

            org.bukkit.inventory.meta.ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                // 표시명: [F] 생대구 형식
                String baseName = resolveDisplayName(fish, result);
                meta.setDisplayName(formatDisplayNameWithGrade(fish, baseName));

                // Lore에 {size} 플레이스홀더 + 트로피 정보 적용
                List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
                if (lore == null) lore = new java.util.ArrayList<>();
                if (fish.hasSize()) {
                    lore = lore.stream()
                            .map(line -> line.replace("{size}", String.format("%.1f", size)))
                            .collect(java.util.stream.Collectors.toList());
                }
                appendTrophyLore(fish, lore, size);
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                }

                result.setItemMeta(meta);
            }
            return result;
        }

        logger.warning("Unknown use-type: " + useType + " (fish=" + fish.getId() + ")");
        return null;
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
        return map;
    }

    private static String stripColor(String input) {
        if (input == null) return "";
        return org.bukkit.ChatColor.stripColor(input);
    }

    /**
     * Lore에 사이즈 정보를 추가한다.
     * 사이즈가 없는 아이템(쓰레기/광물)은 추가하지 않는다.
     */
    private void appendSizeLore(Fish fish, List<String> lore, double size) {
        if (!fish.hasSize()) return;
        lore.add("§7사이즈: §f" + String.format("%.1f", size) + "cm");
    }

    /**
     * Lore에 트로피 정보를 추가한다.
     * 사이즈가 없는 아이템은 추가하지 않는다.
     */
    private void appendTrophyLore(Fish fish, List<String> lore, double size) {
        TrophyType trophyType = evaluateTrophyType(fish, size);
        if (trophyType == TrophyType.RARE) {
            lore.add(Texts.colorize(rareTrophyLore));
        } else if (trophyType == TrophyType.NORMAL) {
            lore.add(Texts.colorize(trophyLore));
        }
    }

    /**
     * MMOItems Lore에 {size} 플레이스홀더를 실제 사이즈로 치환한다.
     */
    private ItemStack applySizePlaceholder(ItemStack item, double size) {
        if (item == null) return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.getLore();
        if (lore != null) {
            String sizeStr = String.format("%.1f", size);
            List<String> replaced = lore.stream()
                    .map(line -> line.replace("{size}", sizeStr))
                    .toList();
            meta.setLore(replaced);
            item.setItemMeta(meta);
        }
        return item;
    }
}
