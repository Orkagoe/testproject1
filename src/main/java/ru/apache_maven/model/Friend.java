package ru.apache_maven.model;

public class Friend {
    private final long userId;
    private final String username;
    private final int friendshipPoints;

    public Friend(long userId, String username, int friendshipPoints) {
        this.userId = userId;
        this.username = username;
        this.friendshipPoints = friendshipPoints;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public int getFriendshipPoints() {
        return friendshipPoints;
    }
}
