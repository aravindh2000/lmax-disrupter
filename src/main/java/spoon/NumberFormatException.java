package spoon;

public class NumberFormatException {

    public static void main(String[] args) {

    }

    public void test_call()
    {
        // This string cannot be parsed as a number
        String invalidNumber = "abc123";

        // Attempt to parse the non-numeric string
        int number = Integer.parseInt(invalidNumber);

        System.out.println("Parsed number: " + number);
    }
}