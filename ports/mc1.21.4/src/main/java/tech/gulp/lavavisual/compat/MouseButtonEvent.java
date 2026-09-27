package tech.gulp.lavavisual.compat;

/** Mouse button data, in the shape the newer versions pass to screens. */
public record MouseButtonEvent(double x, double y, int button) { }
