package pw.gatchina.util;

public class Metric {
    public static Metric now() {
        return new Metric();
    }

    private final long startTime = System.currentTimeMillis();

    private Metric() {
    }

    public int diffAsSeconds() {
        return diff() / 1000;
    }

    public int diff() {
        return (int) (System.currentTimeMillis() - startTime);
    }
}