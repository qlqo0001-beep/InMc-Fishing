package me.ninesik.fishing.collection;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 지급 대기 중인 도감 보상.
 * 인벤토리가 꽉 차서 즉시 지급하지 못한 보상을 저장한다.
 * (PendingReward → PendingMilestoneReward: 도감 마일스톤 보상임을 명시)
 */
public class PendingMilestoneReward {

    private final String key;
    private final List<String> commands;
    private final LocalDateTime createdAt;
    /** 지급 사유 설명 (보상 이유 안내용). null이면 기본 문구로 대체된다. */
    private final String description;

    public PendingMilestoneReward(String key, List<String> commands) {
        this(key, commands, null);
    }

    public PendingMilestoneReward(String key, List<String> commands, String description) {
        this.key = key;
        this.commands = commands != null ? List.copyOf(commands) : Collections.emptyList();
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public String getKey() { return key; }
    public List<String> getCommands() { return commands; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
}
