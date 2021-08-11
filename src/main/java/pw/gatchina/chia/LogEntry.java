package pw.gatchina.chia;

import java.time.LocalDateTime;

public class LogEntry {
    public LogEntry() {}

    public String hostname;
    public LocalDateTime datetime;
    public String level;
    public String message;
    public String serviceName;
    public String serviceFullName;
}

