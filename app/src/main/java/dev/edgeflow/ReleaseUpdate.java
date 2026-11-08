package dev.edgeflow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Check the public release only when requested from About. */
final class ReleaseUpdate {
    private static final String REPOSITORY = "FallmoonXD/Color-Hand-Operation";
    static final String HOME = "https://github.com/" + REPOSITORY;
    static final String LATEST = HOME + "/releases/latest";
    private static final Pattern VERSION = Pattern.compile("([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})");

    final String version, notesUrl, apkUrl;
    final int comparedToInstalled;

    private ReleaseUpdate(String version, String tag, int comparison, boolean hasApk) {
        this.version = version;
        comparedToInstalled = comparison;
        notesUrl = HOME + "/releases/tag/" + tag;
        apkUrl = hasApk ? HOME + "/releases/download/" + tag + "/" + apkName(version) : null;
    }

    static ReleaseUpdate check(String installed) throws IOException, JSONException {
        URL api = new URL("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) api.openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ColorHandOperation");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("GitHub HTTP " + status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[8192];
                int n;
                while ((n = reader.read(buffer)) != -1) body.append(buffer, 0, n);
                return parse(installed, new JSONObject(body.toString()));
            }
        } finally {
            connection.disconnect();
        }
    }

    static ReleaseUpdate parse(String installed, JSONObject release) throws JSONException {
        if (release.getBoolean("draft") || release.getBoolean("prerelease"))
            throw new JSONException("Expected a public stable release");
        String tag = release.getString("tag_name");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        Matcher local = VERSION.matcher(installed), remote = VERSION.matcher(version);
        if (!local.matches() || !remote.matches()) throw new JSONException("Invalid release version");
        int comparison = 0;
        for (int i = 1; i <= 3 && comparison == 0; i++)
            comparison = Integer.compare(Integer.parseInt(remote.group(i)), Integer.parseInt(local.group(i)));

        JSONArray assets = release.getJSONArray("assets");
        boolean hasApk = false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (apkName(version).equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))) {
                hasApk = true;
                break;
            }
        }
        return new ReleaseUpdate(version, tag, comparison, hasApk);
    }

    private static String apkName(String version) { return "ColorHandOperation-" + version + ".apk"; }
}
