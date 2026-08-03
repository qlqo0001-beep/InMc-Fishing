package me.ninesik.fishing.fight;

/**
 * Trophy Fight가 실패(FAILED)로 끝났을 때, 왜 실패했는지 원인을 구분한다.
 *
 * <p>피드백: "파이트시, 물고기가 도망가는 원인을 나눴으면 좋겠어. 일반적인
 * 미니게임은 그냥 물고기가 도망갔네..., 파이트시, 탠션 최대 - 줄이 끊겼네..,
 * 릴 파손 - 릴이 고장났네.., 거리 - 줄 길이가 부족하네.. 등으로 가능하게 해야 함."</p>
 *
 * <p>일반(L/R 클릭) 미니게임 실패는 이 enum과 무관하게 기존 {@code messages.fail}
 * 메시지를 그대로 사용한다 — 이 enum은 오직 Trophy Fight({@link TrophyFightManager})
 * 내부의 실패 원인만 구분한다.</p>
 */
public enum FightFailReason {
    /**
     * 원인 미지정. 아직 실패하지 않았거나(SUCCESS/진행 중), CANCELLED처럼 게임
     * 규칙상의 실패가 아닌 경우 기본값으로 유지된다 — 이 경우 기존 {@code messages.fail}
     * 메시지가 fallback으로 쓰인다.
     */
    NONE,
    /** Tension이 Line Strength에 도달 — 줄이 끊어짐. {@code messages.fail-tension} */
    LINE_SNAPPED,
    /** Reel State가 0에 도달 — 릴이 고장남. {@code messages.fail-reel-break} */
    REEL_BROKEN,
    /** Distance가 Max Distance에 도달 — 줄 길이가 부족함. {@code messages.fail-distance} */
    DISTANCE_EXCEEDED,
    /** 제한 시간 초과. {@code messages.fail-timeout} */
    TIMEOUT;

    /**
     * 이 원인에 대응하는 {@code messages.<key>} 설정 키를 반환한다.
     * {@link #NONE}은 기존 일반 실패 메시지 키("fail")를 반환한다.
     */
    public String getMessageKey() {
        return switch (this) {
            case LINE_SNAPPED -> "fail-tension";
            case REEL_BROKEN -> "fail-reel-break";
            case DISTANCE_EXCEEDED -> "fail-distance";
            case TIMEOUT -> "fail-timeout";
            case NONE -> "fail";
        };
    }
}
