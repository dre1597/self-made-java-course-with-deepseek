void main() {
  try {
    Account.execute("ACC-123");
  } catch (AccountBlockedException exception) {
    IO.println("Conta: " + exception.accountId());
    IO.println("Motivo: " + exception.reason());
    IO.println("Mensagem: " + exception.getMessage());
  }
}

static class Account {
  private Account() {
  }

  static void execute(String accountId) {
    throw new AccountBlockedException(
        accountId,
        "Excesso de tentativas de login"
    );
  }
}

static class AccountBlockedException extends RuntimeException {
  private final String accountId;
  private final String reason;

  AccountBlockedException(String accountId, String reason) {
    super("Conta " + accountId + " bloqueada: " + reason);
    this.accountId = accountId;
    this.reason = reason;
  }

  String accountId() {
    return accountId;
  }

  String reason() {
    return reason;
  }
}