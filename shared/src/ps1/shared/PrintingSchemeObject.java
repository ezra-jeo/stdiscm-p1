package ps1.shared;

import java.math.BigInteger;
import java.time.LocalTime;

public record PrintingSchemeObject(long threadId, BigInteger prime, LocalTime timestamp) {
}
