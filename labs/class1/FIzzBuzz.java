void main() {
  var ceiling = 100;
//  var ceiling = 1;
//  var ceiling = 0;
  for (var number = 1; number <= ceiling; number++) {
    var output = "";
    if (number % 3 == 0 && number % 5 == 0) {
      output += "FizzBuzz";
    }

    if (number % 3 == 0) {
      output += "Fizz";
    }
    if (number % 5 == 0) {
      output += "Buzz";
    }
    if (output.isEmpty()) {
      output = String.valueOf(number);
    }
    IO.println(output);
  }
}