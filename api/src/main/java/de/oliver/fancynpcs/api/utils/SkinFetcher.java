package de.oliver.fancynpcs.api.utils;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.oliver.fancylib.UUIDFetcher;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lushplugins.chatcolorhandler.ChatColorHandler;
import org.lushplugins.chatcolorhandler.parsers.ParserTypes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skin system is deprecated and will be replaced with a slightly different system in the near future.
 */
@Deprecated
public final class SkinFetcher {

    @Deprecated
    public static final Map<String, SkinData> skinCache = new ConcurrentHashMap<>(); // identifier -> skinData
    private final static Gson GSON = new Gson();

    private SkinFetcher() {
    }

    /**
     * Fetches the skin data from the Mojang API asynchronously.
     *
     * @param identifier The identifier of the skin. This can be a UUID, username, URL or a placeholder by PAPI.
     * @return A CompletableFuture that will contain the SkinData.
     */
    @Deprecated
    public static CompletableFuture<SkinData> fetchSkin(String identifier) {
        return CompletableFuture.supplyAsync(() -> {
            String parsedIdentifier = ChatColorHandler.translate(identifier, ParserTypes.placeholder());

            // try to get skin from cache
            if (skinCache.containsKey(parsedIdentifier)) {
                return skinCache.get(parsedIdentifier);
            }

            if (isURL(parsedIdentifier)) {
                return fetchSkinByURL(parsedIdentifier).join();
            }

            if (isUUID(parsedIdentifier)) {
                // try to get skin from online player
                Optional<? extends Player> onlinePlayer = Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player -> player.getUniqueId().toString().equals(parsedIdentifier))
                        .findFirst();

                if (onlinePlayer.isPresent()) {
                    SkinData skinData = getSkinByOnlinePlayer(onlinePlayer.get());
                    if (skinData != null) {
                        skinCache.put(parsedIdentifier, skinData);
                        return skinData;
                    }
                }

                return fetchSkinByUUID(parsedIdentifier).join();
            }

            // assume it's a username
            UUID uuid = UUIDFetcher.getUUID(parsedIdentifier);
            if (uuid != null) {
                // try to get skin from online player
                Optional<? extends Player> onlinePlayer = Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player -> player.getName().equals(parsedIdentifier))
                        .findFirst();

                if (onlinePlayer.isPresent()) {
                    SkinData skinData = getSkinByOnlinePlayer(onlinePlayer.get());
                    if (skinData != null) {
                        skinCache.put(uuid.toString(), skinData);
                        return skinData;
                    }
                }

                // try to get skin from cache with UUID
                if (skinCache.containsKey(uuid.toString())) {
                    return skinCache.get(uuid.toString());
                }

                return fetchSkinByUUID(uuid.toString()).join();
            }

            return null;
        });
    }

    /**
     * Fetches the skin data from the online player.
     *
     * @param player The player.
     * @return The SkinData or {@code null} if the skin data could not be fetched.
     */
    @Deprecated
    public static SkinData getSkinByOnlinePlayer(Player player) {
        if (player == null) {
            return null;
        }

        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                return new SkinData(player.getUniqueId().toString(), property.getValue(), property.getSignature());
            }
        }

        return null;
    }

    /**
     * Fetches the skin data from the Mojang API by UUID asynchronously.
     *
     * @param uuid The UUID of the player.
     * @return A CompletableFuture that will contain the SkinData.
     */
    @Deprecated
    public static CompletableFuture<SkinData> fetchSkinByUUID(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                String json = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8).useDelimiter("\\A").next();
                JsonParser parser = new JsonParser();
                JsonObject obj = parser.parse(json).getAsJsonObject();

                String value = obj.getAsJsonArray("properties").get(0).getAsJsonObject().getAsJsonPrimitive("value").getAsString();
                String signature = obj.getAsJsonArray("properties").get(0).getAsJsonObject().getAsJsonPrimitive("signature").getAsString();
                SkinData skinData = new SkinData(uuid, value, signature);

                skinCache.put(uuid, skinData);

                FancyNpcsPlugin.get().getSkinCache().upsert(new SkinCacheData(skinData, System.currentTimeMillis(), 1000L * 60 * 60 * 24 + randomFromTo(15, 30))); // 15-30 days
                return skinData;
            } catch (IOException e) {
                FancyNpcsPlugin.get().getFancyLogger().warn("Failed to fetch skin data for UUID " + uuid);
                FancyNpcsPlugin.get().getFancyLogger().warn(e);
                return null;
            }
        });
    }

    /**
     * Fetches the skin data from the Mojang API by URL asynchronously.
     *
     * @param skinURL The URL of the skin.
     * @return A CompletableFuture that will contain the SkinData.
     */
    @Deprecated
    public static CompletableFuture<SkinData> fetchSkinByURL(String skinURL) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.mineskin.org/generate/url");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                // Send POST data
                DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
                outputStream.writeBytes(GSON.toJson(new MineSkinRequest(skinURL, 0, "", "auto")));
                outputStream.close();

                // Handle response
                int responseCode = conn.getResponseCode();
                InputStream inputStream = null;

                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = conn.getInputStream();
                } else if (responseCode >= 400) {
                    inputStream = conn.getErrorStream();
                    String errorBody = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A").next();
                    FancyNpcsPlugin.get().getFancyLogger().warn("4xx Error Response Body: " + errorBody);
                    inputStream.close();
                    return null;
                }

                if (inputStream == null) {
                    FancyNpcsPlugin.get().getFancyLogger().warn("Failed to fetch skin data for URL " + skinURL);
                    return null;
                }

                String json = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A").next();
                inputStream.close();

                JsonParser parser = new JsonParser();
                JsonObject obj = parser.parse(json).getAsJsonObject();

                String value = obj.getAsJsonObject("data").getAsJsonObject("texture").getAsJsonPrimitive("value").getAsString();
                String signature = obj.getAsJsonObject("data").getAsJsonObject("texture").getAsJsonPrimitive("signature").getAsString();
                SkinData skinData = new SkinData(skinURL, value, signature);

                FancyNpcsPlugin.get().getSkinCache().upsert(new SkinCacheData(skinData, System.currentTimeMillis(), 1000L * 60 * 60 * 24 * randomFromTo(30, 30 * 3))); // 1-3 months

                skinCache.put(skinURL, skinData);
                return skinData;
            } catch (IOException e) {
                FancyNpcsPlugin.get().getFancyLogger().warn("Failed to fetch skin data for URL " + skinURL);
                FancyNpcsPlugin.get().getFancyLogger().warn(e);
                return null;
            }
        });
    }

    private static boolean isURL(String identifier) {
        return identifier.startsWith("http");
    }

    public static boolean isPlaceholder(String identifier) {
        return identifier.startsWith("%") && identifier.endsWith("%") || identifier.startsWith("{") && identifier.endsWith("}");
    }

    private static boolean isUUID(String identifier) {
        return identifier.length() == 36 && identifier.contains("-");
    }

    private static long randomFromTo(long from, long to) {
        return from + (long) (Math.random() * (to - from));
    }

    /**
     * Represents all required data for a skin.
     *
     * @param identifier The identifier of the skin. This can be a UUID, username, URL or a placeholder by PAPI.
     * @param value      The value of the skin. If {@code null}, the skin will be fetched from the Mojang API.
     * @param signature  The signature of the skin. If {@code null}, the skin will be fetched from the Mojang API.
     */
    @Deprecated
    public record SkinData(@NotNull String identifier, @Nullable String value, @Nullable String signature) {

        /**
         * Fetches the skin data from the Mojang API if the value or signature is {@code null}.
         *
         * @return The value of the skin or {@code null} if the skin data could not be fetched.
         */
        @Override
        public String value() {
            if (value == null || value.isEmpty()) {
                try {
                    SkinData skinData = fetchSkin(identifier).join();
                    return skinData == null ? null : skinData.value();
                } catch (Exception e) {
                    FancyNpcsPlugin.get().getFancyLogger().warn("Failed to fetch skin data for " + identifier);
                    FancyNpcsPlugin.get().getFancyLogger().warn(e);
                }
            }

            return value;
        }

        /**
         * Fetches the skin data from the Mojang API if the value or signature is {@code null}.
         *
         * @return The signature of the skin or {@code null} if the skin data could not be fetched.
         */
        @Override
        public String signature() {
            if (signature == null || signature.isEmpty()) {
                try {
                    SkinData skinData = fetchSkin(identifier).join();
                    return skinData == null ? null : skinData.signature();
                } catch (Exception e) {
                    FancyNpcsPlugin.get().getFancyLogger().warn("Failed to fetch skin data for " + identifier);
                    FancyNpcsPlugin.get().getFancyLogger().warn(e);
                }
            }

            return signature;
        }
    }

    /**
     * Represents the cached skin data. For internal use only.
     *
     * @param skinData    The skin data.
     * @param lastUpdated The timestamp when the skin data was last updated.
     * @param timeToLive  The time to live of the skin data in milliseconds.
     */
    @Deprecated
    @ApiStatus.Internal
    public record SkinCacheData(@NotNull SkinData skinData, long lastUpdated, long timeToLive) {
        public boolean isExpired() {
            return timeToLive > 0 && System.currentTimeMillis() - lastUpdated > timeToLive;
        }
    }

    private record MineSkinRequest(String url, int visibility, String name, String variant) {
    }
}
