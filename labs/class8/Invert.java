void main() {
  IO.println(Invert.execute(Map.of("a", "b", "c", "d")));
  IO.println(Invert.execute(Map.of("b", "a", "d", "c")));

  var map = new HashMap<String, String>();
  map.put("a", "b");
  map.put("c", null);

  IO.println(Invert.execute(map));
  IO.println(Invert.execute(Map.of()));
}

static class Invert {
  private Invert() {
  }

  public static Map<String, String> execute(Map<String, String> items) {
    return items.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }
}