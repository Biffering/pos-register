// The checkout window (Step 3), with saved products and sales (Step 4).
//
// Run it with:   java Checkout.java
// (run it from this folder, so it finds products.csv)
//
// - Click a product button to add it to the sale.
// - Or type a product code in the box at the top and press Enter.
//   (A USB barcode scanner works the same way: it "types" the code + Enter.)
// - Select a line and click "Remove One" to take one off.
// - Click PAY, enter the cash the customer handed you, and the register
//   works out the change and shows the receipt with its QR code.
//
// Saved between runs:
// - products.csv      the products for sale. Edit it in any text editor or
//                     spreadsheet, then restart the register.
// - data/sales.csv    one line per finished sale. Receipt numbers carry on
//                     from the last sale in here.
//
// This window is built with Swing, the window toolkit that comes with Java.

import io.nayuki.qrcodegen.QrCode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List; // (java.awt has a "List" too; this says we mean the java.util one)
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// ---------- Store settings: change these to fit your store ----------

record Product(String code, String name, int priceCents) {}

final String STORE_NAME = "MY STORE";
final double TAX_RATE = 0.0825; // 8.25% sales tax
final String SURVEY_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"; // ;)

// Where the register keeps its files (inside the folder you run it from).
final Path PRODUCTS_FILE = Path.of("products.csv");
final Path SALES_FILE = Path.of("data", "sales.csv");
final int FIRST_RECEIPT_NUMBER = 1001;

// ---------- What the register is keeping track of ----------

// The products for sale, loaded from products.csv when the register starts.
List<Product> CATALOG;

// The current sale: each product and how many of it.
// A LinkedHashMap remembers the order things were added.
final Map<Product, Integer> sale = new LinkedHashMap<>();
int nextReceiptNumber;

// Parts of the window we need to update as the sale changes.
JFrame window;
JTextField codeBox;
DefaultTableModel saleRows;
JTable saleTable;
JLabel subtotalLabel = new JLabel();
JLabel taxLabel = new JLabel();
JLabel totalLabel = new JLabel();

final Font BIG = new Font(Font.SANS_SERIF, Font.BOLD, 22);
final Font NORMAL = new Font(Font.SANS_SERIF, Font.PLAIN, 18);

void main() {
    // Make pop-up boxes (Pay, messages) big enough to tap on a touchscreen.
    UIManager.put("OptionPane.messageFont", NORMAL);
    UIManager.put("OptionPane.buttonFont", BIG);
    UIManager.put("TextField.font", BIG);
    UIManager.put("Button.font", NORMAL);

    // Load the saved products, and find where receipt numbers left off.
    try {
        CATALOG = loadProducts();
        nextReceiptNumber = findNextReceiptNumber();
    } catch (IOException | IllegalArgumentException e) {
        JOptionPane.showMessageDialog(null, "The register can't start:\n" + e.getMessage(),
                "Problem with saved files", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    // Swing wants windows built on its own "event thread"; invokeLater does that.
    SwingUtilities.invokeLater(this::buildWindow);
}

// ---------- Saved files: products and sales ----------

// Reads products.csv. Each line is:  code,name,price   for example
//   1001,Coffee,1.99
// The first line is a header and is skipped, as are blank lines and lines
// starting with #. If the file doesn't exist yet, it's created with a
// starter list so there's something to edit.
List<Product> loadProducts() throws IOException {
    if (!Files.exists(PRODUCTS_FILE)) {
        Files.writeString(PRODUCTS_FILE, """
                code,name,price
                1001,Coffee,1.99
                1002,Tea,1.79
                1003,Orange Juice,3.25
                2001,Bagel,2.49
                2002,Muffin,2.99
                2003,Croissant,2.75
                3001,Bottled Water,1.29
                3002,Granola Bar,1.59
                """);
    }

    List<Product> products = new ArrayList<>();
    Set<String> codesSeen = new HashSet<>();
    List<String> lines = Files.readAllLines(PRODUCTS_FILE);
    for (int i = 1; i < lines.size(); i++) {          // i = 1 skips the header
        String line = lines.get(i).trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int lineNumber = i + 1;                       // what a text editor would show

        // The code is before the first comma and the price after the last one,
        // so a name like "Muffin, Blueberry" still works.
        int first = line.indexOf(',');
        int last = line.lastIndexOf(',');
        if (first < 0 || first == last) {
            throw new IllegalArgumentException(PRODUCTS_FILE + " line " + lineNumber
                    + " should look like  1001,Coffee,1.99  but is:\n" + line);
        }
        String code = line.substring(0, first).trim();
        String name = line.substring(first + 1, last).trim();
        int price = parseCents(line.substring(last + 1));
        if (code.isEmpty() || name.isEmpty() || price < 0) {
            throw new IllegalArgumentException(PRODUCTS_FILE + " line " + lineNumber
                    + " has a missing code or name, or a price that isn't a dollar amount:\n" + line);
        }
        if (!codesSeen.add(code)) {
            throw new IllegalArgumentException(PRODUCTS_FILE + " line " + lineNumber
                    + " uses code " + code + ", which is already used by another product.");
        }
        products.add(new Product(code, name, price));
    }
    if (products.isEmpty()) {
        throw new IllegalArgumentException(PRODUCTS_FILE + " has no products in it.");
    }
    return products;
}

// Looks at the last saved sale and returns the number after it.
int findNextReceiptNumber() throws IOException {
    if (!Files.exists(SALES_FILE)) return FIRST_RECEIPT_NUMBER;
    int highest = FIRST_RECEIPT_NUMBER - 1;
    for (String line : Files.readAllLines(SALES_FILE)) {
        String firstColumn = line.split(",", 2)[0].trim();
        try {
            highest = Math.max(highest, Integer.parseInt(firstColumn));
        } catch (NumberFormatException e) {
            // the header line, or something that isn't a sale: skip it
        }
    }
    return highest + 1;
}

// Adds one line to data/sales.csv for a finished sale, for example:
//   1001,2026-11-24 09:15:02,2x1001;1x2001,647,53,700,1000,300
// Items are "how many x product code", and money is in cents, like
// everywhere else in this program.
void saveSale(int receiptNumber, int subtotal, int tax, int total, int cash) throws IOException {
    StringBuilder items = new StringBuilder();
    for (var line : sale.entrySet()) {
        if (!items.isEmpty()) items.append(';');
        items.append(line.getValue()).append('x').append(line.getKey().code());
    }
    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String row = String.join(",", String.valueOf(receiptNumber), time, items.toString(),
            String.valueOf(subtotal), String.valueOf(tax), String.valueOf(total),
            String.valueOf(cash), String.valueOf(cash - total));

    Files.createDirectories(SALES_FILE.getParent());
    if (!Files.exists(SALES_FILE)) {
        Files.writeString(SALES_FILE, "receipt,time,items,subtotal_cents,tax_cents,total_cents,cash_cents,change_cents\n");
    }
    // APPEND adds to the end of the file instead of replacing it.
    Files.writeString(SALES_FILE, row + "\n", StandardOpenOption.APPEND);
}

// ---------- Building the window ----------

void buildWindow() {
    window = new JFrame(STORE_NAME + " - Register");
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel main = new JPanel(new BorderLayout(12, 12));
    main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    window.setContentPane(main);

    // TOP: the code box
    JPanel top = new JPanel(new BorderLayout(8, 8));
    JLabel scanLabel = new JLabel("Scan or type code:");
    scanLabel.setFont(BIG);
    codeBox = new JTextField();
    codeBox.setFont(BIG);
    codeBox.addActionListener(e -> addByCode()); // runs when Enter is pressed
    top.add(scanLabel, BorderLayout.WEST);
    top.add(codeBox, BorderLayout.CENTER);
    main.add(top, BorderLayout.NORTH);

    // LEFT: one big button per product
    JPanel productButtons = new JPanel(new GridLayout(0, 2, 8, 8));
    for (Product p : CATALOG) {
        JButton b = new JButton("<html><center>" + p.name() + "<br>" + money(p.priceCents()) + "</center></html>");
        b.setFont(NORMAL);
        b.setPreferredSize(new Dimension(180, 80));
        b.setToolTipText("Code " + p.code());
        b.addActionListener(e -> addItem(p));
        productButtons.add(b);
    }
    JPanel left = new JPanel(new BorderLayout());
    left.add(productButtons, BorderLayout.NORTH); // NORTH keeps buttons from stretching tall
    main.add(left, BorderLayout.WEST);

    // RIGHT: the sale list
    saleRows = new DefaultTableModel(new Object[] {"Item", "Qty", "Price"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    saleTable = new JTable(saleRows);
    saleTable.setFont(NORMAL);
    saleTable.setRowHeight(32);
    saleTable.getTableHeader().setFont(BIG);
    saleTable.getColumnModel().getColumn(0).setPreferredWidth(260);

    // Totals
    JPanel totals = new JPanel(new GridLayout(3, 2, 4, 4));
    for (JLabel l : List.of(subtotalLabel, taxLabel)) l.setFont(NORMAL);
    totalLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
    totals.add(label("Subtotal", NORMAL));  totals.add(subtotalLabel);
    totals.add(label(String.format("Tax (%.2f%%)", TAX_RATE * 100), NORMAL)); totals.add(taxLabel);
    totals.add(label("TOTAL", new Font(Font.SANS_SERIF, Font.BOLD, 30))); totals.add(totalLabel);
    for (JLabel l : List.of(subtotalLabel, taxLabel, totalLabel)) l.setHorizontalAlignment(SwingConstants.RIGHT);

    // Action buttons
    JButton removeButton = new JButton("Remove One");
    JButton clearButton = new JButton("Clear Sale");
    JButton payButton = new JButton("PAY");
    removeButton.addActionListener(e -> removeOne());
    clearButton.addActionListener(e -> clearSale());
    payButton.addActionListener(e -> pay());
    for (JButton b : List.of(removeButton, clearButton)) b.setFont(NORMAL);
    payButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    payButton.setBackground(new Color(0x2E7D32)); // green
    payButton.setForeground(Color.WHITE);
    payButton.setPreferredSize(new Dimension(200, 70));

    JPanel actions = new JPanel(new GridLayout(1, 3, 8, 8));
    actions.add(removeButton);
    actions.add(clearButton);
    actions.add(payButton);

    JPanel bottomRight = new JPanel(new BorderLayout(8, 12));
    bottomRight.add(totals, BorderLayout.CENTER);
    bottomRight.add(actions, BorderLayout.SOUTH);

    JPanel right = new JPanel(new BorderLayout(8, 12));
    right.add(new JScrollPane(saleTable), BorderLayout.CENTER);
    right.add(bottomRight, BorderLayout.SOUTH);
    main.add(right, BorderLayout.CENTER);

    refresh();
    window.setSize(1200, 720);
    window.setLocationRelativeTo(null); // center on screen
    window.setVisible(true);
    codeBox.requestFocusInWindow();
}

JLabel label(String text, Font font) {
    JLabel l = new JLabel(text);
    l.setFont(font);
    return l;
}

// ---------- Ringing items up ----------

void addByCode() {
    String code = codeBox.getText().trim();
    codeBox.setText("");
    if (code.isEmpty()) return;
    for (Product p : CATALOG) {
        if (p.code().equals(code)) {
            addItem(p);
            return;
        }
    }
    Toolkit.getDefaultToolkit().beep();
    JOptionPane.showMessageDialog(window, "No product with code \"" + code + "\".",
            "Not found", JOptionPane.WARNING_MESSAGE);
    codeBox.requestFocusInWindow();
}

void addItem(Product p) {
    sale.merge(p, 1, Integer::sum); // add 1 to its count (or start at 1)
    refresh();
    saleTable.clearSelection();
    codeBox.requestFocusInWindow();  // ready for the next scan
}

// Removes one of the selected line, or of the last line if none is selected.
void removeOne() {
    if (sale.isEmpty()) return;
    List<Product> lines = new ArrayList<>(sale.keySet());
    int row = saleTable.getSelectedRow();
    Product p = (row >= 0) ? lines.get(row) : lines.get(lines.size() - 1);
    int qty = sale.get(p);
    if (qty > 1) sale.put(p, qty - 1);
    else sale.remove(p);
    refresh();
    // keep the same line selected so you can click Remove again
    int newRow = new ArrayList<>(sale.keySet()).indexOf(p);
    if (newRow >= 0 && row >= 0) saleTable.setRowSelectionInterval(newRow, newRow);
    codeBox.requestFocusInWindow();
}

void clearSale() {
    if (sale.isEmpty()) return;
    int answer = JOptionPane.showConfirmDialog(window, "Clear the whole sale?",
            "Clear Sale", JOptionPane.YES_NO_OPTION);
    if (answer == JOptionPane.YES_OPTION) {
        sale.clear();
        refresh();
    }
    codeBox.requestFocusInWindow();
}

// Redraws the sale list and totals from the "sale" map.
void refresh() {
    saleRows.setRowCount(0);
    for (var line : sale.entrySet()) {
        Product p = line.getKey();
        int qty = line.getValue();
        saleRows.addRow(new Object[] {p.name(), qty, money(p.priceCents() * qty)});
    }
    int subtotal = subtotal();
    int tax = tax(subtotal);
    subtotalLabel.setText(money(subtotal));
    taxLabel.setText(money(tax));
    totalLabel.setText(money(subtotal + tax));
}

int subtotal() {
    int sum = 0;
    for (var line : sale.entrySet()) sum += line.getKey().priceCents() * line.getValue();
    return sum;
}

int tax(int subtotal) {
    return (int) Math.round(subtotal * TAX_RATE);
}

// ---------- Paying ----------

void pay() {
    if (sale.isEmpty()) {
        JOptionPane.showMessageDialog(window, "Ring something up first.");
        return;
    }
    int subtotal = subtotal();
    int tax = tax(subtotal);
    int total = subtotal + tax;

    // Keep asking until we get a valid amount (or the cashier clicks Cancel).
    int cash;
    while (true) {
        String typed = JOptionPane.showInputDialog(window,
                "Total due: " + money(total) + "\n\nCash received (for example 10 or 10.50):",
                "Pay - Cash", JOptionPane.QUESTION_MESSAGE);
        if (typed == null) { codeBox.requestFocusInWindow(); return; } // Cancel
        cash = parseCents(typed);
        if (cash < 0) {
            JOptionPane.showMessageDialog(window, "\"" + typed + "\" isn't a dollar amount.");
        } else if (cash < total) {
            JOptionPane.showMessageDialog(window, "That's " + money(total - cash) + " short.");
        } else {
            break;
        }
    }

    int receiptNumber = nextReceiptNumber;
    nextReceiptNumber++;
    try {
        saveSale(receiptNumber, subtotal, tax, total, cash);
    } catch (IOException e) {
        JOptionPane.showMessageDialog(window,
                "The sale went through, but it couldn't be saved to " + SALES_FILE + ":\n" + e.getMessage(),
                "Sale not saved", JOptionPane.WARNING_MESSAGE);
    }
    showReceipt(receiptNumber, receiptText(receiptNumber, subtotal, tax, total, cash));

    sale.clear(); // ready for the next customer
    refresh();
    codeBox.requestFocusInWindow();
}

// "10" -> 1000, "10.5" -> 1050, "$4.85" -> 485. Returns -1 if it isn't money.
// BigDecimal does exact decimal math (no 0.30000000000000004 surprises).
int parseCents(String typed) {
    try {
        BigDecimal amount = new BigDecimal(typed.trim().replace("$", ""));
        if (amount.signum() < 0 || amount.scale() > 2) return -1;
        return amount.movePointRight(2).intValueExact();
    } catch (NumberFormatException | ArithmeticException e) {
        return -1;
    }
}

// ---------- The receipt ----------

String receiptText(int number, int subtotal, int tax, int total, int cash) {
    StringBuilder r = new StringBuilder();
    r.append("==============================\n");
    r.append(center(STORE_NAME)).append('\n');
    r.append(center("Receipt #" + number)).append('\n');
    r.append("==============================\n");
    for (var line : sale.entrySet()) {
        Product p = line.getKey();
        int qty = line.getValue();
        String name = (qty > 1 ? qty + " x " : "") + p.name();
        r.append(line(name, p.priceCents() * qty));
    }
    r.append("------------------------------\n");
    r.append(line("Subtotal", subtotal));
    r.append(line(String.format("Tax (%.2f%%)", TAX_RATE * 100), tax));
    r.append(line("TOTAL", total));
    r.append(line("Cash", cash));
    r.append(line("CHANGE", cash - total));
    r.append("==============================\n");
    r.append(center("Thanks for shopping!"));
    return r.toString();
}

void showReceipt(int number, String text) {
    JTextArea receipt = new JTextArea(text);
    receipt.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
    receipt.setEditable(false);

    JLabel scanMe = new JLabel("How did we do? Scan to take a quick survey:", SwingConstants.CENTER);
    scanMe.setFont(NORMAL);
    JLabel qr = new JLabel(new ImageIcon(qrImage(surveyLink(number), 6)));

    JPanel survey = new JPanel(new BorderLayout(4, 8));
    survey.add(scanMe, BorderLayout.NORTH);
    survey.add(qr, BorderLayout.CENTER);

    JPanel panel = new JPanel(new BorderLayout(8, 12));
    panel.add(receipt, BorderLayout.CENTER);
    panel.add(survey, BorderLayout.SOUTH);
    JOptionPane.showMessageDialog(window, panel, "Receipt #" + number, JOptionPane.PLAIN_MESSAGE);
}

String surveyLink(int receiptNumber) {
    String joiner = SURVEY_URL.contains("?") ? "&" : "?";
    return SURVEY_URL + joiner + "receipt=" + receiptNumber;
}

// Draws the QR code as a picture: black squares on white.
// "scale" is how many pixels wide each square is.
BufferedImage qrImage(String text, int scale) {
    QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
    int border = 4; // the blank margin scanners need, in squares
    int pixels = (qr.size + border * 2) * scale;
    BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, pixels, pixels);
    g.setColor(Color.BLACK);
    for (int y = 0; y < qr.size; y++) {
        for (int x = 0; x < qr.size; x++) {
            if (qr.getModule(x, y)) {
                g.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
            }
        }
    }
    g.dispose();
    return img;
}

// ---------- Little helpers ----------

String money(int cents) {
    return String.format("$%d.%02d", cents / 100, cents % 100);
}

String line(String label, int cents) {
    return String.format("%-20s %9s%n", label, money(cents));
}

String center(String text) {
    int pad = Math.max(0, (30 - text.length()) / 2);
    return " ".repeat(pad) + text;
}
