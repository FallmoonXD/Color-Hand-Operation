package android.accessibilityservice;

/** API 33 surface. Activation accessors are deliberately absent. */
public final class MagnificationConfig {
    private float scale, x, y;
    private int mode;
    public float getScale() { return scale; }
    public float getCenterX() { return x; }
    public float getCenterY() { return y; }
    public int getMode() { return mode; }
    public static final class Builder {
        private final MagnificationConfig value = new MagnificationConfig();
        public Builder setMode(int mode) { value.mode = mode; return this; }
        public Builder setScale(float scale) { value.scale = scale; return this; }
        public Builder setCenterX(float x) { value.x = x; return this; }
        public Builder setCenterY(float y) { value.y = y; return this; }
        public MagnificationConfig build() { return value; }
    }
}
