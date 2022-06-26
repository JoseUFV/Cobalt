package it.auties.whatsapp.api;

import it.auties.whatsapp.util.Exceptions;
import it.auties.whatsapp.util.HmacValidationException;

import java.util.function.BiFunction;
import java.util.logging.Logger;

import static it.auties.whatsapp.api.ErrorHandler.Location.MESSAGE;

/**
 * This interface allows to handle a socket error and provides a default way to do so
 */
public interface ErrorHandler extends BiFunction<ErrorHandler.Location, Throwable, Boolean> {
    /**
     * A logger instance
     */
    Logger LOGGER = Logger.getLogger("ErrorHandler");

    /**
     * Default error handler implementation
     *
     * @return a non-null error handler
     */
    static ErrorHandler defaultErrorHandler() {
        return (location, throwable) -> {
            LOGGER.warning("Socket failure at %s".formatted(location));
            LOGGER.warning("Saved stacktrace at: %s".formatted(Exceptions.save(throwable)));
            if (location != MESSAGE || !(throwable instanceof HmacValidationException)) {
                LOGGER.warning("Ignoring failure");
                return false;
            }

            LOGGER.warning("Restoring session");
            return true;
        };
    }

    /**
     * The constants of this enumerated type describe the various locations where an error can occur in the socket
     */
    enum Location {
        /**
         * Unknown
         */
        UNKNOWN,

        /**
         * Called when a malformed node is sent
         */
        ERRONEOUS_NODE,

        /**
         * Called when the media connection cannot be renewed
         */
        MEDIA_CONNECTION,

        /**
         * Called when an error arrives from the stream
         */
        STREAM,

        /**
         * Called when an error is thrown while logging in
         */
        LOGIN,

        /**
         * Called when an error is thrown while pulling or pushing app data
         */
        APP_STATE_SYNC,

        /**
         * Called when an error occurs when serializing or deserializing a Whatsapp message
         */
        MESSAGE
    }
}
