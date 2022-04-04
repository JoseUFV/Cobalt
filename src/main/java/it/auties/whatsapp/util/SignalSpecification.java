package it.auties.whatsapp.util;

import java.util.concurrent.Semaphore;

public interface SignalSpecification {
    Semaphore SEMAPHORE = new Semaphore(1);
    int CURRENT_VERSION = 3;
    int IV_LENGTH = 16;
    int KEY_LENGTH = 32;
    int MAC_LENGTH = 8;
    int SIGNATURE_LENGTH = 64;
    int KEY_TYPE = 5;
}
