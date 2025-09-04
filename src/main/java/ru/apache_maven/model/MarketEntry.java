package ru.apache_maven.model;

public class MarketEntry {
    public final long marketId;
    public final long sellerId;
    public final String sellerUsername;
    public final int playerId;
    public final int price;

    public MarketEntry(int marketId, long sellerId, String sellerUsername, int playerId, int price) {
        this.marketId = marketId;
        this.sellerId = sellerId;
        this.sellerUsername = sellerUsername;
        this.playerId = playerId;
        this.price = price;
    }
}