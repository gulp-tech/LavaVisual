package tech.gulp.lavavisual.effects;

/** Handed out by the mixin on Player: the raw skin-parts byte the game keeps for a player. Vanilla never reads its
    top bit, and LavaVisual uses it as the mark (see Badge), so what a player shares travels with their own skin. */
public interface SkinParts {
    int lavavisual$skinParts();
}
