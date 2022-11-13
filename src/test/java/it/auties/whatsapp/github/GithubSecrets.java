package it.auties.whatsapp.github;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.utils.LibraryLoader;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.util.JacksonProvider;
import it.auties.whatsapp.utils.Chunks;
import it.auties.whatsapp.utils.ConfigUtils;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import static java.net.URI.create;
import static java.net.http.HttpRequest.BodyPublishers.ofString;

@UtilityClass
public class GithubSecrets implements JacksonProvider {
    private final LazySodiumJava SODIUM = new LazySodiumJava(new SodiumJava(LibraryLoader.Mode.BUNDLED_ONLY));
    private final String REQUEST_PATH = "https://api.github.com/repos/Auties00/WhatsappWeb4j";
    private final String PUBLIC_KEY_PATH = "actions/secrets/public-key";
    private final String UPDATE_SECRET_PATH = "actions/secrets/%s";
    private final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private final GithubKey PUBLIC_KEY = getPublicKey();

    public void updateCredentials() throws IOException, InterruptedException {
        var credentials = getCredentialsAsJson();
        var cypheredCredentials = encrypt(credentials, true);
        updateSecret(cypheredCredentials, GithubActions.CREDENTIALS_NAME);

        var store = getStoreAsJson();
        var cypheredStore = encrypt(store, true);
        updateSecret(cypheredStore, GithubActions.STORE_NAME);

        var contactName = loadContactName();
        var cypheredContactName = encrypt(contactName.getBytes(StandardCharsets.UTF_8), true);
        updateSecret(cypheredContactName, GithubActions.CONTACT_NAME);
    }

    @SneakyThrows
    private byte[] getStoreAsJson() {
        return SMILE.writeValueAsBytes(Whatsapp.lastConnection().store());
    }

    @SneakyThrows
    private byte[] getCredentialsAsJson() {
        return SMILE.writeValueAsBytes(Whatsapp.lastConnection().keys());
    }

    private byte[] encrypt(byte[] data, boolean compress) throws IOException {
        var publicKeyBytes = Base64.getDecoder().decode(PUBLIC_KEY.key());
        var compressed = getCompressedData(data, compress);
        var cypher = new byte[compressed.length + 48];
        var result = SODIUM.cryptoBoxSeal(cypher, compressed, compressed.length, publicKeyBytes);
        if (!result) {
            throw new IllegalStateException("crypto_box_seal failed");
        }

        return data;
    }

    private byte[] getCompressedData(byte[] data, boolean compress) throws IOException {
        if (!compress) {
            return data;
        }

        var compressedStream = new ByteArrayOutputStream();
        var gzip = new GZIPOutputStream(compressedStream);
        gzip.write(Base64.getEncoder()
                .encode(data));
        gzip.close();
        return compressedStream.toByteArray();
    }

    private GithubKey getPublicKey() {
        try {
            var request = createPublicKeyRequest();
            var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            var result = JSON.readValue(response.body(), GithubKey.class);
            if(result.message() != null){
                throw new IllegalArgumentException("Cannot get public key: %s".formatted(response.body()));
            }

            return result;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest createPublicKeyRequest() throws IOException {
        return HttpRequest.newBuilder()
                .GET()
                .uri(create("%s/%s".formatted(REQUEST_PATH, PUBLIC_KEY_PATH)))
                .header("Authorization", "Bearer %s".formatted(loadGithubToken()))
                .header("Accept", "application/vnd.github.v3+json")
                .build();
    }

    private void updateSecret(byte[] cypheredCredentials, String name)
            throws IOException, InterruptedException {
        var parts = Chunks.partition(cypheredCredentials);

        var encryptedSize = encrypt(new byte[]{(byte) parts.length}, false);
        var sizeRequest = createUpdateSecretRequest(Base64.getEncoder().encodeToString(encryptedSize), "%s_CHUNKS".formatted(name));
        var sizeResponse = HTTP_CLIENT.send(sizeRequest, HttpResponse.BodyHandlers.ofString());
        if (sizeResponse.statusCode() != 201 && sizeResponse.statusCode() != 204) {
            throw new IllegalStateException(
                    "Cannot update %s size: %s(status code %s)".formatted(PUBLIC_KEY.keyId(), sizeResponse.body(), sizeResponse.statusCode()));
        }

        System.out.printf("Sent %s chunk size(chunks: %s, status code %s)%n", name, parts.length, sizeResponse.statusCode());

        for(var index = 0; index < parts.length; index++){
            var part = parts[index];
            var request = createUpdateSecretRequest(Base64.getEncoder().encodeToString(part), "%s_%s".formatted(name, index));
            var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201 && response.statusCode() != 204) {
                throw new IllegalStateException(
                        "Cannot update %s: %s(status code %s, size: %s)".formatted(name, response.body(), response.statusCode(), part.length));
            }

            System.out.printf("Sent %s(chunk: %s, status code %s)%n", name, index, response.statusCode());
        }
    }

    private HttpRequest createUpdateSecretRequest(String cypheredCredentials, String name)
            throws IOException {
        var upload = new GithubUpload(PUBLIC_KEY.keyId(), cypheredCredentials);
        return HttpRequest.newBuilder()
                .PUT(ofString(JSON.writeValueAsString(upload)))
                .uri(create("%s/%s".formatted(REQUEST_PATH, UPDATE_SECRET_PATH.formatted(name))))
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer %s".formatted(loadGithubToken()))
                .build();
    }

    private String loadGithubToken() throws IOException {
        var config = ConfigUtils.loadConfiguration();
        return Objects.requireNonNull(config.getProperty("github_token"), "Missing github_token in configuration");
    }

    private String loadContactName() throws IOException {
        var config = ConfigUtils.loadConfiguration();
        return Objects.requireNonNull(config.getProperty("contact"), "Missing github_token in configuration");
    }
}
