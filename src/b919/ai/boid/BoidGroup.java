package b919.ai.boid;

import arc.struct.Seq;
import mindustry.gen.Unit;
public class BoidGroup{
    public Unit leader;
    public final Seq<Unit> members = new Seq<>();
    //TODO follow a stonger ally unit if detected
    public Unit followTarget;
    public float roamX, roamY, roamRadius = 300f;
    public float patrolX, patrolY, patrolTimer;
    public int maxSize = 7;

    public int size(){
        return members.size;
    }

    public boolean hasRoomFor(int n){
        return members.size + n <= maxSize;
    }

    public boolean contains(Unit u){
        return members.contains(u);
    }

    public void add(Unit u){
        if(u != null && !members.contains(u)) members.add(u);
    }
}
