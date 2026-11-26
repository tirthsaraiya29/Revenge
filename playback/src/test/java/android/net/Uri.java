package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.util.Locale;

/**
 * Test-only stand-in for the framework Uri used by Media3 builders under plain JUnit.
 * It is never compiled into the application.
 */
public final class Uri implements Parcelable {

    public static final Uri EMPTY = new Uri("");

    private final String raw;

    private Uri(String raw) {
        this.raw = raw;
    }

    public static Uri parse(String uriString) {
        if (uriString == null) {
            return null;
        }
        return new Uri(uriString);
    }

    public static Uri fromFile(File file) {
        return new Uri("file://" + file.getAbsolutePath());
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        String base = scheme + ":" + ssp;
        return new Uri(fragment == null ? base : base + "#" + fragment);
    }

    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        String base = baseUri.toString();
        return new Uri(base.endsWith("/") ? base + pathSegment : base + "/" + pathSegment);
    }

    public static String encode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    public static String decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    public String getScheme() {
        int separator = raw.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String scheme = raw.substring(0, separator).toLowerCase(Locale.US);
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            return null;
        }
        return scheme;
    }

    public String getAuthority() {
        int start = raw.indexOf("://");
        if (start < 0) {
            return null;
        }
        int end = raw.length();
        for (int i = start + 3; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return raw.substring(start + 3, end);
    }

    public String getHost() {
        String authority = getAuthority();
        if (authority == null) {
            return null;
        }
        int at = authority.lastIndexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        int colon = hostPort.indexOf(':');
        return colon >= 0 ? hostPort.substring(0, colon) : hostPort;
    }

    public String getPath() {
        int start = raw.indexOf("://");
        if (start >= 0) {
            int slash = raw.indexOf('/', start + 3);
            if (slash < 0) {
                return "";
            }
            int end = raw.length();
            int query = raw.indexOf('?', slash);
            if (query >= 0) {
                end = query;
            }
            int fragment = raw.indexOf('#', slash);
            if (fragment >= 0 && fragment < end) {
                end = fragment;
            }
            return raw.substring(slash, end);
        }
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            int end = raw.length();
            int query = raw.indexOf('?', colon);
            if (query >= 0) {
                end = query;
            }
            return raw.substring(colon + 1, end);
        }
        return raw;
    }

    public String getLastPathSegment() {
        String path = getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        String segment = slash >= 0 ? path.substring(slash + 1) : path;
        return segment.isEmpty() ? null : segment;
    }

    public String getQueryParameter(String key) {
        int query = raw.indexOf('?');
        if (query < 0) {
            return null;
        }
        int end = raw.indexOf('#', query);
        String queryString = end >= 0
                ? raw.substring(query + 1, end)
                : raw.substring(query + 1);
        for (String pair : queryString.split("&")) {
            int equals = pair.indexOf('=');
            if (equals >= 0 && pair.substring(0, equals).equals(key)) {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }

    public Uri buildUpon() {
        return new Builder(this).build();
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Uri)) {
            return false;
        }
        return raw.equals(((Uri) other).raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeString(raw);
    }

    public static final Parcelable.Creator<Uri> CREATOR = new Parcelable.Creator<Uri>() {
        @Override
        public Uri createFromParcel(Parcel source) {
            return new Uri(source.readString());
        }

        @Override
        public Uri[] newArray(int size) {
            return new Uri[size];
        }
    };

    public static final class Builder {
        private String value = "";

        public Builder() {
        }

        public Builder(Uri uri) {
            value = uri.raw;
        }

        public Builder scheme(String scheme) {
            int separator = value.indexOf(':');
            String rest = separator >= 0 ? value.substring(separator + 1) : value;
            value = scheme + ":" + rest;
            return this;
        }

        public Builder authority(String authority) {
            String scheme = "";
            int separator = value.indexOf(':');
            if (separator >= 0) {
                scheme = value.substring(0, separator + 1);
            }
            int start = value.indexOf("://");
            String rest = start >= 0 ? value.substring(start + 3) : "";
            int path = rest.indexOf('/');
            String pathPart = path >= 0 ? rest.substring(path) : "";
            value = scheme + "//" + authority + pathPart;
            return this;
        }

        public Builder path(String path) {
            String base = this.value;
            int start = base.indexOf("://");
            String prefix;
            if (start >= 0) {
                int slash = base.indexOf('/', start + 3);
                prefix = slash >= 0 ? base.substring(0, slash) : base;
            } else {
                int separator = base.indexOf(':');
                prefix = separator >= 0 ? base.substring(0, separator + 1) : "";
            }
            value = prefix + path;
            return this;
        }

        public Builder appendPath(String segment) {
            return path(getPath() + "/" + segment);
        }

        public Uri build() {
            return new Uri(value);
        }

        private String getPath() {
            return new Uri(value).getPath();
        }
    }
}
