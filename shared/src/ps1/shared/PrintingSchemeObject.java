package ps1.shared;

import java.time.LocalTime;

public record PrintingSchemeObject(long threadId, long prime, LocalTime timestamp) {
}
