package pw.gatchina.chia;

import java.time.Instant;

public class LogFile {
    public LogFile() {}

    public String filename;
    public String hostname;
    public Instant lastModifiedTime;
    public int lines;
    public int linesMatches;
}