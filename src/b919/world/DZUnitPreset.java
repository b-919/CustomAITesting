package b919.world;

import arc.struct.IntMap;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
//Ignore this class is for debug with DZ unit presets still don't know how this fully works so can be buggy

public class DZUnitPreset extends UnitType{
    public int remains = 0;
    public float[] healthBars = {};
    public float[] armorAddition = {};

    public static class UnitArmorData{
        public float[] subHealths;
        public float lastHealth;

        public UnitArmorData(float[] subHealths, float lastHealth){
            this.subHealths = subHealths;
            this.lastHealth = lastHealth;
        }
    }

    protected static final IntMap<UnitArmorData> armorMap = new IntMap<>();

    public DZUnitPreset(String name){
        super(name);
        outlineRadius = 0;
        drawCell = false;
    }

    @Override
    public Unit spawn(Team team, float x, float y){
        Unit unit = super.spawn(team, x, y);
        if(unit != null && healthBars != null && healthBars.length > 0){
            float[] initHealths = new float[healthBars.length];
            System.arraycopy(healthBars, 0, initHealths, 0, healthBars.length);
            armorMap.put(unit.id, new UnitArmorData(initHealths, unit.health));
        }
        return unit;
    }

    @Override
    public void update(Unit unit){
        super.update(unit);

        if(unit.dead() || !unit.isAdded()){
            armorMap.remove(unit.id);
            return;
        }

        UnitArmorData data = armorMap.get(unit.id);
        if(data == null){
            if(healthBars == null || healthBars.length == 0) return;
            float[] initHealths = new float[healthBars.length];
            System.arraycopy(healthBars, 0, initHealths, 0, healthBars.length);
            armorMap.put(unit.id, new UnitArmorData(initHealths, unit.health));
            return;
        }

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
                    }else{
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

    public static UnitArmorData armorData(Unit unit){
        return unit == null ? null : armorMap.get(unit.id);
    }

    public static float extraHealth(Unit unit){
        UnitArmorData data = armorData(unit);
        if(data == null) return 0f;
        float sum = 0f;
        for(float f : data.subHealths) sum += Math.max(f, 0f);
        return sum;
    }

    public static float effectiveArmor(Unit unit){
        if(unit == null) return 0f;
        float total = unit.type.armor;
        UnitArmorData data = armorMap.get(unit.id);
        if(data != null && unit.type instanceof DZUnitPreset preset){
            for(int i = 0; i < data.subHealths.length; i++){
                if(data.subHealths[i] > 0f && i < preset.armorAddition.length){
                    total += preset.armorAddition[i];
                }
            }
        }
        return total;
    }
}
