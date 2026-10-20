package deadzone.world.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Geometry;
import arc.math.geom.Rect;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.*;
import deadzone.world.DZPartParms;
import deadzone.world.abilities.AbilityTextures;
import deadzone.world.infection.bullets.*;
import deadzone.world.weapons.parts.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.blocks.environment.Floor;

import static mindustry.Vars.*;

public class UnitPreset extends UnitType {
    public int remains = 0;
    public TextureRegion[] abilityRegions = new TextureRegion[AbilityTextures.values().length];

    public boolean lookAtMouse = false;

    public TextureRegion damagedRegion, severelyDamagedRegion;
    public TextureRegion[][] armorRegions;

    public float[] healthBars = {};
    public float[] armorAddition = {};

    public static class UnitArmorData {
        public float[] subHealths;
        public float lastHealth;

        public UnitArmorData(float[] subHealths, float lastHealth) {
            this.subHealths = subHealths;
            this.lastHealth = lastHealth;
        }
    }

    protected transient IntMap<UnitArmorData> unitArmorMap = new IntMap<>();

    Effect despawn;

    public Rect[][] treadStrips;
    public transient TextureRegion chunkReg = new TextureRegion(), treadChainRegion;

    public TextureRegion kneeRegion, footRegion;
    public static final IntSet groundedLegs = new IntSet();

    public @Nullable Effect stepEffect = Fx.none;
    public float mechBaseLayer = Layer.groundUnit - 0.002f;
    public float mechLegElevation = 4;

    public UnitPreset(String name){
        super(name);
        outlineRadius = 0;
        drawCell = false;

        weapons.add(new Weapon("death"){{
            controllable = false;
            noAttack = true;
            shootOnDeath = true;
            x = y = 0;
            top = false;
            mirror = false;
            shootCone = inaccuracy = 360;
            bullet = new BulletType(0, 0){{
                instantDisappear = true;
                shootEffect = smokeEffect = despawnEffect = Fx.none;
                collides = false;

                fragBullets = remains;
                fragLifeMin = 30f;
                fragLifeMax = 25f;
                fragBullet = new InfectionRemainsBulletType(6, 0){{
                    despawnEffect = despawn;
                    collides = false;
                    suffix = "dz-infection-remains";
                }};
            }};
        }});
    }

    @Override
    public Unit spawn(Team team, float x, float y) {
        Unit unit = super.spawn(team, x, y);
        if(unit != null && healthBars != null && healthBars.length > 0){
            float[] initHealths = new float[healthBars.length];
            System.arraycopy(healthBars, 0, initHealths, 0, healthBars.length);
            unitArmorMap.put(unit.id, new UnitArmorData(initHealths, unit.health));
        }
        return unit;
    }

    @Override
    public void load(){
        super.load();
        mineLaserRegion = Core.atlas.find("dz-minelaser");
        mineLaserEndRegion = Core.atlas.find("dz-minelaser-end");
        treadChainRegion = Core.atlas.find(name + "-treads-chain");
        kneeRegion = Core.atlas.find(name + "-knee");
        footRegion = Core.atlas.find(name + "-foot", Core.atlas.find(name + "-leg"));

        damagedRegion = Core.atlas.find(name + "-1", region);
        severelyDamagedRegion = Core.atlas.find(name + "-2", region);

        if(healthBars != null && healthBars.length > 0){
            armorRegions = new TextureRegion[healthBars.length][3];
            for(int i = 0; i < healthBars.length; i++){
                String armorBase = name + "-armor-" + (i + 1);
                armorRegions[i][0] = Core.atlas.find(armorBase);
                armorRegions[i][1] = Core.atlas.find(armorBase + "-1", armorRegions[i][0]);
                armorRegions[i][2] = Core.atlas.find(armorBase + "-2", armorRegions[i][0]);
            }
        }

        for (AbilityTextures type : AbilityTextures.values()) {
            abilityRegions[type.ordinal()] = Core.atlas.find(name + "-" + type.name());
        }
    }

    @Override
    public void applyColor(Unit unit){
        Draw.color();
        if(healFlash){
            Tmp.c1.set(Color.white).lerp(healColor, Mathf.clamp(unit.healTime - unit.hitTime));
        }
        Draw.mixcol(Tmp.c1, Math.max(unit.hitTime, !healFlash ? 0f : Mathf.clamp(unit.healTime)));

        if(unit.drownTime > 0 && unit.lastDrownFloor != null){
            Draw.mixcol(Tmp.c1.set(unit.lastDrownFloor.mapColor).mul(0.83f), unit.drownTime * 0.9f);
        }
        if(renderer != null && renderer.overlays != null){
            renderer.overlays.checkApplySelection(unit);
        }
    }

    @Override
    public void drawBody(Unit unit) {
        applyColor(unit);
        TextureRegion drawReg;

        if (unit.health < unit.maxHealth * 0.3f) {
            drawReg = severelyDamagedRegion != null && severelyDamagedRegion.found() ? severelyDamagedRegion : region;
        } else if (unit.health < unit.maxHealth * 0.8f) {
            drawReg = damagedRegion != null && damagedRegion.found() ? damagedRegion : region;
        } else {
            drawReg = region;
        }

        if (drawReg != null && drawReg.found()) {
            Draw.rect(drawReg, unit.x, unit.y, unit.rotation - 90);
        }

        UnitArmorData data = unitArmorMap.get(unit.id);
        if(data != null && armorRegions != null){
            for(int i = 0; i < data.subHealths.length; i++){
                if(data.subHealths[i] > 0f && i < armorRegions.length){
                    float healthFraction = data.subHealths[i] / healthBars[i];
                    TextureRegion armorReg;

                    if(healthFraction < 0.3f && armorRegions[i][2] != null && armorRegions[i][2].found()){
                        armorReg = armorRegions[i][2]; // Severely damaged (-2)
                    } else if(healthFraction < 0.7f && armorRegions[i][1] != null && armorRegions[i][1].found()){
                        armorReg = armorRegions[i][1]; // Damaged (-1)
                    } else {
                        armorReg = armorRegions[i][0]; // Normal
                    }

                    if(armorReg != null && armorReg.found()){
                        Draw.rect(armorReg, unit.x, unit.y, unit.rotation - 90);
                    }
                }
            }
        }
        drawWeapons(unit);
    }

    @Override
    public void update(Unit unit){
        super.update(unit);

        if(unit.dead() || !unit.isAdded()){
            unitArmorMap.remove(unit.id);
            return;
        }

        // why do i refuse to use unit entities or whatever
        // this is miserable, someone hit me on the chrome dome with a frying pan

        UnitArmorData data = unitArmorMap.get(unit.id);
        if(data != null){
            if(unit.health < data.lastHealth){
                float rawDamageTaken = data.lastHealth - unit.health;
                unit.health = data.lastHealth;

                float totalArmor = armor;
                for(int i = 0; i < data.subHealths.length; i++){
                    if(data.subHealths[i] > 0f && i < armorAddition.length){
                        totalArmor += armorAddition[i];
                    }
                }

                float netDamage = Math.max(1f, rawDamageTaken - totalArmor);

                for(int i = 0; i < data.subHealths.length; i++){
                    if(data.subHealths[i] > 0f){
                        if(netDamage >= data.subHealths[i]){
                            netDamage -= data.subHealths[i];
                            data.subHealths[i] = 0f;
                        } else {
                            data.subHealths[i] -= netDamage;
                            netDamage = 0f;
                            break;
                        }
                    }
                }

                if(netDamage > 0f){
                    unit.health -= netDamage;
                    if(unit.health <= 0f){
                        unit.kill();
                    }
                }
            }
            data.lastHealth = unit.health;
        }

        if(lookAtMouse && unit.isPlayer()){
            float targetAngle = Angles.angle(unit.x, unit.y, unit.aimX, unit.aimY);
            unit.rotation = Angles.moveToward(unit.rotation, targetAngle, rotateSpeed);
        }

        // im still not sure why this exists

        if(unit instanceof Tankc && unit.vel.len() > 0.001f){
            float rad = unit.rotation * Mathf.degRad;
            float cos = Mathf.cos(rad);
            float sin = Mathf.sin(rad);

            float forwardVel = unit.vel.x * cos + unit.vel.y * sin;
            float sideVel = -unit.vel.x * sin + unit.vel.y * cos;

            sideVel *= 0.1f;

            unit.vel.x = forwardVel * cos - sideVel * sin;
            unit.vel.y = forwardVel * sin + sideVel * cos;
        }
    }

    @Override
    public void createIcons(MultiPacker packer){
        super.createIcons(packer);
        if(sample instanceof Tankc){
            PixmapRegion pr = packer.get(name + "-treads");
            if(pr != null && treadRects.length == 0){
                setupTreads(pr);
            }
        }
    }

    // stabu
    protected void setupTreads(PixmapRegion pr){
        int w = pr.width, h = pr.height, out = outlineColor.rgba();
        boolean[][] solid = new boolean[w][h];

        for(int x = 0; x < w; x++)
            for(int y = 0; y < h; y++){
                int c = pr.pixmap.get(pr.x + x, pr.y + y);
                solid[x][y] = (c >>> 24) > 16 && c != out;
            }

        Seq<Seq<Rect>> islands = new Seq<>();

        for(int x = 0; x < w; x++)
            for(int y = 0; y < h; y++){
                if(!solid[x][y]) continue;

                Seq<Rect> strips = new Seq<>();
                IntSeq q = IntSeq.with(x, y);
                solid[x][y] = false;

                int[] iMin = new int[w], iMax = new int[w];
                java.util.Arrays.fill(iMin, h);
                java.util.Arrays.fill(iMax, -1);

                while(!q.isEmpty()){
                    int cy = q.pop(), cx = q.pop();
                    iMin[cx] = Math.min(iMin[cx], cy);
                    iMax[cx] = Math.max(iMax[cx], cy);

                    for(int d = 0; d < 4; d++){
                        int nx = cx + Geometry.d4x[d], ny = cy + Geometry.d4y[d];
                        if(nx >= 0 && nx < w && ny >= 0 && ny < h && solid[nx][ny]){
                            solid[nx][ny] = false;
                            q.add(nx, ny);
                        }
                    }
                }

                Rect last = null;
                for(int cx = 0; cx < w; cx++){
                    if(iMax[cx] == -1) continue;
                    int sy = iMin[cx], sh = iMax[cx] - sy + 1;

                    if(last != null && last.y == sy && last.height == sh) last.width++;
                    else strips.add(last = new Rect(cx, sy, 1, sh));
                }
                islands.add(strips);
            }

        treadRects = new Rect[islands.size];
        treadStrips = new Rect[islands.size][];

        for(int i = 0; i < islands.size; i++){
            treadStrips[i] = islands.get(i).toArray(Rect.class);
            treadRects[i] = new Rect(treadStrips[i][0]);
            for(Rect r : treadStrips[i]) treadRects[i].merge(r);
            treadRects[i].move(-w / 2f, -h / 2f);
        }
    }

    // also stabu
    @Override
    public <T extends Unit&Tankc> void drawTank(T unit){
        if(treadChainRegion == null || !treadChainRegion.found() || treadStrips == null){
            super.drawTank(unit);
            return;
        }

        applyColor(unit);
        Draw.rect(treadRegion, unit.x, unit.y, unit.rotation - 90);

        float s = Draw.scl;
        float rot = unit.rotation - 90;
        float progress = unit.treadTime() * 2f;

        for(int i = 0; i < treadRects.length; i++){
            int trackY = Math.round(treadRects[i].y + treadRegion.height / 2f);
            int trackH = Math.round(treadRects[i].height);
            float offset = (progress % trackH + trackH) % trackH;

            for(Rect strip : treadStrips[i]){
                float dy = trackY + (strip.y - trackY + offset) % trackH;
                float dh = Math.min(strip.height, trackY + trackH - dy);

                float ox = (strip.x + strip.width / 2f - treadRegion.width / 2f) * s;
                float oy = (treadRegion.height / 2f - strip.y) * s;

                drawChunk(strip.x, dy, strip.width, dh, ox, oy - dh / 2f * s, rot, unit);

                if(dh < strip.height){
                    float rh = strip.height - dh;
                    drawChunk(strip.x, trackY, strip.width, rh, ox, oy - dh * s - rh / 2f * s, rot, unit);
                }
            }
        }
    }

    // stabu
    protected void drawChunk(float sx, float sy, float sw, float sh, float ox, float oy, float rot, Unit unit) {
        chunkReg.set(treadChainRegion, (int)sx, (int)sy, (int)sw, (int)sh);
        Tmp.v1.set(ox, oy).rotate(rot);
        Draw.rect(chunkReg, unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, sw * Draw.scl, sh * Draw.scl, rot);
    }

    //me (horrendous visuals)
    @Override
    public void drawMiningBeam(Unit unit, float px, float py){
        if(!unit.mining()) return;
        float prevZ = Draw.z();
        float swingScl = 12f, swingMag = tilesize / 8f;
        float flashScl = 10f;

        float ex = unit.mineTile.worldx() + Mathf.sin(Time.time + 48, swingScl, swingMag);
        float ey = unit.mineTile.worldy() + Mathf.sin(Time.time + 48, swingScl + 2f, swingMag);

        Draw.z(Layer.bullet + 0.1f);
        Draw.color(Color.valueOf("cadae6"), Color.white, Mathf.absin(Time.time, flashScl, 0.5f));
        Draw.alpha(Renderer.unitLaserOpacity);
        Drawf.laser(mineLaserRegion, mineLaserEndRegion, px, py, ex, ey, 0.75f + Mathf.absin(5, 0.25f));
        Drawf.light(px, py, ex, ey, 8f + Mathf.absin(5, 0.25f), Color.valueOf("6db9f5"), 0.75f);

        Mathf.randomSeed(unit.id);
        Fx.colorSpark.at(ex, ey, Mathf.random(360), Color.valueOf("494865").a(0.5f));

        Draw.color();
        Draw.reset();
        Draw.z(prevZ);
    }

    // me(i think?)
    public boolean hasModPart(){
        if(parts != null){
            for(int i = 0; i < parts.size; i++){
                if(parts.get(i) instanceof ShadowPart) return true;
            }
        }
        if(weapons != null){
            for(int i = 0; i < weapons.size; i++){
                Weapon w = weapons.get(i);
                if(w.parts != null){
                    for(int j = 0; j < w.parts.size; j++){
                        if(w.parts.get(j) instanceof ShadowPart) return true;
                    }
                }
            }
        }
        return false;
    }

    // me
    // this is a lot of god knows what, it just makes the feet go "up n down"
    @Override
    public void drawMech(Mechc mech) {
        Unit unit = (Unit) mech;
        Draw.reset();

        float z = Draw.z();
        Draw.z(mechBaseLayer);

        float e = unit.elevation;
        Floor floor = unit.isFlying() ? Blocks.air.asFloor() : unit.floorOn();

        if (floor.isLiquid) {
            Draw.color(Color.white, floor.mapColor, 0.5f);
        }

        float cos = Mathf.lerp(Mathf.cos(mech.walkExtend(true), 2f / Mathf.PI, 1f), 0f, e);
        float extension = Mathf.lerp(mech.walkExtend(false), 0, e);
        float boostTrns = e * 2f;
        float stepHeight = 4f * (1f - e);

        int sideIndex = 0;
        for (int i : Mathf.signs) {
            sideIndex++;
            int legKey = unit.id * 2 + sideIndex;

            Draw.mixcol(Tmp.c1.set(mechLegColor).lerp(Color.white, Mathf.clamp(unit.hitTime)),
                    Math.max(Math.max(0, i * extension / mechStride), unit.hitTime));

            float width = hitSize / 2f * -i, length = unit.type.mechStride * 1.35f;

            float cx = unit.x + Angles.trnsx(mech.baseRotation(), length, width),
                    cy = unit.y + Angles.trnsy(mech.baseRotation(), length, width);

            float footForward = extension * i;

            float liftPhase = Math.max(0f, cos * i);
            float stepLift = liftPhase * stepHeight;

            float footX = unit.x + Angles.trnsx(mech.baseRotation(), footForward - boostTrns + stepLift * 0.2f, -boostTrns * i);
            float footY = unit.y + Angles.trnsy(mech.baseRotation(), footForward - boostTrns + stepLift * 0.2f, -boostTrns * i);

            boolean isGroundedNow = stepLift <= 0.05f;
            if (isGroundedNow && !groundedLegs.contains(legKey) && unit.isGrounded() && Math.abs(extension) > 0.01f) {
                groundedLegs.add(legKey);
                stepEffect.at(cx, cy, mech.baseRotation());
            } else if (!isGroundedNow) {
                groundedLegs.remove(legKey);
            }

            TextureRegion renderFoot = (footRegion != null && footRegion.found()) ? footRegion : legRegion;
            float heightScale = 1f + (stepLift / Math.max(stepHeight, 0.001f)) * 0.2f;

            Draw.rect(renderFoot,
                    footX, footY,
                    renderFoot.width * renderFoot.scl() * i * heightScale,
                    renderFoot.height * renderFoot.scl() * heightScale,
                    mech.baseRotation() - 90 + 35f * i * e);
        }

        Draw.mixcol(Color.white, unit.hitTime);
        if (unit.lastDrownFloor != null) {
            Draw.color(Color.white, Tmp.c1.set(unit.lastDrownFloor.mapColor).mul(0.83f), unit.drownTime * 0.9f);
        } else {
            Draw.color(Color.white);
        }
        Draw.rect(baseRegion, unit, mech.baseRotation() - 90);

        for (int i : Mathf.signs) {
            Draw.mixcol(Tmp.c1.set(mechLegColor).lerp(Color.white, Mathf.clamp(unit.hitTime)),
                    Math.max(Math.max(0, i * extension / mechStride), unit.hitTime));

            float footForward = extension * i;
            float kneeX = unit.x + Angles.trnsx(mech.baseRotation(), footForward * 0.6f - boostTrns, i * 5f);
            float kneeY = unit.y + Angles.trnsy(mech.baseRotation(), footForward * 0.6f - boostTrns, i * 5f);

            if (kneeRegion != null && kneeRegion.found()) {
                Draw.rect(kneeRegion,
                        kneeX, kneeY,
                        kneeRegion.width * kneeRegion.scl() * i,
                        kneeRegion.height * kneeRegion.scl(),
                        mech.baseRotation() - 90);
            }
        }

        Draw.z(z);
        Draw.mixcol();
    }


    @Override
    public void drawShadow(Unit unit){
    }

    // this.
    public void updatePrams(Unit unit){
        DZPartParms.randParams.set(
                unit.id
        );
    }

    // stabu
    @Override
    public void drawSoftShadow(Unit unit, float alpha){
        if(hasModPart()){
            float rad = 1.6f;
            float size = Math.max(region.width, region.height) * region.scl() * softShadowScl;
            float fX = unit.x;
            float fY = unit.y;
            float fRot = unit.rotation;
            float fXscl = Draw.xscl;
            float fYscl = Draw.yscl;
        }else{
            super.drawSoftShadow(unit, alpha);
        }
    }

    @Override
    public void draw(Unit unit) {
        if(parts.size > 0) {
            updatePrams(unit);
            ImprovedRegionPartParams.velocityParams.set(unit);
        };

        super.draw(unit);
    }

    @Override
    public void drawWeapons(Unit unit){
        if(parts.size > 0) {
            updatePrams(unit);
            ImprovedRegionPartParams.velocityParams.set(unit);
        };
        super.drawWeapons(unit);
    }
}