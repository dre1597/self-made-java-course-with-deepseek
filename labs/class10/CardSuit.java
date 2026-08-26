void main() {
  IO.println(CardSuit.HEARTS.emoji());
  IO.println(CardSuit.DIAMONDS.emoji());
  IO.println(CardSuit.CLUBS.emoji());
  IO.println(CardSuit.SPADES.emoji());
}

enum CardSuit {
  HEARTS, DIAMONDS, CLUBS, SPADES;

  public String emoji() {
    return switch (this) {
      case HEARTS -> "♥";
      case DIAMONDS -> "♦";
      case CLUBS -> "♣";
      case SPADES -> "♠";
    };
  }
}