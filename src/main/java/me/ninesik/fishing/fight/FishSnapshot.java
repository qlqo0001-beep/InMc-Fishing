package me.ninesik.fishing.fight;

import me.ninesik.fishing.model.Fish;
import me.ninesik.fishing.model.Grade;

/**
 * Trophy Fight 시작 시점의 물고기 정보 스냅샷.
 *
 * <p>Fight 진행 중에는 Fish Registry가 리로드될 수 있으므로,
 * Fight 시작 시점의 물고기 정보를 캡처하여 사용한다.</p>
 *
 * <p>Phase 1에서는 기본 정보만 포함하며, 이후 Phase에서
 * 트로피 여부, 사이즈 등의 필드가 추가될 수 있다.</p>
 */
public record FishSnapshot(
        String fishId,
        String fishName,
        String gradeId,
        double avgSize,
        double maxSize,
        boolean hasSize
) {
    /**
     * Fish 객체와 Grade로부터 스냅샷을 생성한다.
     */
    public static FishSnapshot of(Fish fish, Grade grade) {
        return new FishSnapshot(
                fish.getId(),
                fish.getVanillaName() != null ? fish.getVanillaName() : fish.getId(),
                grade.getId(),
                fish.getAvgSize(),
                fish.getMaxSize(),
                fish.hasSize()
        );
    }
}