package b919.ai;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.meta.BlockFlag;

public class DangerIndex{
    public static class Danger{
        public float x, y, range;
        public boolean turret;
    }

    private static final Seq<Danger> pool = new Seq<>();
    private static int used;

    private static Seq<Danger> sink;
    private static float sinkX, sinkY, sinkRadius, sinkMargin;

    public static void fill(Seq<Danger> out, Team team, float cx, float cy, float radius, float margin){
        out.clear();
        used = 0;

        Seq<Building> turrets = Vars.indexer.getEnemy(team, BlockFlag.turret);
        if(turrets != null){
            for(int i = 0; i < turrets.size; i++){
                Building b = turrets.get(i);
                if(b == null || !b.isValid()) continue;
                float range = b.block instanceof BaseTurret turret ? turret.range : 90f;
                if(Mathf.dst(cx, cy, b.x, b.y) <= radius + range){
                    add(out, b.x, b.y, range + margin, true);
                }
            }
        }

        sink = out;
        sinkX = cx;
        sinkY = cy;
        sinkRadius = radius;
        sinkMargin = margin;
        Units.nearbyEnemies(team, cx, cy, radius, DangerIndex::addUnit);
        sink = null;
    }

    private static void addUnit(Unit u){
        if(u == null || u.dead() || !u.isValid()) return;
        float range = maxWeaponRange(u.type);
        if(range <= 0f) return;
        if(Mathf.dst(sinkX, sinkY, u.x, u.y) <= sinkRadius + range){
            add(sink, u.x, u.y, range + sinkMargin, false);
        }
    }

    private static void add(Seq<Danger> out, float x, float y, float range, boolean turret){
        Danger d;
        if(used < pool.size){
            d = pool.get(used);
        }else{
            d = new Danger();
            pool.add(d);
        }
        used++;
        d.x = x;
        d.y = y;
        d.range = range;
        d.turret = turret;
        out.add(d);
    }

    public static float maxWeaponRange(UnitType type){
        if(type == null || type.weapons == null) return 0f;
        float r = 0f;
        for(int i = 0; i < type.weapons.size; i++){
            Weapon w = type.weapons.get(i);
            if(w != null) r = Math.max(r, w.range());
        }
        return r;
    }
}
