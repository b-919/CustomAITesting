package b919.ai.boid;

import arc.graphics.Color;

public enum BoidState{
    WANDER(Color.valueOf("9a9a9a"), "wander"),
    FOLLOW(Color.valueOf("33ccff"), "follow"),
    SWARM(Color.valueOf("ffd166"), "swarm"),
    EVADE(Color.valueOf("b07dff"), "evade"),
    RAM_BUILDING(Color.valueOf("ff5c5c"), "ram-building"),
    RAM_UNIT(Color.valueOf("ff8c42"), "ram-unit");

    public final Color color;
    public final String label;

    BoidState(Color color, String label){
        this.color = color;
        this.label = label;
    }
}
