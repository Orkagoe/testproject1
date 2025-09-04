package ru.apache_maven.model;

public class League {
    private int id;
    private String name;

    public League(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}