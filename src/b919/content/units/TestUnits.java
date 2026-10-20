package b919.content.units;

import b919.ai.boid.BoidMassAI;
import b919.world.DZUnitPreset;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.MechUnit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class TestUnits{
    public static UnitType AlfredoTheMass, RicardoTheArtillery;

    public static void load(){
        AlfredoTheMass = new DZUnitPreset("alfredo_the_mass"){{
            speed = 4.5f;
            accel = 0.08f;
            drag = 0.04f;
            rotateSpeed = 2.5f;
            flying = true;
            hitSize = 2f;
            health = 120f;
            armor = 0f;
            omniMovement = true;
            circleTarget = true;
            circleTargetRadius = 60f;
            targetAir = true;
            targetGround = true;

            remains = 4;
            healthBars = new float[]{40f, 25f};
            armorAddition = new float[]{1f, 2f};

            weapons.add(new Weapon(){{
                shootOnDeath = true;
                targetUnderBlocks = false;
                reload = 24f;
                shootCone = 180f;
                ejectEffect = Fx.none;
                shootSound = mindustry.gen.Sounds.explosionCrawler;
                shootSoundVolume = 0.4f;
                x = shootY = 0f;
                mirror = false;
                bullet = new BulletType(){{
                    collidesTiles = false;
                    collides = false;
                    rangeOverride = 25f;
                    hitEffect = Fx.pulverize;
                    speed = 0f;
                    splashDamageRadius = 42f;
                    instantDisappear = true;
                    splashDamage = 90f;
                    killShooter = true;
                    hittable = false;
                    collidesAir = true;
                }};
            }});

            aiController = BoidMassAI::new;
        }};
        RicardoTheArtillery = new UnitType("ricardo_the_mass"){{// unfinished
            constructor = MechUnit::create;
            speed = 4f;
            accel = 0.08f;
        }};
    }
}
