package it.auties.whatsapp.model.message.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import it.auties.protobuf.base.ProtobufMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

/**
 * The constants of this enumerated type describe the various types of status of a {@link Message}
 */
@AllArgsConstructor
@Accessors(fluent = true)
public enum MessageStatus implements ProtobufMessage {
    /**
     * Unknown
     */
    UNKNOWN(0),

    /**
     * Pending
     */
    PENDING(1),

    /**
     * Acknowledged by the server
     */
    SERVER_ACK(2),

    /**
     * Delivered
     */
    DELIVERED(3),

    /**
     * Read
     */
    READ(4),

    /**
     * Played
     */
    PLAYED(5);

    @Getter
    private final int index;

    public static Optional<MessageStatus> of(String name) {
        return name == null ? Optional.empty() : Arrays.stream(values())
                .filter(entry -> name.toLowerCase().contains(entry.name().toLowerCase()))
                .findFirst();
    }

    @JsonCreator
    public static MessageStatus of(int index) {
        return Arrays.stream(values())
                .filter(entry -> entry.index() == index)
                .findFirst()
                .orElse(UNKNOWN);
    }
}