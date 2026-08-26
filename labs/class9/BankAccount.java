void main() {
  var bankAccount = new BankAccount(100);
  IO.println(bankAccount.deposit(50));
  IO.println(bankAccount.withdraw(20));
  IO.println(bankAccount.withdraw(100));
//  IO.println(bankAccount.withdraw(-10));
//  IO.println(bankAccount.deposit(-10));
}

public class BankAccount {
  private double balance;

  public BankAccount(final double balance) {
    this.balance = balance;
  }

  public double deposit(double amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }

    return balance + amount;
  }

  public double withdraw(double amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    if (amount > balance) {
      throw new IllegalArgumentException("Amount must be less than balance");
    }

    this.balance -= amount;
    return this.balance;
  }
}