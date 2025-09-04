package ru.apache_maven.model;

public class User {
    private final long id;
    private final String username;
    private final long points;
    private Integer favoriteCardId; // Новое поле
    private String title; // Новое поле

    public User(long id, String username, long points) {
        this.id = id;
        this.username = username;
        this.points = points;
        this.favoriteCardId = null;
        this.title = "Новичок";
    }

    public User(long id, String username, long points, Integer favoriteCardId, String title) {
        this.id = id;
        this.username = username;
        this.points = points;
        this.favoriteCardId = favoriteCardId;
        this.title = title;
    }

    // Геттеры
    public long getId() { return id; }
    public String getUsername() { return username; }
    public long getPoints() { return points; }
    public Integer getFavoriteCardId() { return favoriteCardId; }
    public String getTitle() { return title; }

    // Сеттеры
    public void setFavoriteCardId(Integer favoriteCardId) { this.favoriteCardId = favoriteCardId; }
    public void setTitle(String title) { this.title = title; }
}