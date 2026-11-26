package android.net;

/**
 * JVM unit-test stand-in for the framework Uri. The SDK's stub jar cannot construct a Uri, and
 * media3 only needs an immutable value here (the filters under test never resolve it).
 */
public final class Uri {
  public static final Uri EMPTY = new Uri("");

  private final String value;

  private Uri(String value) {
    this.value = value;
  }

  public static Uri parse(String uriString) {
    return new Uri(uriString == null ? "" : uriString);
  }

  @Override
  public String toString() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Uri && value.equals(((Uri) other).value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
