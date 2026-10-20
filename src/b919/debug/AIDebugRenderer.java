package b919.debug;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import b919.Main;
import b919.ai.boid.BoidData;
import b919.ai.boid.BoidMassAI;
import b919.ai.DangerIndex;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

public class AIDebugRenderer{

    public static void init(){
        Events.run(Trigger.update, () -> {
            if(Core.input.keyTap(KeyCode.f8)) Main.debug = !Main.debug;
        });

        Events.run(Trigger.draw, AIDebugRenderer::draw);
    }

    private static void draw(){
        if(!Main.debug) return;

        for(Unit u : Groups.unit){
            if(!(u.controller() instanceof BoidMassAI)) continue;

            BoidData d = BoidData.get(u);
            if(d == null) continue;

            Draw.z(Layer.overlayUI);
            Lines.stroke(1f);

            Draw.color(d.state.color, 0.65f);
            Lines.circle(u.x, u.y, BoidMassAI.perceptionRadius);

            Draw.color(Pal.accent, 0.65f);
            Lines.circle(u.x, u.y, BoidMassAI.separationRadius);

            Draw.color(d.state.color, 0.35f);
            Lines.circle(u.x, u.y, BoidMassAI.groupRadius);

            if(d.group != null){
                Draw.color(Color.valueOf("7bd88f"), 0.5f);
                Lines.circle(d.group.roamX, d.group.roamY, d.group.roamRadius);
                Lines.circle(d.group.patrolX, d.group.patrolY, 6f);
            }else if(d.hasHome){
                Draw.color(Color.valueOf("7bd88f"), 0.5f);
                Lines.circle(d.homeX, d.homeY, d.roamRadius);
            }

            Draw.color(Pal.remove, 0.3f);
            for(int i = 0; i < d.dangers.size; i++){
                DangerIndex.Danger dg = d.dangers.get(i);
                Lines.circle(dg.x, dg.y, dg.range);
            }

            Draw.color(d.state.color, 0.9f);
            if(d.buildingTarget != null && d.buildingTarget.isValid()){
                Lines.line(u.x, u.y, d.buildingTarget.x, d.buildingTarget.y);
            }
            if(d.unitTarget != null && d.unitTarget.isValid()){
                Lines.line(u.x, u.y, d.unitTarget.x, d.unitTarget.y);
            }
            if(d.leader != null && d.leader.isValid()){
                Lines.line(u.x, u.y, d.leader.x, d.leader.y);
            }

            float massTenths = Mathf.round(d.mass * 10f) / 10f;
            Drawf.text(d.state.label + " m" + massTenths + " c" + d.chargedCount, u.x, u.y + u.hitSize + 4f, Color.white);
        }

        Draw.reset();
    }
}
