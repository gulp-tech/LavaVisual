package tech.gulp.lavavisual.compat;

/** Key press data, in the shape the newer versions pass to screens. */
public record KeyEvent(int key, int scancode, int modifiers) { }
