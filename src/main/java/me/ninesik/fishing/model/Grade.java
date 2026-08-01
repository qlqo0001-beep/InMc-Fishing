package me.ninesik.fishing.model;

import java.util.Objects;

public class Grade {
    private final String id;
    private final int weight;
    private final int inputCount;
    private final double timeSeconds;
    private final String nextGradeId;
    private final String color; // 등급 표시용 색상 코드 (예: &f, &a)

    private Grade(String id, int weight, int inputCount, double timeSeconds, String nextGradeId, String color) {
        this.id = id;
        this.weight = weight;
        this.inputCount = inputCount;
        this.timeSeconds = timeSeconds;
        this.nextGradeId = nextGradeId;
        this.color = color;
    }

    public static Grade create(String id, int weight, int inputCount, double timeSeconds, String nextGradeId) {
        return create(id, weight, inputCount, timeSeconds, nextGradeId, "&f");
    }

    public static Grade create(String id, int weight, int inputCount, double timeSeconds, String nextGradeId, String color) {
        return new Grade(id, weight, inputCount, timeSeconds, nextGradeId, color != null ? color : "&f");
    }

    public String getId() { return id; }
    public int getWeight() { return weight; }
    public int getInputCount() { return inputCount; }
    public double getTimeSeconds() { return timeSeconds; }
    public String getNextGradeId() { return nextGradeId; }
    public String getColor() { return color; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Grade grade = (Grade) o;
        return Objects.equals(id, grade.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Grade{" +
                "id='" + id + '\'' +
                ", weight=" + weight +
                ", inputCount=" + inputCount +
                ", timeSeconds=" + timeSeconds +
                ", nextGradeId='" + nextGradeId + '\'' +
                '}';
    }
}