package ru.apache_maven.model;

public class Challenge {
    private int id;
    private String name;
    private String description;
    private String type;
    private int target;
    private int rewardPoints;
    private int rewardDollars;

    public Challenge(int id, String name, String description, String type, int target, int rewardPoints, int rewardDollars) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.target = target;
        this.rewardPoints = rewardPoints;
        this.rewardDollars = rewardDollars;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public int getTarget() { return target; }
    public int getRewardPoints() { return rewardPoints; }
    public int getRewardDollars() { return rewardDollars; }
}