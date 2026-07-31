package me.ninesik.fishing.model;

import java.util.List;

public class Fish {
    private final String id;
    private final String useType;
    private final String vanillaMaterial;
    private final String vanillaName;
    private final String mmoitemsType;
    private final String mmoitemsId;
    private final List<String> vanillaLore;
    private final List<String> commands;
    private final int weight;
    private final boolean doubleEnabled;
    private final Grade grade;

    private Fish(Builder b) {
        this.id = b.id;
        this.useType = b.useType;
        this.vanillaMaterial = b.vanillaMaterial;
        this.vanillaName = b.vanillaName;
        this.vanillaLore = b.vanillaLore != null ? List.copyOf(b.vanillaLore) : List.of();
        this.mmoitemsType = b.mmoitemsType;
        this.mmoitemsId = b.mmoitemsId;
        this.weight = b.weight;
        this.doubleEnabled = b.doubleEnabled;
        this.commands = b.commands != null ? List.copyOf(b.commands) : List.of();
        this.grade = b.grade;
    }

    public String getId() { return id; }
    public String getUseType() { return useType; }
    public String getVanillaMaterial() { return vanillaMaterial; }
    public String getVanillaName() { return vanillaName; }
    public List<String> getVanillaLore() { return vanillaLore; }
    public String getMmoitemsType() { return mmoitemsType; }
    public String getMmoitemsId() { return mmoitemsId; }
    public int getWeight() { return weight; }
    public boolean isDoubleEnabled() { return doubleEnabled; }
    public List<String> getCommands() { return commands; }
    public Grade getGrade() { return grade; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String useType = "vanilla";
        private String vanillaMaterial;
        private String vanillaName;
        private String mmoitemsType;
        private String mmoitemsId;
        private List<String> vanillaLore;
        private List<String> commands;
        private int weight = 1;
        private boolean doubleEnabled = true;
        private Grade grade;

        public Builder id(String v) { id = v; return this; }
        public Builder useType(String v) { useType = v; return this; }
        public Builder vanillaMaterial(String v) { vanillaMaterial = v; return this; }
        public Builder vanillaName(String v) { vanillaName = v; return this; }
        public Builder vanillaLore(List<String> v) { vanillaLore = v; return this; }
        public Builder mmoitemsType(String v) { mmoitemsType = v; return this; }
        public Builder mmoitemsId(String v) { mmoitemsId = v; return this; }
        public Builder weight(int v) { weight = v; return this; }
        public Builder doubleEnabled(boolean v) { doubleEnabled = v; return this; }
        public Builder commands(List<String> v) { commands = v; return this; }
        public Builder grade(Grade v) { grade = v; return this; }

        public Fish build() {
            if (id == null || id.isEmpty())
                throw new IllegalStateException("Fish id must not be null or empty");
            if (grade == null)
                throw new IllegalStateException("Fish grade must not be null");
            return new Fish(this);
        }
    }
}