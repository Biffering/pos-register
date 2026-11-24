# POS Register

A simple point-of-sale (cash register) system written in Java, built step by
step as a learning project and run on **GhostBSD** (FreeBSD) in a virtual machine.

It works on any computer with Java 25 or newer: Linux, BSD, Windows, or macOS.

## Features

- Checkout window with product buttons, a running total, and sales tax
- **Barcode-scanner ready:** scanners type the code into the code box like a keyboard
- Remove items, clear a sale, and take cash payments with change worked out
- Numbered receipts with a QR code customers can scan to take a quick survey
- Products are kept in an editable `products.csv`; every sale is saved to `data/sales.csv`
- A terminal version for when no screen is attached

## Run it

Install Java 25 or newer, download this project, then from its folder run:

```bash
java Checkout.java
```

No build tools needed; Java compiles and runs it in one step.

Other versions from earlier steps:

| Command | What it is |
|---|---|
| `java Checkout.java` | Steps 3 and 4: the checkout window, with saved products and sales |
| `java Register.java` | Step 2: the terminal register (type codes or names) |
| `java Step1Receipt.java` | Step 1: prints one sample receipt |

## Try a sale

1. Click **Coffee** and **Bagel**, or type `1001` in the code box and press Enter
2. Click **PAY** and enter `10`
3. Scan the QR code on the receipt with your phone to take the survey

## Make it your own

**Products** are in `products.csv`. Open it in a text editor or spreadsheet,
one product per line, then restart the register:

```
code,name,price
1001,Coffee,1.99
```

Near the top of `Checkout.java`:

- `STORE_NAME`: your store's name on the receipt
- `TAX_RATE`: your sales tax (`0.0825` = 8.25%)
- `SURVEY_URL`: the link the receipt's QR code opens

(The terminal version, `Register.java`, keeps its own product list and settings
near the top of its file.)

## Saved sales

Every finished sale adds a line to `data/sales.csv`, which opens in any
spreadsheet app. Receipt numbers continue from the last saved sale, even after
a restart. Money is recorded in cents (`700` = $7.00), and items as
"how many x code" (`2x1001` = two Coffees).

The `data/` folder is never uploaded to GitHub, so real sales stay private.

## How it works

- **Money is stored in whole cents.** Computers can't store some decimals
  exactly (0.1 + 0.2 = 0.30000000000000004), so counting cents keeps every
  total exact.
- **The window** uses Swing, the toolkit built into Java.
- **QR codes** are made by the [QR Code generator library](https://www.nayuki.io/page/qr-code-generator-library)
  by Project Nayuki (MIT License), included in `io/nayuki/qrcodegen/`.

## Roadmap

- [x] Step 1: add up items and print a receipt
- [x] Step 2: product list, look items up by code or name
- [x] Step 3: checkout window
- [x] Step 4: save products and sales to files
- [ ] Step 5: daily sales report, receipt printer support
