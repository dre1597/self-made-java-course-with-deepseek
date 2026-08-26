static String message(final Notification notification) {
  return switch (notification) {
    case Email(var to) -> "Enviando e-mail para " + to;
    case Sms(var phone) -> "Enviando SMS para " + phone;
    case Push(var userId) -> "Enviando push para " + userId;
  };
}

void main() {
  IO.println(message(new Email("maria@email.com")));
  IO.println(message(new Sms("11999999999")));
  IO.println(message(new Push("user-123")));
}

sealed interface Notification
    permits Email, Sms, Push {
}

record Email(String to) implements Notification {
}

record Sms(String phone) implements Notification {
}

record Push(String userId) implements Notification {
}