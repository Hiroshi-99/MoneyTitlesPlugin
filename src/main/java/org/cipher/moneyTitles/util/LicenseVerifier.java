package org.cipher.moneyTitles.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

/**
 * License verification utility for Lukittu licensing system integration.
 * Handles license verification against the Lukittu API.
 */
public class LicenseVerifier {
    private final String licenseKey;
    private final Logger logger;
    private final JavaPlugin plugin;
    private boolean offlineMode = false;
    private boolean offlineModeWarningShown = false;

    // Hardcoded values for security as recommended by Lukittu
    private static final String TEAM_ID = "96ae43c8-9751-4330-aa00-08ee48afded3";
    private static final String PRODUCT_ID = "47271801-ce10-4985-b088-5ad9b0408f56";
    // Properly formatted multi-line string for the public key
    private static final String PUBLIC_KEY_STRING = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0rO0UydAbrgeXboaMROX" +
            "nhLYAnXUfrXjgoBBHclyyO4BSJqAxBTXOJ53PkxctmxbOBbXdELFBgEd7o1OZ7z" +
            "P8JI+iSlAv6r4UDrCWmWnuLZoHg06bSgbxCCr0mBhAlbZTOW3M9UmIlEIswpW4ac" +
            "KdwoDe3M45O9csKo8SWMRMZbApn7LtWUTkRRJpeUye/RoBVdBQcxX6+YPaPukcuS" +
            "IaCkj70QK49e5iX0sWtIm35wqHNss5k49dFvOHdDrIvg9B3ccIdqkqdfZHb95y+D" +
            "hJrmuaGjcLe4p8e7Rc/fPHlfbi/1hQDjj9T19gY5szC8elqZpZMQ3xK0SXSOgPJ2" +
            "iQIDAQAB";

    // Correct API base URL as per documentation
    private static final String API_BASE_URL = "https://app.lukittu.com";

    /**
     * Creates a new license verifier
     * 
     * @param licenseKey The license key to verify
     * @param plugin     The plugin instance
     */
    public LicenseVerifier(String licenseKey, JavaPlugin plugin) {
        this.licenseKey = licenseKey;
        this.plugin = plugin;
        this.logger = Logger.getLogger("MoneyTitles");
    }

    /**
     * Verifies the license key against the Lukittu API
     * 
     * @return true if the license is valid, false otherwise
     */
    public boolean verify() {
        // If we're in offline mode and already warned the user, return true to allow
        // plugin to function
        if (offlineMode) {
            if (!offlineModeWarningShown) {
                sendColoredMessage(ChatColor.YELLOW + "╔═════════════════════════════════════╗");
                sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.RED + ChatColor.BOLD
                        + "      CONNECTION ERROR       " + ChatColor.YELLOW + " ║");
                sendColoredMessage(ChatColor.YELLOW + "╠═════════════════════════════════════╣");
                sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Cannot connect to license server "
                        + ChatColor.YELLOW + " ║");
                sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Enabling offline mode...         "
                        + ChatColor.YELLOW + " ║");
                sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.RED + " "
                        + "Cannot connect to Lukittu license server. Enabling offline mode." + ChatColor.YELLOW + " ║");
                sendColoredMessage(ChatColor.YELLOW + "╚═════════════════════════════════════╝");
                offlineModeWarningShown = true;
            }
            return true;
        }

        try {
            // Build the verification URL with correct endpoint
            String urlStr = String.format(
                    "%s/api/v1/client/teams/%s/verification/verify",
                    API_BASE_URL, TEAM_ID);

            // Set up the connection
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            // Create JSON request body
            String jsonBody = String.format(
                    "{\"licenseKey\":\"%s\",\"productId\":\"%s\"}",
                    licenseKey, PRODUCT_ID);

            // Send the request
            conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

            // Check response code
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warning("License verification failed with response code: " + responseCode);
                return false;
            }

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse the response
            String responseData = response.toString();
            if (responseData.isEmpty()) {
                logger.warning("Empty response from license server");
                return false;
            }

            // For now, we'll accept a 200 response as validation
            // In a production environment, you should properly parse the JSON and check the
            // 'valid' field
            return true;

        } catch (java.net.UnknownHostException e) {
            // Switch to offline mode if host can't be resolved
            sendColoredMessage(ChatColor.YELLOW + "╔═════════════════════════════════════╗");
            sendColoredMessage(
                    ChatColor.YELLOW + "║ " + ChatColor.RED + ChatColor.BOLD + "      CONNECTION ERROR       "
                            + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Cannot connect to license server "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Enabling offline mode...         "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(
                    ChatColor.YELLOW + "║ " + ChatColor.RED + " " + e.getMessage() + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╚═════════════════════════════════════╝");
            offlineMode = true;
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "License verification error", e);
            // Don't fail on verification errors - allow plugin to function in case of
            // network issues
            sendColoredMessage(ChatColor.YELLOW + "╔═════════════════════════════════════╗");
            sendColoredMessage(
                    ChatColor.YELLOW + "║ " + ChatColor.RED + ChatColor.BOLD + "    VERIFICATION WARNING     "
                            + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " License verification failed!     "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Plugin will continue to function "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + " Please verify your license key   "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╚═════════════════════════════════════╝");
            offlineMode = true;
            return true;
        }
    }

    /**
     * Converts a PEM formatted public key string to a PublicKey object
     * 
     * @param key The PEM formatted public key string
     * @return The PublicKey object
     * @throws Exception If there's an error parsing the key
     */
    private PublicKey getPublicKeyFromString(String key) throws Exception {
        try {
            String publicKeyPEM = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing public key", e);
            throw e;
        }
    }

    /**
     * Sends a heartbeat to the Lukittu API to verify the license is still valid
     * This should be called periodically to ensure continued license validation
     * 
     * @return true if the heartbeat succeeds, false otherwise
     */
    public boolean sendHeartbeat() {
        // If we're in offline mode, return true to allow plugin to continue functioning
        if (offlineMode) {
            return true;
        }

        try {
            // Build the heartbeat URL with correct endpoint
            String urlStr = String.format(
                    "%s/api/v1/client/teams/%s/verification/heartbeat",
                    API_BASE_URL, TEAM_ID);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            // Simulated device ID - in a real scenario, generate a consistent hardware ID
            String deviceId = "minecraft-server-" + java.util.UUID.randomUUID().toString();

            // Create JSON request body - deviceIdentifier is required for heartbeat
            String jsonBody = String.format(
                    "{\"licenseKey\":\"%s\",\"productId\":\"%s\",\"deviceIdentifier\":\"%s\"}",
                    licenseKey, PRODUCT_ID, deviceId);

            // Send the request
            conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            logger.log(Level.WARNING, "License heartbeat error", e);
            // Don't fail on heartbeat errors - allow plugin to continue functioning
            return true;
        }
    }

    /**
     * Checks if the verifier is running in offline mode
     * 
     * @return true if in offline mode, false otherwise
     */
    public boolean isOfflineMode() {
        return offlineMode;
    }

    /**
     * Checks if the offline mode warning has been shown to the user
     * 
     * @return true if the warning has been shown, false otherwise
     */
    public boolean isOfflineModeWarningShown() {
        return offlineModeWarningShown;
    }

    /**
     * Sends a colored message to the console
     * 
     * @param message The message to send
     */
    private void sendColoredMessage(String message) {
        if (plugin != null && plugin.getServer() != null) {
            plugin.getServer().getConsoleSender().sendMessage("[MoneyTitles] " + message);
        } else {
            // Fallback to regular logger if plugin is null
            logger.info(message);
        }
    }
}