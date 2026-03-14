package bot2yes;

/** Fast xorshift32 PRNG. */
public class Rand {
    public static int state = 12345;

    public static int next() {
        state ^= state << 13;
        state ^= state >> 17;
        state ^= state << 5;
        return state & 0x7fffffff;
    }

    public static int nextInt(int bound) {
        return next() % bound;
    }
}
