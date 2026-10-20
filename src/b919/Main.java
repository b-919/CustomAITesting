package b919;

import arc.util.Log;
import b919.content.TestBlock;
import b919.content.units.TestUnits;
import b919.debug.AIDebugRenderer;
import mindustry.mod.Mod;

public class Main extends Mod{

    public static boolean debug = true; // set in false to disable all the silly renderers

    public Main(){
        Log.info("Arroz.");
    }

    @Override
    public void loadContent(){
        TestUnits.load();
        TestBlock.load();
        AIDebugRenderer.init();
        Log.info("con pollo.");
    }
}
