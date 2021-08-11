package pw.gatchina.chia;

public class JsonConfig {
    public Mongo mongo;
    public boolean skipDebug;
    public int cappedLogCollectionSize;
    public String logLineRegExp;
    public String dateTimePattern;

    public static class Mongo {
        public String connection;
    }

}
