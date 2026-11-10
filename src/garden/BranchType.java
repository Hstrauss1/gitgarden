package garden;

/** The kinds of branch the bonsai algorithm grows (same set cbonsai uses). */
public enum BranchType {
    TRUNK,
    SHOOT_LEFT,
    SHOOT_RIGHT,
    DYING,
    DEAD;

    public boolean isLeaf() {
        return this == DYING || this == DEAD;
    }
}
