package ru.apache_maven.model;

public class Clan {
    private int id;
    private String name;
    private long ownerId; // ID владельца клана
    private long totalPoints; // Общие очки клана

    public Clan(int id, String name, long ownerId, long totalPoints) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.totalPoints = totalPoints;
    }

    // Геттеры
    public int getId() { return id; }
    public String getName() { return name; }
    public long getOwnerId() { return ownerId; }
    public long getTotalPoints() { return totalPoints; }

    // Сеттеры
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setOwnerId(long ownerId) { this.ownerId = ownerId; }
    public void setTotalPoints(long totalPoints) { this.totalPoints = totalPoints; }

    @Override
    public String toString() {
        return "Clan{id=" + id + ", name='" + name + "', ownerId=" + ownerId + ", totalPoints=" + totalPoints + "}";
    }
}