package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;

public class ProtocolLibHook {
    private final InMcFishing plugin;
    private boolean available = false;

    public ProtocolLibHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            this.available = true;
            plugin.getLogger().info("ProtocolLib detected and hooked successfully.");
        } catch (ClassNotFoundException e) {
            this.available = false;
            plugin.getLogger().info("ProtocolLib not found. ProtocolLib features will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    // TODO: ProtocolLib 패킷 조작 메서드 구현
    // - 커스텀 애니메이션
    // - 클라이언트 측 효과
    // - 패킷 필터링
}