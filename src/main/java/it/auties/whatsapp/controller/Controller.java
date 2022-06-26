package it.auties.whatsapp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.auties.whatsapp.util.Preferences;
import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.auties.whatsapp.util.JacksonProvider.JSON;

/**
 * This interface represents is implemented by all WhatsappWeb4J's controllers.
 * It provides an easy way to store IDs and serialize said class.
 */
@SuppressWarnings("unused")
public sealed interface Controller permits Store, Keys {
    /**
     * Returns all the known IDs
     *
     * @return a non-null list
     */
    static LinkedList<Integer> knownIds() {
        try (var walker = Files.walk(Preferences.home(), 1)) {
            return walker.map(Controller::parsePathAsId)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toCollection(LinkedList::new));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot list known ids", exception);
        }
    }

    private static Optional<Integer> parsePathAsId(Path file) {
        try {
            return Optional.of(Integer.parseInt(file.getFileName()
                    .toString()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Converts this object to JSON
     *
     * @return a non-null string
     */
    default String toJSON() throws JsonProcessingException {
        return JSON.writeValueAsString(this);
    }

    /**
     * Saves this object as a JSON
     *
     * @param async whether to perform the write operation asynchronously or not
     */
    void save(boolean async);

    /**
     * Clears some or all fields of this object
     */
    void clear();

    /**
     * Deletes this object from memory
     */
    void delete();
}
