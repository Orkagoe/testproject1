package ru.apache_maven.model;

public class Pack {
    private int id;
    private String name;
    private String description;
    private int price;
    private int playerCount;
    private String category;
    private boolean isDaily;
    private int cooldownHours;
    private int quantity;

    public Pack(int id, String name, String description, int price, int playerCount, String category, boolean isDaily, int cooldownHours) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.playerCount = playerCount;
        this.category = category;
        this.isDaily = isDaily;
        this.cooldownHours = cooldownHours;
        this.quantity = 0;
    }

    public Pack() {
    }

    public int getId() { return id; }
    public String getName() { return name != null ? name : "Неизвестный пак"; }
    public String getDescription() { return description != null ? description : "Описание отсутствует"; }
    public int getPrice() { return price; }
    public int getPlayerCount() { return playerCount; }
    public String getCategory() { return category != null ? category : "Случайная"; }
    public boolean isDaily() { return isDaily; }
    public int getCooldownHours() { return cooldownHours; }
    public int getQuantity() { return quantity; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(int price) { this.price = price; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
    public void setCategory(String category) { this.category = category; }
    public void setDaily(boolean isDaily) { this.isDaily = isDaily; }
    public void setCooldownHours(int cooldownHours) { this.cooldownHours = cooldownHours; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}