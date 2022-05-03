//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.BirthLog;
import edu.cwru.sepia.environment.model.history.DamageLog;
import edu.cwru.sepia.environment.model.history.DeathLog;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.DistanceMetrics;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class combatAgent extends Agent {
    private static final long serialVersionUID = 1L;
    private Map<Integer, Action> unitOrders;
    private int[] enemies;
    private boolean wanderwhenidle;
    private int lastStepMovedIn;

    public combatAgent(int var1, String[] var2) {
        super(var1);
        if (var2 != null && var2.length != 0) {
            this.verbose = Boolean.parseBoolean(var2[2]);
            String[] var3 = var2[0].split(" ");
            this.enemies = new int[var3.length];

            for(int var4 = 0; var4 < this.enemies.length; ++var4) {
                this.enemies[var4] = Integer.parseInt(var3[var4]);
            }

            this.wanderwhenidle = Boolean.parseBoolean(var2[1]);
        } else {
            this.setDefaults();
        }

    }

    public combatAgent(int var1, int[] var2, boolean var3, boolean var4) {
        super(var1);
        this.enemies = new int[var2.length];
        System.arraycopy(var2, 0, this.enemies, 0, var2.length);
        this.wanderwhenidle = var3;
        this.verbose = var4;
    }

    public combatAgent(int var1) {
        super(var1);
        this.setDefaults();
    }

    private void setDefaults() {
        this.verbose = false;
        this.wanderwhenidle = false;
        this.enemies = null;
    }

    public Map<Integer, Action> initialStep(StateView var1, HistoryView var2) {
        if (this.enemies == null) {
            int var3 = 0;
            Integer[] var4 = var1.getPlayerNumbers();
            int var5 = var4.length;

            int var6;
            for(var6 = 0; var6 < var5; ++var6) {
                Integer var7 = var4[var6];
                if (var7 != this.getPlayerNumber()) {
                    ++var3;
                }
            }

            this.enemies = new int[var3];
            int var11 = 0;
            Integer[] var13 = var1.getPlayerNumbers();
            var6 = var13.length;

            for(int var14 = 0; var14 < var6; ++var14) {
                Integer var8 = var13[var14];
                if (var8 != this.getPlayerNumber()) {
                    this.enemies[var11++] = var8;
                }
            }
        }

        this.unitOrders = new HashMap();
        Iterator var9 = var1.getUnitIds(this.playernum).iterator();

        while(var9.hasNext()) {
            Integer var12 = (Integer)var9.next();
            this.unitOrders.put(var12, null);
        }

        this.doAggro(var1);
        Map var10 = this.getAction(var1);
        this.lastStepMovedIn = var1.getTurnNumber();
        return var10;
    }

    public Map<Integer, Action> middleStep(StateView var1, HistoryView var2) {
        for(int var3 = this.lastStepMovedIn; var3 < var1.getTurnNumber(); ++var3) {
            Iterator var4 = var2.getBirthLogs(var3).iterator();

            while(var4.hasNext()) {
                BirthLog var5 = (BirthLog)var4.next();
                if (this.playernum == var5.getController()) {
                    this.unitOrders.put(var5.getNewUnitID(), null);
                }
            }

            LinkedList var11 = new LinkedList();
            LinkedList var12 = new LinkedList();
            Iterator var6 = var2.getDeathLogs(var3).iterator();

            Action var10;
            while(var6.hasNext()) {
                DeathLog var7 = (DeathLog)var6.next();
                if (this.playernum == var7.getController()) {
                    var11.add(var7.getDeadUnitID());
                }

                Iterator var8 = this.unitOrders.entrySet().iterator();

                while(var8.hasNext()) {
                    Entry var9 = (Entry)var8.next();
                    if (var9.getValue() != null) {
                        var10 = Action.createCompoundAttack((Integer)var9.getKey(), var7.getDeadUnitID());
                        if (var10.equals(var9.getValue())) {
                            var12.add(var9.getKey());
                        }
                    }
                }
            }

            var6 = var12.iterator();

            Integer var13;
            while(var6.hasNext()) {
                var13 = (Integer)var6.next();
                this.unitOrders.put(var13, null);
            }

            var6 = var11.iterator();

            while(var6.hasNext()) {
                var13 = (Integer)var6.next();
                this.unitOrders.remove(var13);
            }

            if (this.verbose) {
                var6 = var2.getDamageLogs(var3).iterator();

                while(var6.hasNext()) {
                    DamageLog var14 = (DamageLog)var6.next();
                    if (var14.getAttackerController() == this.playernum) {
                        this.writeLineVisual(var14.getAttackerID() + " hit " + var14.getDefenderID() + " for " + var14.getDamage() + " damage");
                    }

                    if (var14.getDefenderController() == this.playernum) {
                        this.writeLineVisual(var14.getDefenderID() + " was hit by " + var14.getAttackerID() + " for " + var14.getDamage() + " damage");
                    }
                }
            }

            var6 = var2.getCommandFeedback(this.playernum, var3).values().iterator();

            while(var6.hasNext()) {
                ActionResult var15 = (ActionResult)var6.next();
                if (var15.getFeedback() != ActionFeedback.INCOMPLETE) {
                    Action var16 = var15.getAction();
                    int var17 = var16.getUnitId();
                    var10 = (Action)this.unitOrders.get(var17);
                    if (var16.equals(var10)) {
                        this.unitOrders.put(var17, null);
                    }
                }
            }
        }

        this.doAggro(var1);
        this.lastStepMovedIn = var1.getTurnNumber();
        return this.getAction(var1);
    }

    public void terminalStep(StateView var1, HistoryView var2) {
        this.lastStepMovedIn = var1.getTurnNumber();
    }

    private Map<Integer, Action> getAction(StateView var1) {
        HashMap var2 = new HashMap();
        Iterator var3 = this.unitOrders.entrySet().iterator();

        while(var3.hasNext()) {
            Entry var4 = (Entry)var3.next();
            if (this.verbose) {
                this.writeLineVisual("Combat Agent for plr " + this.playernum + "'s order: " + var4.getKey() + " is to use " + var4.getValue());
            }

            if (var4.getValue() != null) {
                var2.put(var4.getKey(), var4.getValue());
            }
        }

        return var2;
    }

    private void doAggro(StateView var1) {
        Iterator var2 = this.unitOrders.entrySet().iterator();

        while(true) {
            Entry var3;
            do {
                if (!var2.hasNext()) {
                    return;
                }

                var3 = (Entry)var2.next();
            } while(var3.getValue() != null);

            UnitView var4 = var1.getUnit((Integer)var3.getKey());
            int var5 = var4.getXPosition();
            int var6 = var4.getYPosition();
            int var7 = var4.getTemplateView().getSightRange();
            int[] var8 = this.enemies;
            int var9 = var8.length;

            for(int var10 = 0; var10 < var9; ++var10) {
                int var11 = var8[var10];
                List var12 = var1.getUnitIds(var11);
                int[] var13 = this.findNearest(var1, (Integer)var3.getKey(), var11);
                if (var7 > var13[1] && var13[0] >= 0) {
                    double var14 = Math.random();
                    if (var14 < 0.75D) {
                        this.unitOrders.put((Integer)var3.getKey(), Action.createCompoundAttack((Integer)var3.getKey(), var13[0]));
                    } else {
                        int var16 = (int)Math.floor(Math.random() * (double)var12.size());
                        this.unitOrders.put((Integer)var3.getKey(), Action.createCompoundAttack((Integer)var3.getKey(), (Integer)var12.get(var16)));
                    }
                }
            }
        }
    }

    protected int[] findNearest(StateView var1, int var2, int var3) {
        int[] var4 = new int[]{-1, 2147483647};
        List var5 = var1.getUnitIds(var3);
        UnitView var6 = var1.getUnit(var2);
        if (var6 == null) {
            return var4;
        } else {
            Iterator var7 = var5.iterator();

            while(var7.hasNext()) {
                int var8 = (Integer)var7.next();
                UnitView var9 = var1.getUnit(var8);
                int var10 = DistanceMetrics.chebyshevDistance(var6.getXPosition(), var6.getYPosition(), var9.getXPosition(), var9.getYPosition());
                if (var10 < var4[1]) {
                    var4[0] = var8;
                    var4[1] = var10;
                }
            }

            return var4;
        }
    }

    public static String getUsage() {
        return "It takes three parameters (--agentparam): a space seperated array of enemy player numbers, a boolean for whether it should wander, and a boolean for verbosity";
    }

    public void savePlayerData(OutputStream var1) {
    }

    public void loadPlayerData(InputStream var1) {
    }
}
