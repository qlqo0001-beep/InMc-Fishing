package me.ninesik.fishing.listener;

import me.ninesik.fishing.fight.FightState;
import me.ninesik.fishing.fight.TrophyFightManager;
import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.fatigue.PlayerFatigueManager;
import me.ninesik.fishing.minigame.FishingMiniGame;
import me.ninesik.fishing.minigame.MiniGameManager;
import me.ninesik.fishing.minigame.MiniGame;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.RewardEntry;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.player.PlayerPreferenceManager;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.RodRegistry;
import me.ninesik.fishing.reward.RollEngine;
import me.ninesik.fishing.reward.RollEngine.RollResult;
import me.ninesik.fishing.service.RewardService;
import me.ninesik.fishing.session.FishingSessionManager;
import me.ninesik.fishing.util.InventoryUtil;
import me.ninesik.fishing.util.Texts;
import io.papermc.paper.event.entity.FishHookStateChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FishingListener implements Listener {
    private final me.ninesik.fishing.InMcFishing plugin;
    private final DependencyManager dependencyManager;
    private final RodRegistry rodRegistry;
    private final FishRegistry fishRegistry;
    private final FishingSessionManager sessionManager;
    private final MiniGameManager miniGameManager;
    private final RollEngine rollEngine;
    private final ConfigManager configManager;
    private final FishingMiniGame fishingMiniGame;
    private final RewardService rewardService;
    private final PlayerPreferenceManager playerPreferenceManager;
    private final PlayerFatigueManager fatigueManager;
    private final TrophyFightManager trophyFightManager;

    /**
     * 29.1: rod.yml에 등록되지 않은 순수 바닐라 FISHING_ROD("이름 없는 기본 낚싯대")로 낚시할 때
     * 사용하는 임시 Rod. 모든 등급 보너스가 0이며, allow-unregistered-vanilla-rod가 true일 때만 쓰인다.
     */
    // 유령 클릭 방지: 같은 틱에 RIGHT_CLICK 후 LEFT_CLICK이 오면 무시
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();
    private final Map<UUID, Integer> lastLeftClickTick = new HashMap<>();

    /** 찌가 물에 들어간 동안 액션바를 갱신하는 반복 태스크 (플레이어별) */
    private final Map<UUID, BukkitTask> castStatusTasks = new HashMap<>();
    /** 찌가 물에 들어간 상태인지 여부 (플레이어별) */
    private final Map<UUID, Boolean> hookInWater = new HashMap<>();

    private static final Rod UNREGISTERED_VANILLA_ROD = Rod.builder()
            .id("__unregistered_vanilla__")
            .useType("vanilla")
            .build();

    public FishingListener(me.ninesik.fishing.InMcFishing plugin,
                           DependencyManager dependencyManager, RodRegistry rodRegistry,
                           FishRegistry fishRegistry,
                           FishingSessionManager sessionManager, MiniGameManager miniGameManager,
                           RollEngine rollEngine, ConfigManager configManager,
                           FishingMiniGame fishingMiniGame, RewardService rewardService,
                           PlayerPreferenceManager playerPreferenceManager,
                           PlayerFatigueManager fatigueManager,
                           TrophyFightManager trophyFightManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.rodRegistry = rodRegistry;
        this.fishRegistry = fishRegistry;
        this.sessionManager = sessionManager;
        this.miniGameManager = miniGameManager;
        this.rollEngine = rollEngine;
        this.configManager = configManager;
        this.fishingMiniGame = fishingMiniGame;
        this.rewardService = rewardService;
        this.playerPreferenceManager = playerPreferenceManager;
        this.fatigueManager = fatigueManager;
        this.trophyFightManager = trophyFightManager;

        // 미니게임(일반/자동 낚시) 종료 후 "찌를 던지고 대기 중" 액션바를 이어서 표시하기 위한 콜백 등록
        // Trophy Fight 진행 중에는 캐스트 상태를 재개하지 않는다 (Fight 종료 시 TrophyFightManager가 처리)
        this.fishingMiniGame.setGameEndListener((player, result, wasAutoCatch) -> {
            if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
                return;
            }
            if (result == MiniGame.GameResult.CANCEL) {
                // 퇴장/사망/월드이동 등으로 인한 취소는 cleanupPlayer()가 이미 정리함
                return;
            }
            resumeCastStatusIfWaiting(player);
        });
    }

    /**
     * 4.4절: 플레이어 퇴장·월드이동·사망·아이템드롭·핫바변경 시 미니게임/세션 정리.
     * PlayerDropItemEvent만 드롭 자체를 방지하지 않고 정리한다 (4.4 원문 취지).
     */
    private void cleanupPlayer(Player player) {
        stopCastStatus(player);
        if (miniGameManager.hasActiveGame(player)) {
            MiniGame game = miniGameManager.getGame(player);
            if (game != null) {
                game.stop(player, MiniGame.GameResult.CANCEL);
            }
        }
        // Trophy Fight 강제 종료 (퇴장/월드이동/사망/아이템드롭/핫바변경)
        if (trophyFightManager != null) {
            trophyFightManager.stopFight(player, FightState.CANCELLED);
        }
        sessionManager.removeSession(player);
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        switch (event.getState()) {
            case FISHING -> {
                // 찌를 던진 직후 — 아직 BOBBING 상태가 아닐 수 있음.
                // ActionBar는 FishHookStateChangeEvent에서 HookState가 BOBBING으로
                // 변경될 때 시작한다 (여기서는 아무 작업도 하지 않음).
                return;
            }
            case BITE -> {
                // 입질 — 미니게임 UI(타임바/타이틀)와 겹치지 않도록 반복 갱신만 잠시 멈춘다.
                // hookInWater(찌가 아직 물에 떠 있다는 상태)는 유지하여, 미니게임 종료 후
                // resumeCastStatusIfWaiting()에서 다시 이어서 표시할 수 있게 한다.
                pauseCastStatus(player);
                // 아래 BITE 처리 계속
            }
            default -> {
                // FISHING/BITE를 제외한 모든 상태는 "더 이상 입질을 받을 수 없는 상태"로 간주하고
                // 무조건 정지시킨다 (화이트리스트 방식).
                //   - IN_GROUND: 찌가 땅에 박혀 입질 자체가 불가능한 상태
                //   - FAILED_ATTEMPT: 아무것도 낚지 못하고 회수(reel-in)한 경우 — 이전에는 이 값이
                //     처리되지 않아 hookInWater가 지워지지 않고, 반복 태스크가 계속 액션바를 다시
                //     그려서 "회수해도 액션바가 안 사라지는" 버그의 원인이었다.
                //   - REEL_IN, CAUGHT_FISH, CAUGHT_ENTITY: 정상 회수/포획
                // 이렇게 화이트리스트(FISHING/BITE)만 남기고 나머지를 전부 정지 대상으로 두면,
                // Bukkit이 향후 상태를 추가하더라도 "안 사라지는 액션바" 버그가 재발하지 않는다.
                stopCastStatus(player);
                return;
            }
        }

        // BITE 상태 처리
        if (event.getState() != PlayerFishEvent.State.BITE) {
            return;
        }

        if (!configManager.isEnabled()) {
            return;
        }

        if (configManager.isRequireEmptySlot()
                && !InventoryUtil.hasEmptySlot(player.getInventory())) {
            event.setCancelled(true);
            return;
        }

        // 허용된 월드인지 확인
        if (!isAllowedWorld(player)) {
            return;
        }

        // Trophy Fight 중에는 새 입질/미니게임 시작 방지
        if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
            event.setCancelled(true);
            return;
        }

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

        boolean minigameEnabled = playerPreferenceManager.isMinigameEnabled(player);
        RollResult result = minigameEnabled
                ? rollEngine.roll(player, rod)
                : rollEngine.roll(player, rod, configManager.getMinigameOffAllowedGrades());
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
                .size(result.getSize())
                .isTrophy(result.isTrophy())
                .isRareTrophy(result.isRareTrophy())
                .build();
        if (minigameEnabled) {
            fishingMiniGame.start(player, result.getGrade(), reward);
        } else {
            fishingMiniGame.startAutoCatch(
                    player,
                    result.getGrade(),
                    reward,
                    configManager.getAutoCatchDelaySeconds(result.getGrade().getId())
            );
        }
    }

    /**
     * 찌가 물에 완전히 들어간 상태에서 액션바 표시를 시작한다.
     * config.yml의 cast-status.refresh-interval-ticks 주기로 반복 갱신한다.
     */
    private void startCastStatus(Player player) {
        UUID uuid = player.getUniqueId();
        if (hookInWater.put(uuid, true) != null) {
            // 이미 표시 중이면 갱신만
            showCastStatus(player);
            return;
        }

        showCastStatus(player);

        int intervalTicks = configManager.getCastStatusRefreshIntervalTicks();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!hookInWater.containsKey(uuid)) {
                cancelCastStatusTask(uuid);
                return;
            }
            showCastStatus(player);
        }, intervalTicks, intervalTicks);
        castStatusTasks.put(uuid, task);
    }

    /**
     * 액션바 표시를 중지하고 태스크를 취소한다. 찌가 물에서 완전히 벗어난 경우(회수/획득/취소)에만
     * 호출한다. hookInWater 자체를 제거하므로, 이후 재개(resume) 대상에서도 제외된다.
     */
    private void stopCastStatus(Player player) {
        UUID uuid = player.getUniqueId();
        hookInWater.remove(uuid);
        cancelCastStatusTask(uuid);
    }

    /**
     * 입질(BITE)로 미니게임이 시작될 때 반복 갱신 태스크만 멈춘다. hookInWater는 유지해서,
     * 찌가 아직 물에 떠 있다는 사실(=미니게임 종료 후 다시 대기 액션바를 보여줘야 한다는 사실)을
     * 기억해 둔다.
     */
    private void pauseCastStatus(Player player) {
        cancelCastStatusTask(player.getUniqueId());
    }

    /**
     * 미니게임(일반/자동 낚시 모두)이 성공·실패·타임아웃으로 끝났을 때 호출된다.
     * 퇴장/사망/월드이동 등으로 인한 CANCEL은 이미 cleanupPlayer()가 정리했으므로 대상에서 제외한다.
     * BITE 시점에 hookInWater를 지우지 않았기 때문에, 여전히 찌가 물에 떠 있는 상태(=플레이어가
     * 그 사이 로그아웃/월드이동 등으로 정리되지 않은 상태)라면 대기 액션바를 다시 시작한다.
     */
    public void resumeCastStatusIfWaiting(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!Boolean.TRUE.equals(hookInWater.get(uuid))) {
            return;
        }
        if (castStatusTasks.containsKey(uuid)) {
            return; // 이미 실행 중 (중복 방지)
        }
        // 미니게임 종료 후 찌가 여전히 BOBBING 상태인지 확인
        FishHook hook = player.getFishHook();
        if (hook == null || hook.getState() != FishHook.HookState.BOBBING) {
            // 찌가 더 이상 물에 떠 있지 않으면 대기 상태 해제
            hookInWater.remove(uuid);
            return;
        }
        showCastStatus(player);
        int intervalTicks = configManager.getCastStatusRefreshIntervalTicks();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!hookInWater.containsKey(uuid)) {
                cancelCastStatusTask(uuid);
                return;
            }
            showCastStatus(player);
        }, intervalTicks, intervalTicks);
        castStatusTasks.put(uuid, task);
    }

    private void cancelCastStatusTask(UUID uuid) {
        BukkitTask task = castStatusTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void showCastStatus(Player player) {
        if (!configManager.isCastStatusEnabled()) {
            return;
        }

        if (!configManager.isEnabled()) {
            sendCastStatus(player, "plugin-disabled", Map.of());
            return;
        }
        if (!isAllowedWorld(player)) {
            sendCastStatus(player, "wrong-world", Map.of());
            return;
        }
        if (configManager.isRequireEmptySlot()
                && !InventoryUtil.hasEmptySlot(player.getInventory())) {
            sendCastStatus(player, "no-empty-slot", Map.of());
            return;
        }

        RodLookupResult lookup = lookupRod(player);
        String rodName;
        if (lookup instanceof RodLookupResult.Matched matched) {
            Rod rod = matched.rod();
            rodName = rod.getVanillaName() != null && !rod.getVanillaName().isBlank()
                    ? rod.getVanillaName()
                    : rod.getId();
        } else if (lookup instanceof RodLookupResult.UnregisteredVanilla
                && configManager.isAllowUnregisteredVanillaRod()) {
            rodName = "Fishing Rod";
        } else {
            sendCastStatus(player, "unregistered-rod", Map.of());
            return;
        }

        String ready = Texts.apply(configManager.getCastStatusMessage("ready"), Map.of("rod", rodName));
        if (!playerPreferenceManager.isMinigameEnabled(player)) {
            // 미니게임 OFF 상태 — 피로도 표시
            String autoCatch = configManager.getCastStatusMessage("minigame-off");
            if (autoCatch != null && !autoCatch.isBlank()) {
                String fatigueStr = fatigueManager != null
                        ? String.valueOf(fatigueManager.getFatigue(player))
                        : "?";
                ready = ready + " &8| " + Texts.apply(autoCatch, Map.of("fatigue", fatigueStr));
            }
        }
        Texts.sendActionBar(player, ready);
    }

    private void sendCastStatus(Player player, String key, Map<String, String> placeholders) {
        Texts.sendActionBar(player, Texts.apply(configManager.getCastStatusMessage(key), placeholders));
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
        Action action = event.getAction();

        // 피로도 회복 물약 사용 처리 (우클릭 시)
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && fatigueManager != null) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held != null && held.getType() != Material.AIR) {
                Fish potionFish = findFatiguePotion(held);
                if (potionFish != null) {
                    // 물약 사용 — 피로도 회복
                    int recovery = potionFish.getFatigueRecovery();
                    fatigueManager.recover(player, recovery);
                    int result = fatigueManager.getFatigue(player);
                    // 아이템 1개 소모
                    int amount = held.getAmount();
                    if (amount > 1) {
                        held.setAmount(amount - 1);
                    } else {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    }
                    player.sendMessage("§a피로도가 " + recovery + "만큼 회복되었습니다. (현재: " + result + ")");
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!miniGameManager.hasActiveGame(player)) {
            // Trophy Fight 중 L/R 클릭은 릴 조작(Reeling 토글)으로 처리
            if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
                java.util.Optional<me.ninesik.fishing.fight.FightSession> session =
                        trophyFightManager.getSession(player.getUniqueId());
                if (session.isPresent()) {
                    me.ninesik.fishing.fight.FightSession fs = session.get();
                    int currentTick = player.getTicksLived();
                    UUID uuid = player.getUniqueId();

                    // 유령 클릭 방지 (일반 미니게임과 동일한 처리 — 패치예정.md 피드백:
                    // 우클릭 시 같은 틱에 유령 LEFT_CLICK_AIR가 발생해 릴 감기가 함께
                    // 등록되던 버그. 이 필터가 없으면 우클릭도 릴을 당기는 것처럼 보인다.)
                    if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                        lastRightClickTick.put(uuid, currentTick);
                        // 우클릭 = 릴 풀기 (피드백): Distance 증가 + Tension 감소 + Reel State 회복.
                        // 안 눌러도 연타를 멈추면 유예시간 후 자동으로 풀기 상태가 꺼진다.
                        fs.registerReleaseClick();
                    } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                        Integer lastRight = lastRightClickTick.get(uuid);
                        Integer lastLeft = lastLeftClickTick.get(uuid);
                        boolean ghost = (lastRight != null && lastRight == currentTick)
                                || (lastLeft != null && lastLeft == currentTick);
                        lastLeftClickTick.put(uuid, currentTick);
                        if (!ghost) {
                            fs.registerReelClick();
                        }
                    }
                }
                event.setCancelled(true);
                return;
            }
            return;
        }

        MiniGame game = miniGameManager.getGame(player);
        if (game == null || !game.isActive(player)) {
            return;
        }

        int currentTick = player.getTicksLived();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // 우클릭: 현재 틱 기록 후 정상 처리
            lastRightClickTick.put(player.getUniqueId(), currentTick);
            game.handleInput(player, MiniGame.InputType.RIGHT_CLICK);
            event.setCancelled(true);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // 좌클릭: 같은 틱에 우클릭이 있었다면 유령 클릭으로 무시
            event.setCancelled(true);
            Integer lastTick = lastRightClickTick.get(player.getUniqueId());
            Integer lastLeftTick = lastLeftClickTick.get(player.getUniqueId());
            if ((lastTick != null && lastTick == currentTick)
                    || (lastLeftTick != null && lastLeftTick == currentTick)) {
                return; // 유령 좌클릭 무시
            }
            lastLeftClickTick.put(player.getUniqueId(), currentTick);
            game.handleInput(player, MiniGame.InputType.LEFT_CLICK);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanupPlayer(player);
        lastRightClickTick.remove(player.getUniqueId());
        lastLeftClickTick.remove(player.getUniqueId());
        hookInWater.remove(player.getUniqueId());
        cancelCastStatusTask(player.getUniqueId());
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

    /**
     * 손에 든 아이템이 피로도 회복 물약인지 확인한다.
     * items/*.yml에서 fatigue-recovery가 0보다 큰 물고기(물약)를 찾는다.
     */
    private Fish findFatiguePotion(ItemStack item) {
        if (item == null || fishRegistry == null) return null;
        org.bukkit.inventory.meta.ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
        if (displayName == null) return null;

        String stripped = ChatColor.stripColor(displayName);
        for (Fish fish : fishRegistry.getAll().values()) {
            if (!fish.isFatiguePotion()) continue;
            // 물약 아이템의 displayName과 매칭 (등급 접두사 제거 후 비교)
            String fishName = rewardService.resolveDisplayName(fish, null);
            String fishStripped = ChatColor.stripColor(fishName);
            if (stripped != null && fishStripped != null && stripped.contains(fishStripped)) {
                return fish;
            }
        }
        return null;
    }

    @EventHandler
    public void onFishHookStateChange(FishHookStateChangeEvent event) {
        FishHook hook = event.getEntity();
        if (!(hook.getShooter() instanceof Player player)) {
            return;
        }

        FishHook.HookState newState = event.getNewHookState();
        if (newState == FishHook.HookState.BOBBING) {
            // 미니게임 진행 중이면 ActionBar를 다시 시작하지 않음
            // (미니게임 종료 후 resumeCastStatusIfWaiting에서만 재개)
            if (miniGameManager.hasActiveGame(player)) {
                return;
            }
            // Trophy Fight 진행 중이면 ActionBar를 시작하지 않음
            if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
                return;
            }
            // 중복 시작 방지 — startCastStatus 내부에서도 hookInWater로 검사
            startCastStatus(player);
        } else if (newState == FishHook.HookState.UNHOOKED
                || newState == FishHook.HookState.HOOKED_ENTITY) {
            // 명시적으로 종료해야 하는 상태만 ActionBar 제거
            // (향후 새 HookState가 추가되어도 의도치 않게 종료되지 않도록 화이트리스트 방식)
            stopCastStatus(player);
        }
        // 기타 상태는 무시
    }

    private boolean isAllowedWorld(Player player) {
        List<String> worlds = configManager.getAllowedWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }
        return worlds.contains(player.getWorld().getName());
    }

    /**
     * Trophy Fight에서 사용할 낚싯대 스탯을 조회한다.
     * rod.yml에 등록된 낚싯대이면 해당 Rod을 반환하고,
     * 그 외(미등록 바닐라/미등록 MMOItems/미낚싯대)에는 null을 반환한다.
     * (null이면 TrophyFightManager가 기본값으로 처리)
     */
    public Rod getRodForFight(Player player) {
        RodLookupResult result = lookupRod(player);
        if (result instanceof RodLookupResult.Matched matched) {
            return matched.rod();
        }
        return null;
    }

    public TrophyFightManager getTrophyFightManager() {
        return trophyFightManager;
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