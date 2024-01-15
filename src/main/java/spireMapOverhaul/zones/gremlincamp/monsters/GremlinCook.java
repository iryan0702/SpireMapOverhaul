package spireMapOverhaul.zones.gremlincamp.monsters;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.unique.IncreaseMaxHpAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Dazed;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.vfx.combat.HealEffect;
import spireMapOverhaul.SpireAnniversary6Mod;
import spireMapOverhaul.abstracts.AbstractSMOMonster;
import spireMapOverhaul.actions.AllEnemyApplyPowerAction;
import spireMapOverhaul.util.Wiz;
import spireMapOverhaul.zones.gremlincamp.PlayerPoisonPower;

import java.util.ArrayList;

import static spireMapOverhaul.SpireAnniversary6Mod.makeID;

public class GremlinCook extends AbstractSMOMonster {
    public static final String ID = makeID(GremlinCook.class.getSimpleName());
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings(ID);
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;

    private static final Byte ALL_BUFF = 0, ALL_BULK = 1, POISON = 2, TACKLE = 3;

    private int healAmt, strAmt, poisonAmt;
    private boolean secondMove = false;

    public GremlinCook() {
        this(0, 0);
    }

    public GremlinCook(float x, float y) {
        super(NAME, ID, 50, 0, 0, 120f, 210f, SpireAnniversary6Mod.makeMonsterPath("GremlinCamp/GremlinCook.png"), x, y);
        setHp(calcAscensionTankiness(56), calcAscensionTankiness(62));
        addMove(ALL_BUFF, Intent.BUFF);
        addMove(ALL_BULK, Intent.BUFF);
        addMove(POISON, Intent.STRONG_DEBUFF);
        addMove(TACKLE, Intent.ATTACK_DEBUFF, calcAscensionDamage(10));

        healAmt = calcAscensionSpecial(8);
        strAmt = AbstractDungeon.ascensionLevel >= 18? 3: 2;
        poisonAmt = calcAscensionSpecial(6);
    }

    @Override
    protected void setUpMisc() {
        super.setUpMisc();
        this.type = EnemyType.NORMAL;
    }

    @Override
    public void takeTurn() { //TODO: Add sounds
        switch (nextMove) {
            case 0:
                useShakeAnimation(Settings.ACTION_DUR_MED);
                Wiz.atb(new AllEnemyApplyPowerAction(this, strAmt, (m) -> new StrengthPower(m, strAmt)));
                break;
            case 1:
                useShakeAnimation(Settings.ACTION_DUR_FAST);
                Wiz.atb(new AbstractGameAction() {
                    @Override
                    public void update() {
                        isDone = true;
                        Wiz.forAllMonstersLiving(m -> {
                            if(!GremlinCook.ID.equals(m.id)) {
                                m.increaseMaxHp(healAmt, true);
                                AbstractDungeon.effectList.add(new HealEffect(m.hb.cX, m.hb.cY, healAmt));
                            }
                        });
                    }
                });
                break;
            case 2:
                useFastAttackAnimation();
                Wiz.atb(new ApplyPowerAction(AbstractDungeon.player, this, new PlayerPoisonPower(AbstractDungeon.player, poisonAmt), poisonAmt, AbstractGameAction.AttackEffect.POISON));
                break;
            case 3:
                DamageInfo info = new DamageInfo(this, moves.get(nextMove).baseDamage, DamageInfo.DamageType.NORMAL);
                if (info.base > -1) {
                    info.applyPowers(this, AbstractDungeon.player);
                }
                useSlowAttackAnimation();
                addToBot(new DamageAction(AbstractDungeon.player, info, AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                Wiz.atb(new MakeTempCardInDiscardAction(new Dazed(), calcAscensionSpecial(2)));
        }

        addToBot(new RollMoveAction(this));
    }

    @Override
    protected void getMove(int i) {
        //First turn always buffs
        if(firstMove) {
            firstMove = false;
            setMoveShortcut(ALL_BUFF, MOVES[ALL_BUFF]);
            return;
        }

        if(!secondMove) {
            secondMove = true;
            setMoveShortcut(ALL_BULK, MOVES[ALL_BULK]);
        }

        ArrayList<Byte> possibilities = new ArrayList<>();
        int enemies = Wiz.getEnemies().size();
        // If not alone, can buff everyone if it wasn't the last move
        if(enemies > 1 && !lastMove(ALL_BUFF)) {
            possibilities.add(ALL_BUFF);
        } else {
            //If alone and late in combat, can buff itself again (helps player)
            if(GameActionManager.turn > 5 && !lastMove(ALL_BUFF)) {
                possibilities.add(ALL_BUFF);
            }
        }

        // Turn 3+ can start poisoning player
        if(GameActionManager.turn > 2 && !lastMove(POISON)) {
            possibilities.add(POISON);
        }

        // When alone or nothing else, start tackling the fucker
        if(enemies == 1 || possibilities.isEmpty()) {
            possibilities.add(TACKLE);
        }

        byte move = Wiz.getRandomItem(possibilities, AbstractDungeon.aiRng);
        setMoveShortcut(move, MOVES[move]);
    }
}