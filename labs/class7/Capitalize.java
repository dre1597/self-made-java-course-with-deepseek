void main() {
  IO.println(Capitalize.execute(null));
  IO.println(Capitalize.execute(""));
  IO.println(Capitalize.execute("OLA MUNDO"));
  IO.println(Capitalize.execute("java"));
  IO.println(Capitalize.execute("j"));
}

static class Capitalize {
  private Capitalize() {
  }

  public static String execute(String text) {
    if (text == null) {
      return null;
    }

    if (text.isBlank()) {
      return "";
    }

    return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
  }
}