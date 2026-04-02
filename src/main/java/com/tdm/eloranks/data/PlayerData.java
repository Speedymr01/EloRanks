package com.tdm.eloranks.data;

import java.util.UUID;

/**
 * Represents a player's Elo data including rating, rank, and stats.
 */
public class PlayerData {

    private final UUID playerId;
    private String playerName;
    private int elo;
    private int rank;
    private int wins;
    private int losses;
    private int draws;
    private long lastDuelTime;
    private int placementMatches; // Number of placement matches played (0-5)
    private boolean placementCompleted; // Whether placement is done

    public PlayerData(UUID playerId, String playerName, int elo) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.elo = elo;
        this.rank = 0;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.lastDuelTime = 0;
        this.placementMatches = 0;
        this.placementCompleted = false;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        this.wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void addLoss() {
        this.losses++;
    }

    public int getDraws() {
        return draws;
    }

    public void addDraw() {
        this.draws++;
    }

    public long getLastDuelTime() {
        return lastDuelTime;
    }

    public void setLastDuelTime(long lastDuelTime) {
        this.lastDuelTime = lastDuelTime;
    }

    public int getTotalMatches() {
        return wins + losses + draws;
    }
    
    public int getPlacementMatches() {
        return placementMatches;
    }
    
    public void incrementPlacementMatches() {
        this.placementMatches++;
    }
    
    public void setPlacementMatches(int matches) {
        this.placementMatches = matches;
    }
    
    public boolean isPlacementCompleted() {
        return placementCompleted;
    }
    
    public void setPlacementCompleted(boolean completed) {
        this.placementCompleted = completed;
    }
    
    public boolean isInPlacement() {
        return !placementCompleted && placementMatches < 5;
    }

    public double getWinRate() {
        if (getTotalMatches() == 0) return 0.0;
        return (double) wins / getTotalMatches() * 100;
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "playerId=" + playerId +
                ", playerName='" + playerName + '\'' +
                ", elo=" + elo +
                ", rank=" + rank +
                ", wins=" + wins +
                ", losses=" + losses +
                ", draws=" + draws +
                '}';
    }
}
