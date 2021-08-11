package pw.gatchina.chia;

public class JsonConfig {
    public Mongo mongo;
    public boolean skipDebug;

    public static class Mongo {
        public String connection;
    }

}
