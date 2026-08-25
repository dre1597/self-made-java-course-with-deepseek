void main() {
  IO.println(MaskMail.execute("john.doe@example.com"));
  IO.println(MaskMail.execute("a@b.com"));
  IO.println(MaskMail.execute(""));
  IO.println(MaskMail.execute(null));
}

static class MaskMail {
  private MaskMail() {
  }

  public static String execute(String email) {
    if (email == null) {
      return null;
    }

    if (email.isBlank()) {
      return "";
    }

    var username = email.substring(0, email.indexOf('@'));
    var domain = email.substring(email.indexOf('@'));

    var maskedUsername = username.substring(0, Math.min(2, username.length())) + "***";

    return maskedUsername + domain;
  }
}
