package dev.growops;

public final class Plant {
    public String strain;
    public double progress;
    public double water = 60;
    public double health = 100;
    public boolean infested;
    public String fertilizer;
    public double careSum;
    public double careTime;
    public double decay;
    public boolean ready;
    public boolean dead;

    public Plant(String strain) {
        this.strain = strain;
    }
}
