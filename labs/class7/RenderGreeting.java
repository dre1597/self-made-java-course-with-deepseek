void main() {
  IO.println(RenderGreeting.execute("Ana"));
  IO.println(RenderGreeting.execute("100%"));
}

static class RenderGreeting {
  private RenderGreeting() {
  }

  public static String execute(String name) {
    return """
          <h1>Olá,%s + "!</h1>
        """.formatted(name);
  }
}