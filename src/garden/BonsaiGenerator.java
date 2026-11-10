package garden;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A Java port of the recursive branch algorithm from cbonsai
 * (https://gitlab.com/jallbrit/cbonsai).
 *
 * A branch walks step by step. Each step it loses one point of "life",
 * picks a (dx, dy) from a weighted dice roll that depends on its type,
 * and may spawn child branches:
 *   - life &lt; 3                    -> a DEAD branch (leaf cluster)
 *   - trunk/shoot near end of life -> a DYING branch (leafy spray)
 *   - trunk, randomly              -> a new trunk split, or a left/right shoot
 *
 * "multiplier" controls how often branching happens; "life" controls size.
 * Coordinates are grid cells with y growing downward (screen style).
 */
public final class BonsaiGenerator {

    private final Random rng;
    private final int lifeStart;
    private final int multiplier;
    private final String[] leaves;
    private final List<Glyph> glyphs = new ArrayList<Glyph>();

    private int groundY;
    private int shootCounter;

    public BonsaiGenerator(long seed, int life, int multiplier, String[] leaves) {
        this.rng = new Random(seed);
        this.lifeStart = life;
        this.multiplier = Math.max(2, multiplier);
        this.leaves = leaves;
    }

    /** Grows a whole tree from (startX, startY) and returns its glyphs in growth order. */
    public List<Glyph> grow(int startX, int startY) {
        glyphs.clear();
        groundY = startY;
        shootCounter = rng.nextInt();
        branch(startX, startY, BranchType.TRUNK, lifeStart);
        return new ArrayList<Glyph>(glyphs);
    }

    private void branch(int x, int y, BranchType type, int life) {
        int shootCooldown = multiplier;

        while (life > 0) {
            life--;
            int age = lifeStart - life;

            int[] delta = deltas(type, life, age);
            int dx = delta[0];
            int dy = delta[1];

            // keep branches from drooping into the pot
            if (dy > 0 && y > groundY - 2) {
                dy--;
            }

            if (life < 3) {
                branch(x, y, BranchType.DEAD, life);
            } else if (type == BranchType.TRUNK && life < multiplier + 2) {
                branch(x, y, BranchType.DYING, life);
            } else if ((type == BranchType.SHOOT_LEFT || type == BranchType.SHOOT_RIGHT)
                    && life < multiplier + 2) {
                branch(x, y, BranchType.DYING, life);
            } else if (type == BranchType.TRUNK && (roll(3) == 0 || life % multiplier == 0)) {
                if (roll(8) == 0 && life > 7) {
                    // split the trunk
                    shootCooldown = multiplier * 2;
                    branch(x, y, BranchType.TRUNK, life + roll(5) - 2);
                } else if (shootCooldown <= 0) {
                    // sprout a shoot, alternating sides
                    shootCooldown = multiplier * 2;
                    shootCounter++;
                    BranchType side = (shootCounter & 1) == 0 ? BranchType.SHOOT_LEFT : BranchType.SHOOT_RIGHT;
                    branch(x, y, side, life + multiplier);
                }
            }
            shootCooldown--;

            x += dx;
            y += dy;
            glyphs.add(new Glyph(x, y, chooseString(type, life, dx, dy), type, chooseBright(type)));
        }
    }

    /** Weighted direction choice for one step; returns {dx, dy}. */
    private int[] deltas(BranchType type, int life, int age) {
        int dx = 0;
        int dy = 0;
        int dice;

        switch (type) {
            case TRUNK:
                if (age <= 2 || life < 4) {
                    // young or nearly dead: grow sideways
                    dy = 0;
                    dx = roll(3) - 1;
                } else if (age < multiplier * 3) {
                    // young trunk: climb slowly, wander a lot
                    dy = age % Math.max(1, multiplier / 2) == 0 ? -1 : 0;
                    dice = roll(10);
                    if (dice == 0) dx = -2;
                    else if (dice <= 3) dx = -1;
                    else if (dice <= 5) dx = 0;
                    else if (dice <= 8) dx = 1;
                    else dx = 2;
                } else {
                    // mature trunk: mostly upward
                    dy = roll(10) > 2 ? -1 : 0;
                    dx = roll(3) - 1;
                }
                break;

            case SHOOT_LEFT:
                dice = roll(10);
                if (dice <= 1) dy = -1;
                else if (dice <= 7) dy = 0;
                else dy = 1;

                dice = roll(10);
                if (dice <= 1) dx = -2;
                else if (dice <= 5) dx = -1;
                else if (dice <= 8) dx = 0;
                else dx = 1;
                break;

            case SHOOT_RIGHT:
                dice = roll(10);
                if (dice <= 1) dy = -1;
                else if (dice <= 7) dy = 0;
                else dy = 1;

                dice = roll(10);
                if (dice <= 1) dx = 2;
                else if (dice <= 5) dx = 1;
                else if (dice <= 8) dx = 0;
                else dx = -1;
                break;

            case DYING:
                dice = roll(10);
                if (dice <= 1) dy = -1;
                else if (dice <= 8) dy = 0;
                else dy = 1;

                dice = roll(15);
                if (dice == 0) dx = -3;
                else if (dice <= 2) dx = -2;
                else if (dice <= 5) dx = -1;
                else if (dice <= 8) dx = 0;
                else if (dice <= 11) dx = 1;
                else if (dice <= 13) dx = 2;
                else dx = 3;
                break;

            case DEAD:
                dice = roll(10);
                if (dice <= 2) dy = -1;
                else if (dice <= 6) dy = 0;
                else dy = 1;
                dx = roll(3) - 1;
                break;
        }
        return new int[]{dx, dy};
    }

    /** Picks the characters for a step, based on the direction it moved. */
    private String chooseString(BranchType type, int life, int dx, int dy) {
        if (life < 4 || type.isLeaf()) {
            return leaves[roll(leaves.length)];
        }
        switch (type) {
            case TRUNK:
                if (dy == 0) return "/~";
                if (dx < 0) return "\\|";
                if (dx == 0) return "/|\\";
                return "|/";
            case SHOOT_LEFT:
                if (dy > 0) return "\\";
                if (dy == 0) return "\\_";
                if (dx < 0) return "\\|";
                if (dx == 0) return "/|";
                return "/";
            case SHOOT_RIGHT:
                if (dy > 0) return "/";
                if (dy == 0) return "_/";
                if (dx < 0) return "\\|";
                if (dx == 0) return "/|";
                return "/";
            default:
                return "?";
        }
    }

    private boolean chooseBright(BranchType type) {
        switch (type) {
            case DYING:
                return roll(10) != 0;
            case DEAD:
                return roll(3) != 0;
            default:
                return roll(2) == 0;
        }
    }

    private int roll(int sides) {
        return rng.nextInt(sides);
    }
}
