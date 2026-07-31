package me.ninesik.fishing.listener;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.minigame.MiniGameManager;
import me.ninesik.fishing.minigame.MiniGame;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.reward.RollEngine.RollResult;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.session.FishingSessionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FishingListener implements Listener {
    private final DependencyManager dependencyManager;
    private final RodRegistry rodRegistry;
    private final FishingSessionManager sessionManager;
    private final MiniGameManager miniGameManager;
    private final RollEngine rollEngine;
    private final ConfigManager configManager;
    private final FishingMiniGame fishingMiniGame;
    private final RewardService rewardService;

    /**
     * 29.1: rod.yml에 등록되지 않은 순수 바닐라 FISHING_ROD("이름 없는 기본 낚싯대")로 낚시할 때
     * 사용하는 임시 Rod. 모든 등급 보너스가 0이며, allow-unregistered-vanilla-rod가 true일 때만 쓰인다.
     */
    // 유령 클릭 방지: 같은 틱에 RIGHT_CLICK 후 LEFT_CLICK이 오면 무시
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();

    private static final Rod UNREGISTERED_VANILLA_ROD = Rod.builder()
            .id("__unregistered_vanilla__")
            .useType("vanilla")
            .build();

    public FishingListener(DependencyManager dependencyManager, RodRegistry rodRegistry,
                           FishingSessionManager sessionManager, MiniGameManager miniGameManager,
                           RollEngine rollEngine, ConfigManager configManager,
                           FishingMiniGame fishingMiniGame, RewardService rewardService) {
        this.dependencyManager = dependencyManager;
        this.rodRegistry = rodRegistry;
        this.sessionManager = sessionManager;
        this.miniGameManager = miniGameManager;
        this.rollEngine = rollEngine;
        this.configManager = configManager;
        this.fishingMiniGame = fishingMiniGame;
        this.rewardService = rewardService;
    }

    /**
     * 4.4절: 플레이어 퇴장·월드이동·사망·아이템드롭·핫바변경 시 미니게임/세션 정리.
     * PlayerDropItemEvent만 드롭 자체를 방지하지 않고 정리한다 (4.4 원문 취지).
     */
    private void cleanupPlayer(Player player) {
        if (miniGameManager.hasActiveGame(player)) {
            MiniGame game = miniGameManager.getGame(player);
            if (game != null) {
                game.stop(player, MiniGame.GameResult.CANCEL);
            }
        }
        sessionManager.removeSession(player);
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.BITE) {
            return;
        }

        Player player = event.getPlayer();

        // 이미 미니게임 중인지 확인
        if (miniGameManager.hasActiveGame(player)) {
            event.setCancelled(true);
            return;
        }

        // 낚싯대 확인 (29.1)
        RodLookupResult lookup = lookupRod(player);
        Rod rod;
        if (lookup instanceof RodLookupResult.Matched matched) {
            rod = matched.rod();
        } else if (lookup instanceof RodLookupResult.UnregisteredVanilla) {
            if (!configManager.isAllowUnregisteredVanillaRod()) {
                blockFishing(event, player);
                return;
            }
            // 29.1: rod.yml에 없어도 낚시는 정상 진행, 모든 등급 보너스 0
            rod = UNREGISTERED_VANILLA_ROD;
        } else {
            // UnregisteredMmoItemRod (MMOItems ROD인데 rod.yml에 미등록 — 우회 악용 방지) 또는 NotARod
            blockFishing(event, player);
            return;
        }

        // 롤링
        RollResult result = rollEngine.roll(player, rod);
        if (result == null || result.getFish() == null) {
            event.setCancelled(true);
            return;
        }

        // 입질 취소 (자동 낚시 방지)
        event.setCancelled(true);

        // 미니게임 시작
        RewardEntry reward = RewardEntry.builder()
                .fish(result.getFish())
                .grade(result.getGrade())
                .isDouble(result.isDouble())
                .isBigFish(result.isBigFish())
                .originalGrade(result.getOriginalGrade())
                .build();
        fishingMiniGame.start(player, result.getGrade(), reward);
    }

    private void blockFishing(PlayerFishEvent event, Player player) {
        event.setCancelled(true);
        String message = configManager.getMessage("unregistered-rod");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!miniGameManager.hasActiveGame(player)) {
            return;
        }

        MiniGame game = miniGameManager.getGame(player);
        if (game == null || !game.isActive(player)) {
            return;
        }

        int currentTick = player.getTicksLived();

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