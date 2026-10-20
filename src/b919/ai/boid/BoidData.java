package b919.ai.boid;

import arc.struct.IntMap;
import arc.struct.Seq;
import b919.ai.DangerIndex;
import mindustry.gen.Building;
import mindustry.gen.Unit;

public class BoidData{
    protected static final IntMap<BoidData> map = new IntMap<>();

    public BoidState state = BoidState.WANDER;

    public float mass;
    public boolean spent;

    public int chargedCount;

    public float wanderAngle;
    public float phase;

    public float neighborTimer, dangerTimer, decisionTimer;

    public Unit leader;
    public BoidGroup group;
    public Unit unitTarget;
    public Building buildingTarget;

    public boolean homeChecked, hasHome;
    public Building home;
    public float homeX, homeY, homeRadius = 240f, roamRadius = 240f;

    public final Seq<Unit> flock = new Seq<>();
    public final Seq<DangerIndex.Danger> dangers = new Seq<>();

    public static BoidData of(Unit unit){
        BoidData data = map.get(unit.id);
        if(data == null){
            data = new BoidData();
            data.wanderAngle = (unit.id * 137.508f) % 360f;
            data.phase = (unit.id * 57.3f) % 360f;
            map.put(unit.id, data);
        }
        return data;
    }

    public static BoidData get(Unit unit){
        return unit == null ? null : map.get(unit.id);
    }

    public static void remove(Unit unit){
        if(unit != null) map.remove(unit.id);
    }

    public static void clear(){
        map.clear();
    }
}
