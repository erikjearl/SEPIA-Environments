package edu.cwru.sepia.agent.planner.resources;

import edu.cwru.sepia.agent.planner.Position;

/**
 * A Resource class.
 * A Resource can be collected, transported, and relocated.
 *
 * Contains a set of getter and setter methods.
 */
public abstract class Resource {
    protected int id;
    protected int amount;
    protected Position position;

    /**
     * the default constructor for the abstract class.
     *
     * @param id a unit ID
     * @param amount quantity of a particular resource
     * @param position the x-y coordinates.
     */
    public Resource(int id, int amount, Position position){
        this.id = id;
        this.position = position;
        this.amount = amount;
    }

    public abstract boolean isGold();

    public abstract boolean isWood();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void collect(int amount) {
        this.amount -= amount;
    }
}
