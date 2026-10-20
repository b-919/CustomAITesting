package b919.ai.boid;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import b919.ai.DangerIndex;
import b919.world.BoidSpawner;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.meta.BlockGroup;

public class BoidMassAI extends AIController{
    public static float perceptionRadius = 120f;
    public static float separationRadius = 40f;
    public static float wSep = 1.5f, wAli = 1.0f, wCoh = 1.0f, wSeek = 1.4f, wFlee = 2.0f, wOrbit = 1.4f;
    public static float wanderDistance = 80f, wanderJitter = 10f;
    public static float massPerSecond = 0.5f;
    public static float massCap = 10f;
    public static float massThreshold = 10f;
    public static float groupRadius = 200f;
    public static float followRadius = 45f;
    public static int groupMinSize = 7, groupMaxSize = 12;
    public static float groupDefaultRadius = 300f;
    public static float groupPatrolSpeed = 24f;

    public static float detectRadius = 180f;
    public static float attackRange = 26f;

    public static float dangerScanRadius = 260f;
    public static float dangerMargin = 24f;

    public static float borderMargin = 100f;

    public static float homeSearchRadius = 900f;

    public static float neighborInterval = 4f;
    public static float decisionInterval = 10f;
    public static float dangerInterval = 8f;
    private static final Vec2 sep = new Vec2(), ali = new Vec2(), coh = new Vec2();
    private static final Vec2 steer = new Vec2(), goal = new Vec2(), tmp = new Vec2();
    private static Unit bestUnit;
    private static Building bestBuilding;
    private static int bestPriority;
    private static float bestScore;
    private static int neighborCount;

    @Override
    public void updateMovement(){
        if(unit == null || unit.dead() || !unit.isAdded() || unit.isPlayer()) return;

        BoidData d = BoidData.of(unit);

        if(d.hasHome && (d.home == null || !d.home.isValid())){
            d.hasHome = false;
            d.home = null;
            d.homeChecked = false;
        }
        if(!d.homeChecked) initHome(d);

        if(!d.spent){
            d.mass = Math.min(massCap, d.mass + massPerSecond * Time.delta / 60f);
        }

        d.neighborTimer -= Time.delta;
        d.dangerTimer -= Time.delta;
        d.decisionTimer -= Time.delta;

        if(d.neighborTimer <= 0f){
            d.neighborTimer = neighborInterval;
            scanNeighbors(d);
            scanCharged(d);
        }

        updateGroup(d);

        if(d.dangerTimer <= 0f){
            d.dangerTimer = dangerInterval;
            DangerIndex.fill(d.dangers, unit.team, unit.x, unit.y, dangerScanRadius, dangerMargin);
        }

        if(d.decisionTimer <= 0f){
            d.decisionTimer = decisionInterval;
            decide(d);
        }

        steer.setZero();

        switch(d.state){
            case WANDER -> {
                flock(d, true);
                seekWander(d);
                if(!charged(d)) fleeDanger(d, wFlee * 0.5f);
            }
            case FOLLOW -> {
                flock(d, true);
                seekLeader(d);
                if(!charged(d)) fleeDanger(d, wFlee * 0.5f);
            }
            case SWARM -> {
                flock(d, true);
                seekTarget(d);
                if(!charged(d)) fleeDanger(d, wFlee * 0.35f);
            }
            case EVADE -> {
                flock(d, false);
                fleeDanger(d, wFlee);
            }
            case RAM_BUILDING, RAM_UNIT -> {
                ram(d);
                return;
            }
        }

        if(d.state != BoidState.FOLLOW) contain(d);
        avoidBorders();
        antiStuck(d);
        apply();
    }

    /** If conflicting forces cancel out at a border or inside a crowd, nudge tangentially so boids never freeze. */
    private void antiStuck(BoidData d){
        float sp = unit.speed();
        if(steer.len2() >= sp * sp * 0.04f || unit.vel.len2() >= sp * sp * 0.01f) return;

        float ang = hasArea(d) ? Angles.angle(areaX(d), areaY(d), unit.x, unit.y) : d.wanderAngle;
        steer.add(Angles.trnsx(ang + 90f, sp * 0.6f), Angles.trnsy(ang + 90f, sp * 0.6f));
    }

    @Override
    public void removed(Unit unit){
        BoidData.remove(unit);
    }

    @Override
    public void afterRead(Unit unit){
        BoidData.remove(unit);
        target = null;
    }

    private boolean charged(BoidData d){
        return d.mass >= massThreshold;
    }

    private void apply(){
        if(steer.isNaN()) steer.setZero();
        steer.limit(unit.speed());
        unit.movePref(steer);
        if(steer.len2() > 0.0001f){
            unit.lookAt(steer.angle());
        }
    }

    private void initHome(BoidData d){
        d.homeChecked = true;
        bestBuilding = null;
        bestScore = Float.MAX_VALUE;
        Vars.indexer.eachBlock(unit.team, unit.x, unit.y, homeSearchRadius, b -> b.block instanceof BoidSpawner, b -> {
            float dst = Mathf.dst2(unit.x, unit.y, b.x, b.y);
            if(dst < bestScore){
                bestScore = dst;
                bestBuilding = b;
            }
        });

        if(bestBuilding != null){
            BoidSpawner.BoidSpawnerBuild spawner = (BoidSpawner.BoidSpawnerBuild)bestBuilding;
            d.hasHome = true;
            d.home = bestBuilding;
            d.homeRadius = ((BoidSpawner)bestBuilding.block).wanderRadius;
            d.homeX = spawner.roamX();
            d.homeY = spawner.roamY();
            d.roamRadius = spawner.roamRadius();
        }
    }

    /** Rectangle containment: at an edge it strips the outward component and adds an inward one, so motion never cancels. */
    private void limitToRect(float minX, float minY, float maxX, float maxY){
        float speed = unit.speed();
        float pad = Math.max(unit.hitSize * 0.5f, 1f);

        if(unit.x < minX + pad){
            if(steer.x < 0f) steer.x = 0f;
            steer.x += speed * (0.5f + Mathf.clamp((minX + pad - unit.x) / pad, 0f, 1f));
        }else if(unit.x > maxX - pad){
            if(steer.x > 0f) steer.x = 0f;
            steer.x -= speed * (0.5f + Mathf.clamp((unit.x - (maxX - pad)) / pad, 0f, 1f));
        }

        if(unit.y < minY + pad){
            if(steer.y < 0f) steer.y = 0f;
            steer.y += speed * (0.5f + Mathf.clamp((minY + pad - unit.y) / pad, 0f, 1f));
        }else if(unit.y > maxY - pad){
            if(steer.y > 0f) steer.y = 0f;
            steer.y -= speed * (0.5f + Mathf.clamp((unit.y - (maxY - pad)) / pad, 0f, 1f));
        }
    }

    /** Circular containment: strips the outward component and adds inward, so motion never cancels. */
    private void contain(BoidData d){
        if(!hasArea(d)) return;

        float cx = areaX(d), cy = areaY(d), r = areaRadius(d);
        float dx = unit.x - cx, dy = unit.y - cy;
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        if(dist <= r || dist < 0.001f) return;

        float nx = dx / dist, ny = dy / dist;
        float speed = unit.speed();

        float out = steer.x * nx + steer.y * ny;
        if(out > 0f){
            steer.x -= nx * out;
            steer.y -= ny * out;
        }
        float over = Mathf.clamp((dist - r) / Math.max(r * 0.4f, 1f), 0f, 1f);
        steer.x -= nx * speed * (0.6f + over);
        steer.y -= ny * speed * (0.6f + over);
    }

    private void avoidBorders(){
        limitToRect(borderMargin, borderMargin, Vars.world.unitWidth() - borderMargin, Vars.world.unitHeight() - borderMargin);
    }

    private void scanNeighbors(BoidData d){
        d.flock.clear();
        Units.nearby(unit.team, unit.x, unit.y, perceptionRadius, other -> {
            if(other != unit && !other.dead() && other.isValid() && other.type == unit.type){
                d.flock.add(other);
            }
        });
    }

    // ----- groups -----

    /** Ensure this boid belongs to a group: join one nearby, create one, and (if leader) absorb smaller ones. */
    private void updateGroup(BoidData d){
        if(d.group != null){
            BoidGroup g = d.group;
            if(!g.contains(unit)){
                d.group = null;
            }else if(g.leader == null || g.leader.dead() || !g.leader.isValid()){
                promoteLeader(g);
            }
        }

        if(d.group == null){
            BoidGroup join = findJoinableGroup(d);
            if(join != null){
                join.add(unit);
                d.group = join;
            }else{
                createGroup(d);
            }
        }

        BoidGroup g = d.group;
        if(g != null && g.leader == unit){
            updateGroupPatrol(g);
            tryAbsorb(d, g);
        }
    }

    /** Nearest nearby group that still has room for this boid ("the first group they met"). */
    private BoidGroup findJoinableGroup(BoidData d){
        BoidGroup best = null;
        float bestScore = Float.MAX_VALUE;

        for(int i = 0; i < d.flock.size; i++){
            Unit o = d.flock.get(i);
            if(o == null || o.dead()) continue;
            BoidData od = BoidData.get(o);
            if(od == null || od.group == null || od.group == d.group) continue;
            if(!od.group.hasRoomFor(1)) continue;

            float dst = Mathf.dst2(unit.x, unit.y, o.x, o.y);
            if(dst < bestScore){
                bestScore = dst;
                best = od.group;
            }
        }
        return best;
    }

    private void createGroup(BoidData d){
        BoidGroup g = new BoidGroup();
        g.leader = unit;
        g.maxSize = Mathf.random(groupMinSize, groupMaxSize + 1);
        g.add(unit);

        if(d.hasHome){
            g.roamX = d.homeX;
            g.roamY = d.homeY;
            g.roamRadius = d.roamRadius;
        }else{
            g.roamX = unit.x;
            g.roamY = unit.y;
            g.roamRadius = groupDefaultRadius;
        }

        g.patrolTimer = 0f;
        updateGroupPatrol(g);
        d.group = g;
    }

    /** Leader picks a random waypoint inside the group area and re-rolls it once reached or after a delay. */
    private void updateGroupPatrol(BoidGroup g){
        g.patrolTimer -= Time.delta / 60f;
        if(g.patrolTimer <= 0f || Mathf.dst(unit.x, unit.y, g.patrolX, g.patrolY) < 24f){
            g.patrolTimer = Mathf.random(3f, 7f);
            float a = Mathf.random(360f);
            float r = Mathf.random() * g.roamRadius * 0.8f;
            g.patrolX = g.roamX + Angles.trnsx(a, r);
            g.patrolY = g.roamY + Angles.trnsy(a, r);
        }
    }

    /** Absorb any smaller group we meet, unless the merge would exceed this group's max size. */
    private void tryAbsorb(BoidData d, BoidGroup g){
        if(g.leader == null) return;

        for(int i = 0; i < d.flock.size; i++){
            Unit o = d.flock.get(i);
            if(o == null || o.dead()) continue;
            BoidData od = BoidData.get(o);
            if(od == null || od.group == null || od.group == g) continue;

            BoidGroup og = od.group;
            if(og.leader == null) continue;

            boolean bigger = g.size() > og.size() || (g.size() == og.size() && g.leader.id < og.leader.id);
            if(!bigger) continue;
            if(g.size() + og.size() > g.maxSize) continue;

            absorb(g, og);
        }
    }

    private void absorb(BoidGroup into, BoidGroup from){
        for(int i = 0; i < from.members.size; i++){
            Unit m = from.members.get(i);
            BoidData md = BoidData.get(m);
            if(md != null) md.group = into;
            into.add(m);
        }
        from.members.clear();
        from.leader = null;
    }

    /** Promote the lowest-id surviving member to leader when the old one is gone. */
    private void promoteLeader(BoidGroup g){
        Unit best = null;
        for(int i = 0; i < g.members.size; i++){
            Unit m = g.members.get(i);
            if(m == null || m.dead() || !m.isValid()) continue;
            if(best == null || m.id < best.id) best = m;
        }
        g.leader = best;
    }

    private boolean hasArea(BoidData d){
        return d.group != null || d.hasHome;
    }

    private float areaX(BoidData d){
        return d.group != null ? d.group.roamX : d.homeX;
    }

    private float areaY(BoidData d){
        return d.group != null ? d.group.roamY : d.homeY;
    }

    private float areaRadius(BoidData d){
        return d.group != null ? d.group.roamRadius : d.roamRadius;
    }

    private void scanCharged(BoidData d){
        d.chargedCount = charged(d) ? 1 : 0;
        Units.nearby(unit.team, unit.x, unit.y, groupRadius, other -> {
            if(other != unit && other.isValid() && other.type == unit.type){
                BoidData od = BoidData.get(other);
                if(od != null && od.mass >= massThreshold) d.chargedCount++;
            }
        });
    }

    private void decide(BoidData d){
        d.buildingTarget = null;
        d.unitTarget = null;
        d.leader = null;

        if(!charged(d) && insideTurretDanger(d)){
            d.state = BoidState.EVADE;
            return;
        }

        Teamc target = findBlockTarget(d);
        if(target == null) target = findEnemyUnit(d);

        if(target != null){
            float reach = attackRange + targetHalf(target);
            float dst = Mathf.dst(unit.x, unit.y, target.getX(), target.getY());
            if(target instanceof Building b){
                d.buildingTarget = b;
                d.state = dst <= reach ? BoidState.RAM_BUILDING : BoidState.SWARM;
            }else{
                d.unitTarget = (Unit)target;
                d.state = dst <= reach ? BoidState.RAM_UNIT : BoidState.SWARM;
            }
            return;
        }

        BoidGroup g = d.group;
        if(g != null && g.leader != null && g.leader != unit && !g.leader.dead() && g.leader.isValid()){
            d.state = BoidState.FOLLOW;
            d.leader = g.leader;
        }else{
            d.state = BoidState.WANDER;
        }
    }

    /** Priority order: turrets, walls, any other block. Lowest priority (units) handled separately. */
    private Teamc findBlockTarget(BoidData d){
        bestBuilding = null;
        bestPriority = 3;
        bestScore = Float.MAX_VALUE;

        Vars.indexer.allBuildings(unit.x, unit.y, detectRadius, b -> {
            if(b == null || !b.isValid() || !b.block.targetable) return;
            if(b.team == unit.team || b.team == Team.derelict) return;
            if(!withinArea(d, b.x, b.y)) return;

            float dst = Mathf.dst(unit.x, unit.y, b.x, b.y) - b.block.size * Vars.tilesize / 2f;
            if(dst > detectRadius) return;

            int pri = blockPriority(b);
            if(pri < bestPriority || (pri == bestPriority && dst < bestScore)){
                bestPriority = pri;
                bestScore = dst;
                bestBuilding = b;
            }
        });

        return bestBuilding;
    }

    private int blockPriority(Building b){
        if(b.block instanceof BaseTurret || b.block.group == BlockGroup.turrets) return 0;
        if(b.block.group == BlockGroup.walls) return 1;
        return 2;
    }

    private Unit findEnemyUnit(BoidData d){
        bestUnit = null;
        bestScore = Float.MAX_VALUE;
        Units.nearbyEnemies(unit.team, unit.x, unit.y, detectRadius, e -> {
            if(e == null || e.dead() || !e.isValid() || !e.targetable(unit.team)) return;
            if(!withinArea(d, e.x, e.y)) return;
            float dst = Mathf.dst2(unit.x, unit.y, e.x, e.y);
            if(dst < bestScore){
                bestScore = dst;
                bestUnit = e;
            }
        });
        return bestUnit;
    }

    private boolean withinArea(BoidData d, float x, float y){
        if(!hasArea(d)) return true;
        return Mathf.dst(x, y, areaX(d), areaY(d)) <= areaRadius(d);
    }

    private float targetHalf(Teamc t){
        if(t instanceof Building b) return b.block.size * Vars.tilesize / 2f;
        if(t instanceof Unit u) return u.hitSize / 2f;
        return 6f;
    }

    private boolean insideTurretDanger(BoidData d){
        for(int i = 0; i < d.dangers.size; i++){
            DangerIndex.Danger dg = d.dangers.get(i);
            if(dg.turret && Mathf.dst(unit.x, unit.y, dg.x, dg.y) <= dg.range) return true;
        }
        return false;
    }

    private void flock(BoidData d, boolean full){
        sep.setZero();
        ali.setZero();
        coh.setZero();
        neighborCount = 0;

        for(int i = 0; i < d.flock.size; i++){
            Unit o = d.flock.get(i);
            if(o == null || o.dead()) continue;

            float dx = unit.x - o.x, dy = unit.y - o.y;
            float dst = Mathf.dst(unit.x, unit.y, o.x, o.y);

            if(dst > 0.001f && dst < separationRadius){
                float s = 1f - dst / separationRadius;
                sep.add(dx / dst * s, dy / dst * s);
            }

            BoidData od = BoidData.get(o);
            if(d.group != (od == null ? null : od.group)) continue;

            ali.add(o.vel.x, o.vel.y);
            coh.add(o.x, o.y);
            neighborCount++;
        }

        float speed = unit.speed();

        if(neighborCount > 0){
            ali.scl(1f / neighborCount).sub(unit.vel.x, unit.vel.y);
            coh.scl(1f / neighborCount).sub(unit.x, unit.y);
        }else{
            ali.setZero();
            coh.setZero();
        }

        normalize(sep, speed);
        normalize(ali, speed);
        normalize(coh, speed);

        steer.add(sep.x * wSep, sep.y * wSep);
        if(full) steer.add(ali.x * wAli, ali.y * wAli);
        steer.add(coh.x * wCoh, coh.y * wCoh);
    }

    private void seekWander(BoidData d){
        float speed = unit.speed();
        BoidGroup g = d.group;

        if(g != null && g.leader == unit){
            goal.set(g.patrolX - unit.x, g.patrolY - unit.y);
        }else if(hasArea(d)){
            float cx = areaX(d), cy = areaY(d), r = areaRadius(d) * 0.8f;
            d.wanderAngle = (d.wanderAngle + groupPatrolSpeed * (Time.delta / 60f)) % 360f;
            goal.set((cx + Angles.trnsx(d.wanderAngle, r)) - unit.x, (cy + Angles.trnsy(d.wanderAngle, r)) - unit.y);
        }else{
            d.wanderAngle += Mathf.random(-wanderJitter, wanderJitter) * Time.delta;
            goal.set(Angles.trnsx(d.wanderAngle, wanderDistance), Angles.trnsy(d.wanderAngle, wanderDistance));
        }

        normalize(goal, speed);
        steer.add(goal.x * wSeek, goal.y * wSeek);
    }

    private void seekLeader(BoidData d){
        Unit l = d.leader;
        if(l == null || l.dead() || !l.isValid()){
            d.state = BoidState.WANDER;
            d.leader = null;
            return;
        }

        float speed = unit.speed();
        float dx = unit.x - l.x, dy = unit.y - l.y;
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        float desired = l.hitSize + followRadius;

        float rx, ry;
        if(dist > 0.001f){
            rx = dx / dist;
            ry = dy / dist;
        }else{
            rx = 1f;
            ry = 0f;
        }

        float err = Mathf.clamp((dist - desired) / Math.max(desired, 1f), -1f, 1f);

        steer.add(-ry * wOrbit * speed, rx * wOrbit * speed);
        steer.add(-rx * err * speed, -ry * err * speed);
    }

    private void seekTarget(BoidData d){
        float tx, ty, half;

        if(d.buildingTarget != null){
            Building b = d.buildingTarget;
            if(!b.isValid()){
                d.state = BoidState.WANDER;
                d.buildingTarget = null;
                return;
            }
            tx = b.x;
            ty = b.y;
            half = b.block.size * Vars.tilesize / 2f;
        }else if(d.unitTarget != null){
            Unit u = d.unitTarget;
            if(u.dead() || !u.isValid()){
                d.state = BoidState.WANDER;
                d.unitTarget = null;
                return;
            }
            tx = u.x;
            ty = u.y;
            half = u.hitSize / 2f;
        }else{
            d.state = BoidState.WANDER;
            return;
        }

        float speed = unit.speed();
        float dist = Mathf.dst(unit.x, unit.y, tx, ty);
        float ang = Angles.angle(unit.x, unit.y, tx, ty);

        if(dist < half + unit.hitSize / 2f + 8f){
            ang += 90f;
        }

        goal.set(Angles.trnsx(ang, speed), Angles.trnsy(ang, speed));
        steer.add(goal.x * wSeek, goal.y * wSeek);
    }

    private void fleeDanger(BoidData d, float weight){
        float speed = unit.speed();
        for(int i = 0; i < d.dangers.size; i++){
            DangerIndex.Danger dg = d.dangers.get(i);
            float dist = Mathf.dst(unit.x, unit.y, dg.x, dg.y);
            if(dist >= dg.range) continue;

            float strength = 1f - dist / Math.max(dg.range, 0.001f);
            if(dist < 0.001f){
                tmp.set(1f, 0f);
            }else{
                tmp.set(unit.x - dg.x, unit.y - dg.y).scl(1f / dist);
            }
            steer.add(tmp.x * strength * weight * speed, tmp.y * strength * weight * speed);
        }
    }

    private void ram(BoidData d){
        if(d.spent) return;

        float tx, ty, half;

        if(d.buildingTarget != null){
            Building b = d.buildingTarget;
            if(!b.isValid()){
                d.state = BoidState.WANDER;
                d.buildingTarget = null;
                return;
            }
            tx = b.x;
            ty = b.y;
            half = b.block.size * Vars.tilesize / 2f;
        }else if(d.unitTarget != null){
            Unit u = d.unitTarget;
            if(u.dead() || !u.isValid()){
                d.state = BoidState.WANDER;
                d.unitTarget = null;
                return;
            }
            tx = u.x;
            ty = u.y;
            half = u.hitSize / 2f;
        }else{
            d.state = BoidState.WANDER;
            return;
        }

        float reach = unit.hitSize / 2f + half + 4f;

        if(Mathf.dst(unit.x, unit.y, tx, ty) <= reach){
            return;
        }

        tmp.set(tx - unit.x, ty - unit.y);
        if(tmp.len2() > 0.0001f){
            tmp.setLength(unit.speed());
            unit.movePref(tmp);
            unit.lookAt(tmp.angle());
        }
    }
    private static void normalize(Vec2 v, float length){
        if(v.len2() > 0.0001f && !v.isNaN()){
            v.setLength(length);
        }else{
            v.setZero();
        }
    }
}
