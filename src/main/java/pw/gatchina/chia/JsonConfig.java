package pw.gatchina.chia;

public class JsonConfig {
    public Mongo mongo;
    public boolean skipDebug;
    public int cappedLogCollectionSize = 2; // Gb
    public static class Mongo {
        public String connection;
    }

}
