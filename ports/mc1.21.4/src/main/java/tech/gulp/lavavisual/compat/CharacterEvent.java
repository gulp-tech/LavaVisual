package tech.gulp.lavavisual.compat;

/** Typed character data, in the shape the newer versions pass to screens. */
public record CharacterEvent(int codepoint, int modifiers) { }
