package edu.cwru.sepia.agent.planner.resources;

import edu.cwru.sepia.agent.planner.Position;

/**
 * Wood is a type of Resource
 */
public class Wood extends Resource {

    public Wood(int id, int amount, Position position){
        super(id, amount, position);
    }

    @Override
    public boolean isGold() {
        return false;
    }

    @Override
    public boolean isWood() {
        return true;
    }
}
