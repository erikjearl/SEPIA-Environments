package edu.cwru.sepia.agent.planner.resources;

import edu.cwru.sepia.agent.planner.Position;

/**
 * Gold is a type of Resource
 */
public class Gold extends Resource {

    public Gold(int id, int amount, Position position){
        super(id, amount, position);
    }

    @Override
    public boolean isGold() {
        return true;
    }

    @Override
    public boolean isWood() {
        return false;
    }
}
