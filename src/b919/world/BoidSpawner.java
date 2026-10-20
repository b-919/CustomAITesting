package b919.world;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.UnitBlock;
import mindustry.world.meta.BuildVisibility;

public class BoidSpawner extends UnitBlock{
    public UnitType unit;
    public float spawnMinSeconds = 5f, spawnMaxSeconds = 20f;
    public float wanderRadius = 800f;

    public BoidSpawner(String name){
        super(name);
        update = true;
        solid = true;
        rotate = false;
        size = 3;
        health = 600;
        hasPower = false;
        hasItems = false;
        configurable = false;
        alwaysUnlocked = true;
        category = Category.units;
        buildVisibility = BuildVisibility.shown;
        requirements(Category.units, BuildVisibility.shown, ItemStack.empty);
    }

    public class BoidSpawnerBuild extends UnitBuild{
        public float timer, delay;

        @Override
        public void updateTile(){
            moveOutPayload();

            if(payload != null) return;

            if(delay <= 0f) delay = Mathf.random(spawnMinSeconds, spawnMaxSeconds);

            timer += Time.delta / 60f;
            if(timer >= delay){
                timer = 0f;
                delay = Mathf.random(spawnMinSeconds, spawnMaxSeconds);
                prepareSpawn();
            }
        }

        public float roamRadius(){
            float w = Vars.world.unitWidth(), h = Vars.world.unitHeight();
            return Math.max(Math.min(wanderRadius, Math.min(w, h) / 2f), 8f);
        }

        public float roamX(){
            float w = Vars.world.unitWidth(), r = roamRadius();
            return Mathf.clamp(x, r, Math.max(r, w - r));
        }

        public float roamY(){
            float h = Vars.world.unitHeight(), r = roamRadius();
            return Mathf.clamp(y, r, Math.max(r, h - r));
        }

        public void prepareSpawn(){
            if(unit == null) return;

            Unit spawned = unit.create(team);
            if(spawned == null) return;

            spawned.rotation = rotdeg();
            spawned.set(x, y);
            payload = new UnitPayload(spawned);
            payVector.setZero();
            payRotation = rotdeg();
        }

        @Override
        public void dumpPayload(){
            if(payload != null && payload.dump()){
                payload = null;
            }
        }
    }
}
