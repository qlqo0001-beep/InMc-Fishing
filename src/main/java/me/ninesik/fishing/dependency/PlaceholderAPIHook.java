package me.ninesik.fishing.dependency;

import me.ninesik.fishing.InMcFishing;

/**
 * PlaceholderAPI 연동.
 *
 * <p>이전에는 {@code registerPlaceholders()}가 빈 메서드고 {@code unregister()}의
 * try 블록은 주석뿐이라, 감지만 하고 아무 placeholder도 제공하지 않으면서
 * 콘솔에는 "hooked successfully"를 찍고 있었다.</p>
 *
 * <p>실제 등록은 {@link FishingPlaceholderExpansion}이 한다. PAPI가 서버에 없으면
 * 그 클래스를 로드하는 것만으로 {@code NoClassDefFoundError}가 나므로,
 * 참조를 이 훅 안에서만 하고 {@link #available} 가드 뒤에 둔다.</p>
 */
public class PlaceholderAPIHook {
    private final InMcFishing plugin;
    private boolean available = false;

    /** 등록된 확장. 미등록이면 null — shutdown에서 이 값으로 해제 여부를 판단한다. */
    private FishingPlaceholderExpansion expansion;

    public PlaceholderAPIHook(InMcFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.available = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            this.available = false;
            plugin.getLogger().info("PlaceholderAPI not found. Placeholder features will be disabled.");
            return;
        }
        registerPlaceholders();
    }

    public boolean isAvailable() {
        return available;
    }

    private void registerPlaceholders() {
        try {
            FishingPlaceholderExpansion created = new FishingPlaceholderExpansion(plugin);
            if (created.register()) {
                this.expansion = created;
                plugin.getLogger().info("PlaceholderAPI 연동 완료 — %inmcfishing_...% 사용 가능");
            } else {
                // register()가 false면 PAPI가 등록을 거부한 것이다(중복 identifier 등).
                // 예전처럼 "hooked successfully"를 찍어 성공한 척하지 않는다.
                plugin.getLogger().warning(
                        "PlaceholderAPI 확장 등록에 실패했습니다. %inmcfishing_...% 는 동작하지 않습니다.");
            }
        } catch (Exception | NoClassDefFoundError e) {
            plugin.getLogger().warning("PlaceholderAPI 확장 등록 중 오류: " + e.getMessage());
        }
    }

    /** 플러그인 종료 시 확장을 해제한다. 남겨두면 PAPI가 죽은 플러그인 인스턴스를 계속 잡는다. */
    public void unregister() {
        if (expansion == null) {
            return;
        }
        try {
            expansion.unregister();
        } catch (Exception | NoClassDefFoundError e) {
            plugin.getLogger().warning("Failed to unregister PlaceholderAPI placeholders: " + e.getMessage());
        } finally {
            expansion = null;
        }
    }
}
