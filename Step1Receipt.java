// STEP 1: Add up a few items, add tax, and print a receipt.
//
// Run it with:   java Step1Receipt.java
//
// Money is stored in CENTS (199 means $1.99). Computers can't store some
// decimals exactly (0.1 + 0.2 = 0.30000000000000004!), so real money code
// counts whole cents to keep every total exact.

// A "record" is a simple bundle of data. Each Item has a name and a price.
record Item(String name, int priceCents) {}

// Sales tax as a decimal: 0.0825 = 8.25%. Change this to match your area.
final double TAX_RATE = 0.0825;

// The program starts running here.
void main() {
    // The customer's cart: a list of items they're buying.
    List<Item> cart = List.of(
        new Item("Coffee", 199),
        new Item("Bagel", 249),
        new Item("Orange Juice", 325)
    );

    // Add up the prices, one item at a time.
    int subtotal = 0;
    for (Item item : cart) {
        subtotal = subtotal + item.priceCents();
    }

    // Tax is a percentage of the subtotal, rounded to the nearest cent.
    int tax = (int) Math.round(subtotal * TAX_RATE);
    int total = subtotal + tax;

    // Print the receipt.
    IO.println("==============================");
    IO.println("          MY STORE            ");
    IO.println("==============================");
    for (Item item : cart) {
        IO.println(line(item.name(), item.priceCents()));
    }
    IO.println("------------------------------");
    IO.println(line("Subtotal", subtotal));
    IO.println(line(String.format("Tax (%.2f%%)", TAX_RATE * 100), tax));
    IO.println(line("TOTAL", total));
    IO.println("==============================");
    IO.println("     Thanks for shopping!     ");
}

// Turns cents into a dollar string: 199 -> "$1.99"
String money(int cents) {
    return String.format("$%d.%02d", cents / 100, cents % 100);
}

// One receipt line: name on the left, price lined up on the right.
String line(String label, int cents) {
    return String.format("%-20s %9s", label, money(cents));
}
