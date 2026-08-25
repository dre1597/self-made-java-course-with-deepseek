void main() {
  IO.println(CountWords.execute("I need a phrase here"));
  IO.println(CountWords.execute("word"));
  IO.println(CountWords.execute("  "));
  IO.println(CountWords.execute(""));
  IO.println(CountWords.execute(null));
}

static class CountWords {
  private CountWords() {
  }

  public static int execute(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.split("\\s+").length;
  }
}