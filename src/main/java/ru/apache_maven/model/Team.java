package ru.apache_maven.model;

public class Team {
    private int id;
    private String name;
    private int leagueId;

    public Team() {}

    public Team(int id, String name, int leagueId) {
        this.id = id;
        this.name = name;
        this.leagueId = leagueId;
    }

    // Геттеры
    public int getId() { return id; }
    public String getName() { return name; }
    public int getLeagueId() { return leagueId; }

    // Сеттеры
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setLeagueId(int leagueId) { this.leagueId = leagueId; }

    @Override
    public String toString() {
        return "Team{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", leagueId=" + leagueId +
                '}';
    }
}