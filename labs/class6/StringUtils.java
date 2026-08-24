void main() {
  IO.println(StringUtils.truncate("Hello, World!", 5));
  IO.println(StringUtils.truncate("Hello, World!", 12));
  IO.println(StringUtils.truncate("Hello, World!", 13));
  IO.println(StringUtils.truncate("Hello, World!", 20));
  IO.println(StringUtils.truncate(null, 0));
}

class StringUtils {
  private StringUtils() {
  }

  public static String truncate(String text, int maxLength) {
    if (text == null) {
      return null;
    }
    return text.substring(0, Math.min(text.length(), maxLength));
  }
}