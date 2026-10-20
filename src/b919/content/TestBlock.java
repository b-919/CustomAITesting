package b919.content;

import b919.content.units.TestUnits;
import b919.world.BoidSpawner;
import mindustry.world.Block;

public class TestBlock {
    public static Block boidSpawner;

    public static void load(){
        boidSpawner = new BoidSpawner("boid-spawner"){{
            unit = TestUnits.AlfredoTheMass;
        }};
    }
}
