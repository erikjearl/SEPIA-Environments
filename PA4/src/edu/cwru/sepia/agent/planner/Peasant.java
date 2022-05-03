package edu.cwru.sepia.agent.planner;

public class Peasant {

    private final int id;
    private Position position;
    private int wood;
    private int gold;

    /**
     * default Peasant class constructor.
     *
     * @param id a unit ID
     * @param position the x-y coordinates
     */
    public Peasant(int id, Position position){
        this.position = position;
        this.id = id;
    }

    /**
     * alternate constructor for efficiently copying a peasant unit.
     *
     * @param peasant an ordinary peasant
     */
    public Peasant(Peasant peasant){
        this.id = peasant.getId();
        this.position = peasant.getPosition();
        this.wood = peasant.getWood();
        this.gold = peasant.getGold();
    }

    public int getId(){
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position newPosition) {
        this.position = newPosition;
    }

    public boolean hasResource(){
        return (wood != 0 || gold != 0);
    }

    public int getGold(){
        return gold;
    }

    public void addGold(int amount){
        gold += amount;
    }

    public int getWood(){
        return wood;
    }

    public void addWood(int amount){
        wood += amount;
    }

}