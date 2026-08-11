package me.ninesik.fishing.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 낚싯대 아이템 로어 포맷 설정 (item-format.yml).
 *
 * <p>Immutable 설정 모델 — 저장된 값은 그대로(색상 코드 포함) 보관하고,
 * 사용처(FishingCommand.appendRodLore)에서 {@code &}→{@code §} 변환을 적용한다.</p>
 */
public final class ItemFormatConfig {

    private final String divider;
    private final boolean sectionGap;
    private final boolean footerDivider;

    private final GradeBonusConfig gradeBonus;
    private final SectionConfig fishingOptions;
    private final SectionConfig fightOptions;
    private final SectionConfig fatigue;

    public ItemFormatConfig(FileConfiguration config) {
        org.bukkit.configuration.ConfigurationSection cfg = config.getConfigurationSection("rod");
        if (cfg == null) {
            cfg = config;
        }
        this.divider = cfg.getString("divider", "&8━━━━━━━━━━━━━━━━━━━━");
        this.sectionGap = cfg.getBoolean("section-gap", true);
        this.footerDivider = cfg.getBoolean("footer-divider", true);

        this.gradeBonus = new GradeBonusConfig(cfg.getConfigurationSection("grade-bonus"));
        this.fishingOptions = new SectionConfig(
                cfg.getConfigurationSection("fishing-options"),
                "&b◈ 낚시 옵션", "더블 확률", "대어 확률");
        this.fightOptions = new SectionConfig(
                cfg.getConfigurationSection("fight-options"),
                "&d◈ 트로피 파이트 옵션", "릴 파워", "줄 강도", "릴 내구도");
        this.fatigue = new SectionConfig(
                cfg.getConfigurationSection("fatigue"),
                "&c◈ 피로도", "최대 피로도", "피로 회복");
    }

    public String getDivider() { return divider; }
    public boolean isSectionGap() { return sectionGap; }
    public boolean isFooterDivider() { return footerDivider; }
    public GradeBonusConfig getGradeBonus() { return gradeBonus; }
    public SectionConfig getFishingOptions() { return fishingOptions; }
    public SectionConfig getFightOptions() { return fightOptions; }
    public SectionConfig getFatigue() { return fatigue; }

    /** 등급 보너스 섹션 전용 설정. */
    public static final class GradeBonusConfig {
        private final boolean enabled;
        private final String title;
        private final boolean showZero;
        private final String linePrefix;
        private final String valueSeparator;
        private final String positiveColor;
        private final String negativeColor;
        private final boolean useGradeColorPositive;

        GradeBonusConfig(org.bukkit.configuration.ConfigurationSection s) {
            this.enabled = s != null && s.getBoolean("enabled", true);
            this.title = s != null ? s.getString("title", "&6✧ 등급 보너스") : "&6✧ 등급 보너스";
            this.showZero = s != null && s.getBoolean("show-zero", false);
            this.linePrefix = s != null ? s.getString("line-prefix", "  &8▸ &7") : "  &8▸ &7";
            this.valueSeparator = s != null ? s.getString("value-separator", "   ") : "   ";
            this.positiveColor = s != null ? s.getString("positive-color", "&a") : "&a";
            this.negativeColor = s != null ? s.getString("negative-color", "&c") : "&c";
            this.useGradeColorPositive = s == null || s.getBoolean("use-grade-color-positive", true);
        }

        public boolean isEnabled() { return enabled; }
        public String getTitle() { return title; }
        public boolean isShowZero() { return showZero; }
        public String getLinePrefix() { return linePrefix; }
        public String getValueSeparator() { return valueSeparator; }
        public String getPositiveColor() { return positiveColor; }
        public String getNegativeColor() { return negativeColor; }
        public boolean isUseGradeColorPositive() { return useGradeColorPositive; }
    }

    /** 낚시/파이트/피로도 공통 섹션 설정 (라벨 목록 = 각 줄의 능력 이름). */
    public static final class SectionConfig {
        private final boolean enabled;
        private final String title;
        private final String linePrefix;
        private final String valueSeparator;
        private final String positiveColor;
        private final String[] labels;

        SectionConfig(org.bukkit.configuration.ConfigurationSection s,
                      String defaultTitle, String... defaultLabels) {
            this.enabled = s != null && s.getBoolean("enabled", true);
            this.title = s != null ? s.getString("title", defaultTitle) : defaultTitle;
            this.linePrefix = s != null ? s.getString("line-prefix", "  &8▸ &7") : "  &8▸ &7";
            this.valueSeparator = s != null ? s.getString("value-separator", "   ") : "   ";
            this.positiveColor = s != null ? s.getString("positive-color", "&a") : "&a";
            String[] loaded = new String[defaultLabels.length];
            for (int i = 0; i < defaultLabels.length; i++) {
                loaded[i] = s != null && s.getString("label-" + (i + 1)) != null
                        ? s.getString("label-" + (i + 1))
                        : defaultLabels[i];
            }
            this.labels = loaded;
        }

        public boolean isEnabled() { return enabled; }
        public String getTitle() { return title; }
        public String getLinePrefix() { return linePrefix; }
        public String getValueSeparator() { return valueSeparator; }
        public String getPositiveColor() { return positiveColor; }
        public String[] getLabels() { return labels; }
    }
}
