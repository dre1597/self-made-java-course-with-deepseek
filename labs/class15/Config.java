package class15;

import java.io.Serializable;


public class Config implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String databaseUrl;
  private String url;

  public Config(String databaseUrl) {
    this.databaseUrl = databaseUrl;
  }

  @Override
  public String toString() {
    return "Config[url=%s]".formatted(url);
  }
}