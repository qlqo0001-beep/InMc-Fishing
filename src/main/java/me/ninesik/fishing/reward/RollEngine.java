package me.ninesik.fishing.reward;

import me.ninesik.fishing.config.ConfigManager;
import me.ninesik.fishing.dependency.DependencyManager;
import me.ninesik.fishing.model.Bait;
import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;
import me.ninesik.fishing.model.Rod;
import me.ninesik.fishing.registry.FishRegistry;
import me.ninesik.fishing.registry.GradeRegistry;
import me.ninesik.fishing.registry.RegistryManager;
import me.ninesik.fishing.service.RewardService;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RollEngine {
    private final RandomService randomService;
    private final GradeRoller gradeRoller;
    private final RewardRoller rewardRoller;
    private final WeightCalculator weightCalculator;
    private final ConfigManager configManager;
    /** Registry 인스턴스를 보관하면 /fishing reload 후 옛 데이터를 계속 보게 된다. */
    private final RegistryManager registryManager;

    /**
     * 트로피 판정은 자체 임계값을 두지 않고 {@link RewardService}에 위임한다.
     *
     * <p>예전에는 여기에 임계값 사본이 있어, 어드민이 collections.yml을 바꾸면
     * "롤 시점 판정은 비트로피인데 아이템 Lore/도감은 트로피"처럼 판정과 표시가 어긋났다.
     * 값을 네 곳에 복제하는 대신 판정 자체를 한 곳에 모았다.</p>
     */
    private final RewardService rewardService;

    public RollEngine(RandomService randomService, RegistryManager registryManager,
                      DependencyManager dependencyManager, ConfigManager configManager,
                      RewardService rewardService) {
        this.randomService = randomService;
        this.configManager = configManager;
        this.registryManager = registryManager;
        this.rewardService = rewardService;
        this.weightCalculator = new WeightCalculator(dependencyManager, configManager);
        this.gradeRoller = new GradeRoller(randomService, weightCalculator, registryManager);
        this.rewardRoller = new RewardRoller(randomService, registryManager, dependencyManager);
    }

    public RollResult roll(Player player, Rod rod, java.util.Set<String> allowedGradeIds, Bait bait) {
        // 1. 등급 롤 (Bait의 등급 보정 적용)
        Grade rolledGrade = gradeRoller.rollGrade(player, rod, allowedGradeIds, bait);
        if (rolledGrade == null) {
            return null;
        }
        
        // 2. 대어 확률 판정 (config + rod 본너스) - 29.3: 대어 판정이 항상 먼저
        boolean isBigFish = false;
        Grade finalGrade = rolledGrade;
        double bigFishChance = configManager.getBigFishChance()
                + (rod != null ? rod.getBigFishChanceBonus() : 0.0);
        if (randomService.nextDouble() * 100 < bigFishChance) {
            // 29.3: S 등급은 승급 없음, 대어 메시지 미출력
            String nextId = rolledGrade.getNextGradeId();
            if (nextId != null) {
                Grade next = registryManager.getGradeRegistry().getById(nextId);
                if (next != null) {
                    isBigFish = true;
                    finalGrade = next;
                }
            }
        }

        // 3. 보상 아이템 롤
        Fish rewardFish = rewardRoller.rollReward(player, finalGrade);
        if (rewardFish == null) {
            return null;
        }

        // 4. 더블 확률 판정 (config + rod 본너스)
        boolean isDouble = false;
        double doubleChance = configManager.getDoubleChance()
                + (rod != null ? rod.getDoubleChanceBonus() : 0.0);
        if (randomService.nextDouble() * 100 < doubleChance) {
            isDouble = true;
        }

        // 5. 물고기 사이즈 산정
        double size = calculateSize(rewardFish);

        // 5-2. 미끼 사이즈 보정 (트로피 판정 전, % → 고정값 순서)
        // 미끼가 커지게 한 사이즈는 어드민이 정한 최대크기를 초과할 수 있다.
        if (bait != null && bait.hasSizeEffect()) {
            if (bait.getSizeBonusPercent() != 0.0) {
                size *= (1.0 + bait.getSizeBonusPercent() / 100.0);
            }
            size += bait.getSizeBonusFixed();
        }

        // 6. 트로피 사전 판정 (패치예정.md: RollEngine이 RewardEntry를 생성하는 시점에 미리 판정)
        // 판정 로직과 임계값을 여기 복제하지 않고 RewardService에 위임한다 — 롤 시점 판정과
        // 아이템 Lore/도감/랭킹 표시가 항상 같은 기준을 쓰게 하기 위함.
        boolean isRareTrophy = rewardService.isRareTrophyBySize(rewardFish, size);
        boolean isTrophy = !isRareTrophy && rewardService.isTrophyBySize(rewardFish, size);

        return new RollResult(rewardFish, finalGrade, rolledGrade, isDouble, isBigFish, size, isTrophy, isRareTrophy);
    }

    /**
     * 가우시안 분포 기반으로 물고기 사이즈를 산정한다.
     * min/max 범위를 벗어나지 않도록 클램프한다.
     * 사이즈가 없는 아이템(쓰레기/광물)은 0.0을 반환한다.
     */
    private double calculateSize(Fish fish) {
        if (!fish.hasSize()) return 0.0;
        double min = fish.getMinSize();
        double max = fish.getMaxSize();
        double avg = fish.getAvgSize();

        // 표준편차: 범위의 1/6 (99.7%가 min~max 안에 들어오도록)
        double stddev = (max - min) / 6.0;
        double size = avg + randomService.nextGaussian() * stddev;
        size = Math.max(min, Math.min(max, size));
        return Math.round(size * 10.0) / 10.0;
    }

    /**
     * 시뮬레이션: n번 롤을 수행하여 등급별/아이템별 통계를 반환한다.
     *
     * <p>Player 없이(=null) 실제 롤과 <b>같은 추첨기</b>를 돌린다. 예전에는 여기에
     * 가중치 추첨이 통째로 재구현돼 있어, 실제 롤 로직이 바뀌어도 시뮬레이션은 옛 방식으로
     * 남는 구조였다(두 곳이 소리 없이 어긋난다).</p>
     *
     * <p>결과는 예전과 동일하다 — {@code WeightCalculator}의 World/Biome/Weather/Time/
     * Permission 보정은 player가 null이면 전부 1.0을 반환하고, Rod/Bait도 null이면
     * 덧셈 보너스가 없다. 즉 이 시뮬레이션은 여전히 "순수 weight 기반 기본 확률"이며,
     * PROGRESS.md에 기록된 기존 결정(Modifier/Rod 보너스 미반영)을 그대로 유지한다.</p>
     *
     * @return 시뮬레이션 결과 맵 (키: 등급ID, 아이템ID, "total", "big_fish", "double")
     */
    public Map<String, Long> simulate(int count) {
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        long total = 0;
        long bigFishCount = 0;
        long doubleCount = 0;

        // count가 최대 1천만이라, 루프 안에서 조회하지 않도록 진입 시 1회만 캡처한다.
        GradeRegistry grades = registryManager.getGradeRegistry();

        // 등급 목록 초기화
        for (Grade g : grades.getAll().values()) {
            gradeCounts.put(g.getId().toUpperCase(), 0L);
        }

        for (int i = 0; i < count; i++) {
            Grade rolledGrade = gradeRoller.rollGrade(null, null, null, null);
            if (rolledGrade == null) continue;

            Grade finalGrade = rolledGrade;
            boolean isBigFish = false;

            // 대어 확률 (29.3)
            if (randomService.nextDouble() * 100 < configManager.getBigFishChance()) {
                String nextId = rolledGrade.getNextGradeId();
                if (nextId != null) {
                    Grade next = grades.getById(nextId);
                    if (next != null) {
                        isBigFish = true;
                        finalGrade = next;
                    }
                }
            }

            Fish selected = rewardRoller.rollReward(null, finalGrade);
            if (selected == null) continue;

            // 더블 확률 (29.3)
            boolean isDouble = randomService.nextDouble() * 100 < configManager.getDoubleChance();

            total++;
            gradeCounts.merge(finalGrade.getId().toUpperCase(), 1L, Long::sum);
            itemCounts.merge(selected.getId(), 1L, Long::sum);
            if (isBigFish) bigFishCount++;
            if (isDouble) doubleCount++;
        }

        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("big_fish", bigFishCount);
        result.put("double", doubleCount);
        for (Map.Entry<String, Long> e : gradeCounts.entrySet()) {
            result.put("grade:" + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : itemCounts.entrySet()) {
            result.put("item:" + e.getKey(), e.getValue());
        }
        return result;
    }

    public static class RollResult {
        private final Fish fish;
        private final Grade grade;
        private final Grade originalGrade;
        private final boolean isDouble;
        private final boolean isBigFish;
        private final double size;
        private final boolean isTrophy;
        private final boolean isRareTrophy;

        public RollResult(Fish fish, Grade grade, Grade originalGrade, boolean isDouble, boolean isBigFish, double size,
                          boolean isTrophy, boolean isRareTrophy) {
            this.fish = fish;
            this.grade = grade;
            this.originalGrade = originalGrade;
            this.isDouble = isDouble;
            this.isBigFish = isBigFish;
            this.size = size;
            this.isTrophy = isTrophy;
            this.isRareTrophy = isRareTrophy;
        }

        public Fish getFish() { return fish; }
        public Grade getGrade() { return grade; }
        public Grade getOriginalGrade() { return originalGrade; }
        public boolean isDouble() { return isDouble; }
        public boolean isBigFish() { return isBigFish; }
        public double getSize() { return size; }
        public boolean isTrophy() { return isTrophy; }
        public boolean isRareTrophy() { return isRareTrophy; }
    }
}
