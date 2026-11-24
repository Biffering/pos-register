// STEP 2: A register you can ring items up on.
//
// Run it with:   java Register.java
//
// Type a product CODE (like 1001) to add it to the sale, or type part of a
// NAME (like "coff") to search. Commands:
//   list   - show every product
//   undo   - remove the last item you rang up
//   done   - finish the sale and print the receipt
//   quit   - close the register
//
// Each receipt ends with a QR code customers can scan with their phone
// camera to take a survey. The QR drawing comes from the free "QR Code
// generator" library by Project Nayuki (MIT License), in the io/ folder.

import io.nayuki.qrcodegen.QrCode;

// A product the store sells: its code, name, and price in cents.
record Product(String code, String name, int priceCents) {}

final double TAX_RATE = 0.0825; // 8.25% sales tax

// Where the receipt's QR code sends customers. Replace this with your own
// survey link (for example a Google Forms link). The receipt number gets
// added to the end so you can tell which sale each answer came from.
final String SURVEY_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"; // ;)

// Each sale gets the next receipt number.
int nextReceiptNumber = 1001;

// The store's product list. Later this will come from a database;
// for now it's written right here in the code.
final List<Product> CATALOG = List.of(
    new Product("1001", "Coffee",        199),
    new Product("1002", "Tea",           179),
    new Product("1003", "Orange Juice",  325),
    new Product("2001", "Bagel",         249),
    new Product("2002", "Muffin",        299),
    new Product("2003", "Croissant",     275),
    new Product("3001", "Bottled Water", 129),
    new Product("3002", "Granola Bar",   159)
);

void main() {
    IO.println("=== REGISTER OPEN ===  (type 'list' to see products)");

    // The items rung up in the current sale, in the order they were scanned.
    List<Product> sale = new ArrayList<>();

    while (true) {
        String input = IO.readln("\nCode or name > ");
        if (input == null) break;           // keyboard closed: stop
        input = input.trim();
        if (input.isEmpty()) continue;      // just pressed Enter: ask again

        switch (input.toLowerCase()) {
            case "quit" -> { IO.println("Register closed."); return; }
            case "list" -> showCatalog();
            case "undo" -> undoLast(sale);
            case "done" -> {
                if (sale.isEmpty()) {
                    IO.println("Nothing rung up yet.");
                } else {
                    printReceipt(sale);
                    sale.clear();           // start a fresh sale
                    IO.println("\n=== NEXT CUSTOMER ===");
                }
            }
            default -> ringUp(input, sale);
        }
    }
}

// Find the product by code; if there's no exact code match, search names.
void ringUp(String input, List<Product> sale) {
    Product byCode = findByCode(input);
    if (byCode != null) {
        sale.add(byCode);
        IO.println("  + " + byCode.name() + "  " + money(byCode.priceCents())
                + "     (sale so far: " + money(subtotal(sale)) + ")");
        return;
    }

    List<Product> matches = searchByName(input);
    if (matches.isEmpty()) {
        IO.println("  No product with code or name \"" + input + "\". Type 'list' to see all.");
    } else if (matches.size() == 1) {
        Product p = matches.get(0);
        sale.add(p);
        IO.println("  + " + p.name() + "  " + money(p.priceCents())
                + "     (sale so far: " + money(subtotal(sale)) + ")");
    } else {
        IO.println("  More than one match. Type the code:");
        for (Product p : matches) {
            IO.println("    " + p.code() + "  " + p.name());
        }
    }
}

Product findByCode(String code) {
    for (Product p : CATALOG) {
        if (p.code().equals(code)) return p;
    }
    return null; // "null" means "nothing found"
}

List<Product> searchByName(String text) {
    List<Product> found = new ArrayList<>();
    for (Product p : CATALOG) {
        if (p.name().toLowerCase().contains(text.toLowerCase())) found.add(p);
    }
    return found;
}

void undoLast(List<Product> sale) {
    if (sale.isEmpty()) {
        IO.println("  Nothing to undo.");
    } else {
        Product removed = sale.remove(sale.size() - 1);
        IO.println("  - Removed " + removed.name() + "     (sale so far: " + money(subtotal(sale)) + ")");
    }
}

void showCatalog() {
    IO.println("  CODE  PRODUCT            PRICE");
    for (Product p : CATALOG) {
        IO.println(String.format("  %-5s %-16s %7s", p.code(), p.name(), money(p.priceCents())));
    }
}

int subtotal(List<Product> sale) {
    int sum = 0;
    for (Product p : sale) sum += p.priceCents();
    return sum;
}

void printReceipt(List<Product> sale) {
    // Group repeats so 3 coffees print as one line: "3 x Coffee"
    Map<Product, Integer> counts = new LinkedHashMap<>();
    for (Product p : sale) counts.merge(p, 1, Integer::sum);

    int subtotal = subtotal(sale);
    int tax = (int) Math.round(subtotal * TAX_RATE);
    int total = subtotal + tax;
    int receiptNumber = nextReceiptNumber;
    nextReceiptNumber++;

    IO.println("\n==============================");
    IO.println("          MY STORE            ");
    IO.println("        Receipt #" + receiptNumber);
    IO.println("==============================");
    for (var entry : counts.entrySet()) {
        Product p = entry.getKey();
        int qty = entry.getValue();
        String label = (qty > 1 ? qty + " x " : "") + p.name();
        IO.println(line(label, p.priceCents() * qty));
    }
    IO.println("------------------------------");
    IO.println(line("Subtotal", subtotal));
    IO.println(line(String.format("Tax (%.2f%%)", TAX_RATE * 100), tax));
    IO.println(line("TOTAL", total));
    IO.println("==============================");
    IO.println("     Thanks for shopping!     ");
    IO.println();
    IO.println("  How did we do? Scan to take");
    IO.println("     a quick survey:");
    printQrCode(surveyLink(receiptNumber));
}

// The survey link with this receipt's number on the end, e.g.
// https://example.com/survey?receipt=1001
String surveyLink(int receiptNumber) {
    String joiner = SURVEY_URL.contains("?") ? "&" : "?";
    return SURVEY_URL + joiner + "receipt=" + receiptNumber;
}

// Draws a QR code in the terminal.
// A QR code is a grid of dark and light squares ("modules"). Terminal
// letters are about twice as tall as they are wide, so each character
// shows TWO squares stacked: the top half and the bottom half.
//   both dark -> █    top dark -> ▀    bottom dark -> ▄    both light -> space
// It's printed black-on-white (the "\u001B[" color codes below) so it
// scans the same whether the terminal theme is dark or light.
void printQrCode(String text) {
    QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
    int border = 2; // blank margin around the code; scanners need it
    String blackOnWhite = "\u001B[30;47m";
    String reset = "\u001B[0m";

    for (int y = -border; y < qr.size + border; y += 2) {
        StringBuilder row = new StringBuilder("  " + blackOnWhite);
        for (int x = -border; x < qr.size + border; x++) {
            boolean top = isDark(qr, x, y);
            boolean bottom = isDark(qr, x, y + 1);
            if (top && bottom) row.append('█');
            else if (top) row.append('▀');
            else if (bottom) row.append('▄');
            else row.append(' ');
        }
        IO.println(row.append(reset));
    }
}

// Squares outside the code (the margin) count as light.
boolean isDark(QrCode qr, int x, int y) {
    return x >= 0 && y >= 0 && x < qr.size && y < qr.size && qr.getModule(x, y);
}

String money(int cents) {
    return String.format("$%d.%02d", cents / 100, cents % 100);
}

String line(String label, int cents) {
    return String.format("%-20s %9s", label, money(cents));
}
