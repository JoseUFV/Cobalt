package it.auties.whatsapp.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import it.auties.bytes.Bytes;
import it.auties.whatsapp.binary.PatchType;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.signal.auth.SignedDeviceIdentity;
import it.auties.whatsapp.model.signal.auth.SignedDeviceIdentityHMAC;
import it.auties.whatsapp.model.signal.keypair.SignalKeyPair;
import it.auties.whatsapp.model.signal.keypair.SignalPreKeyPair;
import it.auties.whatsapp.model.signal.keypair.SignalSignedKeyPair;
import it.auties.whatsapp.model.signal.sender.SenderKeyName;
import it.auties.whatsapp.model.signal.sender.SenderKeyRecord;
import it.auties.whatsapp.model.signal.session.Session;
import it.auties.whatsapp.model.signal.session.SessionAddress;
import it.auties.whatsapp.model.sync.AppStateSyncKey;
import it.auties.whatsapp.model.sync.LTHashState;
import it.auties.whatsapp.util.ControllerProviderLoader;
import it.auties.whatsapp.util.Preferences;
import lombok.*;
import lombok.Builder.Default;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNullElseGet;

/**
 * This controller holds the cryptographic-related data regarding a WhatsappWeb session
 */
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@Jacksonized
@Accessors(fluent = true, chain = true)
@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class Keys implements Controller<Keys> {
    /**
     * The client id
     */
    @Getter
    private int id;

    /**
     * The secret key pair used for buffer messages
     */
    @Default
    @NonNull
    @Getter
    private SignalKeyPair noiseKeyPair = SignalKeyPair.random();

    /**
     * The ephemeral key pair
     */
    @Default
    @NonNull
    @Getter
    private SignalKeyPair ephemeralKeyPair = SignalKeyPair.random();

    /**
     * The signed identity key
     */
    @Default
    @NonNull
    @Getter
    private SignalKeyPair identityKeyPair = SignalKeyPair.random();

    /**
     * The signed pre key
     */
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private SignalSignedKeyPair signedKeyPair;

    /**
     * Whether these keys have generated pre keys assigned to them
     */
    @Default
    @NonNull
    private ConcurrentLinkedDeque<SignalPreKeyPair> preKeys = new ConcurrentLinkedDeque<>();

    /**
     * The user using these keys
     */
    @Getter
    private ContactJid companion;

    /**
     * The companion secret key
     */
    @Default
    @Getter
    private byte[] companionKey = SignalKeyPair.random()
            .publicKey();

    /**
     * The bytes of the encoded {@link SignedDeviceIdentityHMAC} received during the auth process
     */
    @Getter
    private SignedDeviceIdentity companionIdentity;

    /**
     * Sender keys for signal implementation
     */
    @NonNull
    @Default
    private Map<SenderKeyName, SenderKeyRecord> senderKeys = new ConcurrentHashMap<>();

    /**
     * App state keys
     */
    @NonNull
    @Default
    private ConcurrentLinkedDeque<AppStateSyncKey> appStateKeys = new ConcurrentLinkedDeque<>();

    /**
     * Sessions map
     */
    @NonNull
    @Default
    private Map<SessionAddress, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Hash state
     */
    @NonNull
    @Default
    private Map<PatchType, LTHashState> hashStates = new ConcurrentHashMap<>();

    /**
     * Write counter for IV
     */
    @NonNull
    @JsonIgnore
    @Default
    private AtomicLong writeCounter = new AtomicLong();

    /**
     * Read counter for IV
     */
    @NonNull
    @JsonIgnore
    @Default
    private AtomicLong readCounter = new AtomicLong();

    /**
     * Session dependent keys to write and read cyphered messages
     */
    @JsonIgnore
    @Getter
    @Setter
    private Bytes writeKey, readKey;

    @JsonIgnore
    @Getter
    @Setter
    private boolean useDefaultSerializer;

    /**
     * Returns a new instance of random keys
     *
     * @param id the unsigned id of these keys
     * @param useDefaultSerializer whether the default serializer should be used
     * @return a non-null instance of WhatsappKeys
     */
    public static Keys random(int id, boolean useDefaultSerializer) {
        var result = Keys.builder()
                .id(id)
                .useDefaultSerializer(useDefaultSerializer)
                .build();
        result.signedKeyPair(SignalSignedKeyPair.of(result.id(), result.identityKeyPair()));
        result.serialize(true);
        return result;
    }

    /**
     * Returns the keys saved in memory or constructs a new clean instance
     *
     * @param id the id of this session
     * @param useDefaultSerializer whether the default serializer should be used
     * @return a non-null instance of WhatsappKeys
     */
    public static Keys of(int id, boolean useDefaultSerializer) {
        var preferences = Preferences.of("%s/keys.smile", id);
        return Optional.ofNullable(preferences.read(Keys.class))
                .map(store -> store.useDefaultSerializer(useDefaultSerializer))
                .orElseGet(() -> random(id, useDefaultSerializer));
    }

    /**
     * Clears the signal keys associated with this object
     */
    @Override
    public void clear() {
        this.readKey = null;
        this.writeKey = null;
        this.writeCounter.set(0);
        this.readCounter.set(0);
    }

    /**
     * Checks if the serverToken and clientToken are not null
     *
     * @return true if both the serverToken and clientToken are not null
     */
    public boolean hasCompanion() {
        return companion != null;
    }

    /**
     * Checks if the client sent pre keys to the server
     *
     * @return true if the client sent pre keys to the server
     */
    public boolean hasPreKeys() {
        return !preKeys.isEmpty();
    }

    /**
     * Queries the first {@link SenderKeyRecord} that matches {@code name}
     *
     * @param name the non-null name to search
     * @return a non-null SenderKeyRecord
     */
    public SenderKeyRecord findSenderKeyByName(@NonNull SenderKeyName name) {
        return requireNonNullElseGet(senderKeys.get(name), () -> {
            var record = new SenderKeyRecord();
            senderKeys.put(name, record);
            return record;
        });
    }

    /**
     * Queries the {@link Session} that matches {@code address}
     *
     * @param address the non-null address to search
     * @return a non-null Optional SessionRecord
     */
    public Optional<Session> findSessionByAddress(@NonNull SessionAddress address) {
        return Optional.ofNullable(sessions.get(address));
    }

    /**
     * Queries the trusted key that matches {@code id}
     *
     * @param id the id to search
     * @return a non-null signed key pair
     * @throws IllegalArgumentException if no element can be found
     */
    public Optional<SignalSignedKeyPair> findSignedKeyPairById(int id) {
        return id == signedKeyPair.id() ?
                Optional.of(signedKeyPair) :
                Optional.empty();
    }

    /**
     * Queries the trusted key that matches {@code id}
     *
     * @param id the non-null id to search
     * @return a non-null pre key
     */
    public Optional<SignalPreKeyPair> findPreKeyById(Integer id) {
        return id == null ?
                Optional.empty() :
                preKeys.stream()
                        .filter(preKey -> preKey.id() == id)
                        .findFirst();
    }

    /**
     * Queries the app state key that matches {@code id}
     *
     * @param id the non-null id to search
     * @return a non-null Optional app state dataSync key
     */
    public Optional<AppStateSyncKey> findAppKeyById(byte[] id) {
        return appStateKeys.stream()
                .filter(preKey -> preKey.keyId() != null && Arrays.equals(preKey.keyId()
                        .keyId(), id))
                .findFirst();
    }

    /**
     * Queries the hash state that matches {@code name}.
     * Otherwise, creates a new one.
     *
     * @param patchType the non-null name to search
     * @return a non-null hash state
     */
    public Optional<LTHashState> findHashStateByName(@NonNull PatchType patchType) {
        return Optional.ofNullable(hashStates.get(patchType));
    }

    /**
     * Checks whether {@code identityKey} is trusted for {@code address}
     *
     * @param address     the non-null address
     * @param identityKey the nullable identity key
     * @return true if any match is found
     */
    public boolean hasTrust(@NonNull SessionAddress address, byte[] identityKey) {
        return true; // At least for now
    }

    /**
     * Checks whether a session already exists for the given address
     *
     * @param address the address to check
     * @return true if a session for that address already exists
     */
    public boolean hasSession(@NonNull SessionAddress address) {
        return sessions.containsKey(address);
    }

    /**
     * Adds the provided address and record to the known sessions
     *
     * @param address the non-null address
     * @param record  the non-null record
     * @return this
     */
    public Keys putSession(@NonNull SessionAddress address, @NonNull Session record) {
        sessions.put(address, record);
        serialize(true);
        return this;
    }

    /**
     * Adds the provided hash state to the known ones
     *
     * @param patchType the non-null sync name
     * @param state     the non-null hash state
     * @return this
     */
    public Keys putState(@NonNull PatchType patchType, @NonNull LTHashState state) {
        hashStates.put(patchType, state);
        serialize(true);
        return this;
    }

    /**
     * Adds the provided keys to the app state keys
     *
     * @param keys the keys to add
     * @return this
     */
    public Keys addAppKeys(@NonNull Collection<AppStateSyncKey> keys) {
        appStateKeys.addAll(keys);
        serialize(true);
        return this;
    }

    /**
     * Adds the provided pre key to the pre keys
     *
     * @param preKey the key to add
     * @return this
     */
    public Keys addPreKey(SignalPreKeyPair preKey) {
        preKeys.add(preKey);
        serialize(true);
        return this;
    }

    /**
     * Returns write counter
     *
     * @param increment whether the counter should be incremented after the call
     * @return an unsigned long
     */
    public long writeCounter(boolean increment) {
        return increment ?
                writeCounter.getAndIncrement() :
                writeCounter.get();
    }

    /**
     * Returns read counter
     *
     * @param increment whether the counter should be incremented after the call
     * @return an unsigned long
     */
    public long readCounter(boolean increment) {
        return increment ?
                readCounter.getAndIncrement() :
                readCounter.get();
    }

    /**
     * Returns the id of the last available pre key
     *
     * @return an integer
     */
    public int lastPreKeyId() {
        return preKeys.isEmpty() ?
                0 :
                preKeys.getLast()
                        .id();
    }

    /**
     * Get any available app key
     *
     * @return a non-null app key
     */
    public AppStateSyncKey appKey() {
        return Objects.requireNonNull(appStateKeys.peekLast(), "No keys available");
    }

    /**
     * Returns whether any app key is available
     *
     * @return a boolean
     */
    public boolean hasAppKeys(){
        return !appStateKeys.isEmpty();
    }

    @JsonSetter
    private void defaultSignedKey() {
        this.signedKeyPair = SignalSignedKeyPair.of(id, identityKeyPair);
    }

    /**
     * This function sets the companion field to the value of the companion parameter, serializes the object, and returns
     * the object.
     *
     * @param companion the non-null companion
     * @return The object itself.
     */
    public Keys companion(ContactJid companion) {
        this.companion = companion;
        serialize(true);
        return this;
    }

    /**
     * This function sets the companionIdentity field to the value of the companionIdentity parameter, serializes the
     * object, and returns the object.
     *
     * @param companionIdentity The identity of the companion device.
     * @return The object itself.
     */
    public Keys companionIdentity(SignedDeviceIdentity companionIdentity) {
        this.companionIdentity = companionIdentity;
        serialize(true);
        return this;
    }

    @Override
    public void dispose() {
        serialize(false);
    }

    @Override
    public void serialize(boolean async) {
        ControllerProviderLoader.providers(useDefaultSerializer())
                .forEach(serializer -> serializer.serializeKeys(this, async));
    }
}