//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package edu.cwru.sepia.agent;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.DamageLog;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EnemyBlockerAgent extends Agent {
    public EnemyBlockerAgent(int playernum) {
        super(playernum);
    }

    public Map<Integer, Action> initialStep(StateView stateView, HistoryView historyView) {
        return this.middleStep(stateView, historyView);
    }

    public Map<Integer, Action> middleStep(StateView stateView, HistoryView historyView) {
        Map<Integer, Action> actions = new HashMap();
        List<Integer> unitIDs = stateView.getUnitIds(this.playernum);
        Iterator var5 = unitIDs.iterator();

        Integer unitID;
        UnitView unit;
        do {
            if (!var5.hasNext()) {
                return actions;
            }

            unitID = (Integer)var5.next();
            unit = stateView.getUnit(unitID);
        } while(!unit.getTemplateView().getName().toLowerCase().equals("footman"));

        int turnNumber = stateView.getTurnNumber();
        List<DamageLog> damageLogs = historyView.getDamageLogs(turnNumber - 1);
        Iterator var10 = damageLogs.iterator();

        DamageLog damageLog;
        do {
            if (!var10.hasNext()) {
                if (turnNumber < 2) {
                    return actions;
                }

                if (turnNumber < 33) {
                    actions.put(unitID, Action.createCompoundMove(unitID, 9, 12));
                } else {
                    actions.put(unitID, Action.createCompoundMove(unitID, 11, 6));
                }

                return actions;
            }

            damageLog = (DamageLog)var10.next();
        } while(damageLog.getDefenderID() != unitID);

        int enemyFootmanID = (Integer)stateView.getUnitIds(0).get(0);
        actions.put(unitID, Action.createCompoundAttack(unitID, enemyFootmanID));
        return actions;
    }

    public void terminalStep(StateView stateView, HistoryView historyView) {
    }

    public void savePlayerData(OutputStream outputStream) {
    }

    public void loadPlayerData(InputStream inputStream) {
    }
}
