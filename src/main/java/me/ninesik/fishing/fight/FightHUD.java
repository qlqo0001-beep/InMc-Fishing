package me.ninesik.fishing.fight;

import me.ninesik.fishing.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trophy Fight HUD — BossBar + ActionBar 표시.
 *
 * <p>패치예정.md:</p>
 * <ul>
 *   <li>BossBar 게이지(색/길이) = Tension 기준, 네임 텍스트 = Distance 숫자</li>
 *   <li>ActionBar = Fish Stamina/Power/Resistance/Reel State 표시</li>
 * </ul>
 *
 * <p>색상/포맷은 FightConfig(hud 설정)에서 조정할 수 있다.</p>
 *
 * <p><b>클라이언트 전용 연출을 추가할 때 (ProtocolLib 대신 읽을 것):</b>
 * 예전에는 "그 플레이어에게만 보이는" 효과를 내려면 ProtocolLib으로 패킷을
 * 직접 만들어야 했다. 지금 타깃인 Paper API에는 같은 일을 하는 공개 메서드가
 * 이미 있으므로 <b>외부 패킷 라이브러리를 다시 들이지 않는다.</b>
 * (그래서 {@code ProtocolLibHook}은 삭제했다 — 스텁이라 실제로 하는 일이 없으면서
 * 콘솔에는 "hooked successfully"를 찍어 어드민을 오도하고 있었다.)</p>
 *
 * <p>파이트 연출에 바로 쓸 수 있는 것들 — 전부 해당 플레이어에게만 전송되고
 * 서버 상태를 바꾸지 않는다(월드에 실제 변화가 없어 롤백 걱정이 없다):</p>
 * <ul>
 *   <li>{@code player.sendHurtAnimation(yaw)} — 물고기가 강하게 저항할 때 피격 흔들림</li>
 *   <li>{@code player.sendExperienceChange(progress, level)} — 경험치 바를 파이트
 *       게이지로 전용. 실제 경험치는 그대로다</li>
 *   <li>{@code player.sendEquipmentChange(entity, slot, item)} — 파이트 중에만
 *       다른 낚싯대를 든 것처럼 보이게 함</li>
 *   <li>{@code player.sendBlockChange(loc, blockData)} / {@code sendMultiBlockChange} —
 *       수면 물결 등 지형 연출</li>
 *   <li>{@code player.hideEntity(plugin, e)} / {@code showEntity(plugin, e)} —
 *       특정 플레이어에게만 보이는 연출용 엔티티</li>
 *   <li>{@code player.sendPotionEffectChange(...)} — 실제로 효과를 걸지 않고
 *       화면 오버레이만</li>
 * </ul>
 *
 * <p>위 목록으로 안 되는 경우에만 패킷 라이브러리를 검토한다. 그때도
 * 최신 Paper API에 대응 메서드가 생겼는지 먼저 확인할 것.</p>
 */
public class FightHUD {

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    /**
     * placeholder 조립용 재사용 맵.
     *
     * <p>HUD 3종(보스바/액션바/타이틀)이 세션마다 매 틱 불리므로, 그때마다 HashMap을
     * 새로 만들면 세션당 초당 60개가 쌓인다. HUD 갱신은 전부 메인 스레드에서
     * 순차 실행되고 값을 담자마자 {@code Texts.apply}로 소비하므로 재사용해도 안전하다.</p>
     */
    private final Map<String, String> placeholderScratch = new HashMap<>();

    private Map<String, String> scratch() {
        placeholderScratch.clear();
        return placeholderScratch;
    }

    /**
     * {@code String.format("%.0f", v)} 대체. HUD는 매 틱 6개씩 포맷하는데 String.format은
     * 매번 포맷 파서를 태운다. 표시 대상(체력/거리/릴 등)은 모두 음수가 아니라 결과가 같다.
     */
    private static String round0(double value) {
        return Long.toString(Math.round(value));
    }

    /** {@code String.format("%.1f", v)} 대체. */
    private static String round1(double value) {
        long scaled = Math.round(value * 10.0);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    /**
     * Fight 시작 시 BossBar를 생성하고 표시한다.
     */
    public void showBossBar(Player player, FightSession session, FightConfig.HudConfig hudConfig) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.get(uuid);
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            bossBars.put(uuid, bar);
        }
        bar.addPlayer(player);
        updateBossBar(player, session, 0.0, hudConfig);
    }

    /**
     * BossBar를 갱신한다. maxDistance가 0보다 크면 타이틀에 "Distance: X / MAX" 형태로
     * 표시해, Distance가 얼마나 벌어지면 줄이 끊어지는지(패치예정.md 피드백 반영) 보여준다.
     *
     * <p><b>버그 수정 (피드백: "config의 bossbar-title-format/bar-color 값을 못 불러오는 것
     * 같다"):</b> 이전 구현은 {@code config.yml}의 {@code trophy-fight.hud.*} 설정을 전혀
     * 참조하지 않고 타이틀 텍스트("&fDistance: &e...")와 게이지 색을 자바 코드에 하드코딩하고
     * 있었다 — {@link FightConfig.HudConfig}가 로드는 되지만 어디서도 소비되지 않는 죽은 코드였다.
     * 이제 {@code hudConfig.bossbarTitleFormat}의 {@code {distance}}/{@code {max_distance}}
     * 플레이스홀더를 실제 값으로 치환하고, {@code bar-color-safe/warning/danger} 설정을
     * Tension 위험도에 따라 타이틀 색상 접두사로 적용한다.</p>
     */
    public void updateBossBar(Player player, FightSession session, double maxDistance, FightConfig.HudConfig hudConfig) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.get(uuid);
        if (bar == null) {
            return;
        }

        // Tension 비율 (0.0 ~ 1.0) — Line Strength(줄 강도) 기준
        // 지역 변수는 줄 강도 상한을 담고 있으므로 maxTension → maxLineStrength로 명명해
        // 의미(줄이 끊어지는 장력 상한 = 줄 강도)를 코드에 명확히 드러낸다 (피드백.md 연관 정리).
        double maxLineStrength = Math.max(1.0, session.getLineStrength());
        double tensionRatio = Math.min(1.0, session.getTension() / maxLineStrength);
        bar.setProgress(tensionRatio);

        // Tension 위험도에 따른 게이지 색 + 타이틀 색상 접두사 (config 값 사용)
        String colorPrefix;
        if (tensionRatio >= hudConfig.tensionDangerRatio) {
            bar.setColor(BarColor.RED);
            colorPrefix = hudConfig.barColorDanger;
        } else if (tensionRatio >= hudConfig.tensionWarningRatio) {
            bar.setColor(BarColor.YELLOW);
            colorPrefix = hudConfig.barColorWarning;
        } else {
            bar.setColor(BarColor.GREEN);
            colorPrefix = hudConfig.barColorSafe;
        }

        // 네임 텍스트 = config.yml의 bossbar-title-format ({distance}/{max_distance} 치환)
        Map<String, String> placeholders = scratch();
        placeholders.put("distance", round0(session.getDistance()));
        placeholders.put("max_distance", maxDistance > 0 ? round0(maxDistance) : "-");
        String title = Texts.apply(hudConfig.bossBarTitleFormat, placeholders);
        bar.setTitle(Texts.colorize(colorPrefix + title));
    }

    /**
     * ActionBar를 갱신한다.
     * config.yml의 actionbar-format ({stamina}/{power}/{resistance}/{reel} 치환)을 사용한다.
     *
     * <p><b>버그 수정:</b> 이전에는 config의 {@code actionbar-format}을 무시하고
     * "&bStamina &f..." 형태로 자바 코드에 하드코딩된 문자열만 표시했다.</p>
     */
    public void updateActionBar(Player player, FightSession session, FightConfig.HudConfig hudConfig) {
        double maxDistance = session.getMaxDistance();
        Map<String, String> placeholders = scratch();
        placeholders.put("stamina", round0(session.getStamina()));
        placeholders.put("power", round0(session.getPower()));
        placeholders.put("resistance", round0(session.getResistance()));
        placeholders.put("reel", round0(session.getReelState()));
        placeholders.put("distance", round0(session.getDistance()));
        placeholders.put("max_distance", maxDistance > 0 ? round0(maxDistance) : "-");
        String message = Texts.apply(hudConfig.actionBarFormat, placeholders);
        // 행동력(AP)은 지금까지 어디에도 표시되지 않아 "보이지 않는 난이도"로만 작동했다.
        // 서버 성격에 따라 노출할 수 있도록 키를 두되, 기존 플레이어 경험이 바뀌지
        // 않도록 기본값은 OFF다.
        if (hudConfig.showActionPower) {
            FishAI ai = session.getFishAI();
            placeholders.clear();
            placeholders.put("action_power", String.valueOf(ai.getCurrentActionPower()));
            placeholders.put("max_action_power", String.valueOf(ai.getMaxActionPower()));
            message += Texts.apply(hudConfig.actionPowerFormat, placeholders);
        }
        Texts.sendActionBar(player, Texts.colorize(message));
    }

    /**
     * Fish AI 상태를 타이틀로 표시한다. 상태가 유지되는 동안 계속 보이도록
     * 매 틱(TrophyFightManager.tick()) 호출해서 갱신해야 한다.
     *
     * <p>패치예정.md 피드백: "타이틀이 상태가 변할 때까지 유지되어야 해. 상태 유지가
     * 길면 타이틀이 사라지는 문제가 있어." 이전에는 상태가 바뀌는 시점에 한 번만
     * {@code player.sendTitle()}을 호출했는데, Minecraft 타이틀은 fadeIn/stay/fadeOut이
     * 지나면 자동으로 사라지기 때문에 상태 지속시간(REST는 최대 4초 등)이 타이틀
     * 표시시간(총 2초)보다 길면 상태가 안 바뀌었는데도 타이틀이 먼저 꺼져버렸다.
     * 이제 매 틱 새로 타이틀 패킷을 보내 fadeOut이 시작되기 전에 계속 갱신되므로,
     * 상태가 유지되는 동안 타이틀도 계속 떠 있는다.</p>
     *
     * <p>서브타이틀에는 다음 상태 전이까지 남은 시간을 초 단위로 표시해,
     * 줄어드는 카운트다운처럼 보이게 한다. 그 양옆에는 Fish Stamina와 Reel State를
     * 배치해 한눈에 들어오게 한다 (피드백: "물고기 체력과 릴 상태를 서브 타이틀
     * 초후변화 양옆에 1개씩 배치하자. 눈에 잘보이게"). Minecraft 타이틀은 title/subtitle
     * 각 1줄만 지원하고 "두 번째 서브타이틀" 같은 건 없어서, 세 정보를 서브타이틀
     * 한 줄 안에 구분자(┃)로 나란히 배치했다.</p>
     *
     * <p><b>피드백: "해당 부분을 콘피그에서 수정되게 해줘야 해."</b> 타이틀/서브타이틀
     * 포맷과 상태별 색상을 자바 코드에 하드코딩하지 않고 {@code config.yml}의
     * {@code trophy-fight.hud.state-title-format} / {@code state-subtitle-format} /
     * {@code state-color.*}에서 읽어와 서버 운영자가 자유롭게 바꿀 수 있게 했다.</p>
     *
     * @param remainingTicks 현재 상태가 끝나기까지 남은 틱 수 ({@link FishAI#getRemainingTicks()})
     * @param stamina 현재 Fish Stamina (0 ~ maxStamina)
     * @param reelState 현재 Reel State (0 ~ maxReelState)
     * @param hudConfig BossBar/ActionBar와 동일하게 config.yml의 HUD 설정을 사용
     */
    public void updateStateTitle(Player player, FishState state, int remainingTicks,
                                  double stamina, double reelState, FightConfig.HudConfig hudConfig) {
        String stateKey = state.name().toLowerCase();
        String stateColor = hudConfig.stateColors.getOrDefault(stateKey, "&f");

        // 상태별 권장 행동 가이드(좌/우클릭 힌트) — config의 state-guide.<state> 또는 하드코딩 기본값.
        // 가이드가 없으면 기존 generic 포맷(state-*-format)으로 폴백한다.
        FightConfig.HudConfig.StateGuide guide = hudConfig.getStateGuide(state);
        String title = guide != null ? guide.title() : hudConfig.stateTitleFormat;
        String subtitle = guide != null ? guide.subtitle() : hudConfig.stateSubtitleFormat;

        double remainingSeconds = Math.max(0, remainingTicks) / 20.0;
        Map<String, String> placeholders = scratch();
        placeholders.put("state_color", stateColor);
        placeholders.put("state", hudConfig.stateName(state));
        placeholders.put("stamina", round0(stamina));
        placeholders.put("reel", round0(reelState));
        placeholders.put("remaining_seconds", round1(remainingSeconds));

        player.sendTitle(
                Texts.colorize(Texts.apply(title, placeholders)),
                Texts.colorize(Texts.apply(subtitle, placeholders)),
                0, 4, 3
        );
    }

    /**
     * Fight 종료 시 BossBar를 제거한다.
     */
    public void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * 모든 BossBar를 정리한다. (플러그인 종료 시 호출)
     */
    public void cleanup() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }
}