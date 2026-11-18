import java.sql.*;
import java.util.Scanner;

public class InventoryApp {

    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection con = DBConnection.getConnection()) {
            System.out.println("Connected to retail_db successfully.");
            int choice;
            do {
                printMenu();
                choice = readInt("Enter your choice: ");
                switch (choice) {
                    case 1 -> insertSampleProducts(con);
                    case 2 -> insertCustomProduct(con);
                    case 3 -> retrieveByCategory(con);
                    case 4 -> updateStock(con);
                    case 5 -> updatePrice(con);
                    case 6 -> inventoryValueAndDelete(con);
                    case 7 -> System.out.println("Exiting... Goodbye!");
                    default -> System.out.println("Invalid choice. Try again.");
                }
            } while (choice != 7);
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
    }

    static void printMenu() {
        System.out.println("\n===== INVENTORY MANAGEMENT SYSTEM =====");
        System.out.println("1. Insert 3 sample products");
        System.out.println("2. Insert a new product (manual entry)");
        System.out.println("3. Show low-stock products by category (qty < 20, price DESC)");
        System.out.println("4. Update product stock (add quantity)");
        System.out.println("5. Search & update product price");
        System.out.println("6. Total inventory value & delete product (qty = 0)");
        System.out.println("7. Exit");
    }

    // ---------- (a) INSERT ----------
    static void insertProduct(Connection con, int id, String name, String cat, int qty, double price) {
        String sql = "INSERT INTO Product (product_id, product_name, category, quantity, price) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, cat);
            ps.setInt(4, qty);
            ps.setDouble(5, price);
            ps.executeUpdate();
            System.out.println("Inserted product ID " + id + " (" + name + ")");
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Duplicate product ID " + id + " - record skipped.");
        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    static void insertSampleProducts(Connection con) {
        insertProduct(con, 101, "Notebook", "Stationery", 50, 45.0);
        insertProduct(con, 102, "Pen Pack", "Stationery", 10, 120.0);
        insertProduct(con, 103, "USB Cable", "Electronics", 15, 250.0);
        insertProduct(con, 104, "Mouse", "Electronics", 0, 499.0);
    }

    static void insertCustomProduct(Connection con) {
        int id = readInt("Product ID: ");
        System.out.print("Product name: ");
        String name = sc.nextLine();
        System.out.print("Category: ");
        String cat = sc.nextLine();
        int qty = readInt("Quantity: ");
        double price = readDouble("Price: ");
        insertProduct(con, id, name, cat, qty, price);
    }

    // ---------- (b) CONDITIONAL RETRIEVAL + SORT ----------
    static void retrieveByCategory(Connection con) {
        System.out.print("Enter category: ");
        String cat = sc.nextLine();
        String sql = "SELECT * FROM Product WHERE category = ? AND quantity < 20 ORDER BY price DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cat);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                printHeader();
                while (rs.next()) {
                    printRow(rs);
                    found = true;
                }
                if (!found) System.out.println("No products found in '" + cat + "' with quantity < 20.");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------- (c) UPDATE STOCK ----------
    static void updateStock(Connection con) {
        int id = readInt("Product ID: ");
        int add = readInt("Quantity to add: ");
        if (add <= 0) {
            System.out.println("Quantity to add must be greater than zero.");
            return;
        }
        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, add);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows > 0) System.out.println("Stock updated successfully for product ID " + id);
            else System.out.println("Product with ID " + id + " does not exist.");
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // ---------- (d) SEARCH & MODIFY PRICE ----------
    static void updatePrice(Connection con) {
        int id = readInt("Product ID: ");
        double newPrice = readDouble("New price: ");
        String select = "SELECT * FROM Product WHERE product_id = ?";
        String update = "UPDATE Product SET price = ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
                System.out.println("Current details:");
                printHeader();
                printRow(rs);
            }
            if (newPrice <= 0) {
                System.out.println("New price must be greater than zero. No update done.");
                return;
            }
            try (PreparedStatement up = con.prepareStatement(update)) {
                up.setDouble(1, newPrice);
                up.setInt(2, id);
                up.executeUpdate();
            }
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Updated details:");
                printHeader();
                if (rs.next()) printRow(rs);
            }
        } catch (SQLException e) {
            System.out.println("Operation failed: " + e.getMessage());
        }
    }

    // ---------- (e) AGGREGATE + CONDITIONAL DELETE ----------
    static void inventoryValueAndDelete(Connection con) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(quantity * price) AS total FROM Product")) {
            if (rs.next()) System.out.printf("Total inventory value: %.2f%n", rs.getDouble("total"));
        } catch (SQLException e) {
            System.out.println("Aggregate query failed: " + e.getMessage());
            return;
        }

        int id = readInt("Enter product ID to delete: ");
        String select = "SELECT quantity FROM Product WHERE product_id = ?";
        String delete = "DELETE FROM Product WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
                int qty = rs.getInt("quantity");
                if (qty != 0) {
                    System.out.println("Cannot delete: product still has stock (quantity = " + qty + ").");
                    return;
                }
            }
            try (PreparedStatement del = con.prepareStatement(delete)) {
                del.setInt(1, id);
                del.executeUpdate();
                System.out.println("Product ID " + id + " deleted successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }

    // ---------- HELPERS ----------
    static void printHeader() {
        System.out.printf("%-6s %-20s %-15s %-8s %-10s%n", "ID", "Name", "Category", "Qty", "Price");
        System.out.println("-------------------------------------------------------------");
    }

    static void printRow(ResultSet rs) throws SQLException {
        System.out.printf("%-6d %-20s %-15s %-8d %-10.2f%n",
                rs.getInt("product_id"), rs.getString("product_name"),
                rs.getString("category"), rs.getInt("quantity"), rs.getDouble("price"));
    }

    static int readInt(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    static double readDouble(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}