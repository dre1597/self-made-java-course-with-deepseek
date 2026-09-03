package br.com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Main {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (PreparedStatement create = conn.prepareStatement("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT)")) {
                create.executeUpdate();
            }

            try (PreparedStatement insert = conn.prepareStatement("INSERT INTO products (name) VALUES (?)")) {
                insert.setString(1, "caneta");
                insert.executeUpdate();
            }

            try (PreparedStatement query = conn.prepareStatement("SELECT id, name FROM products");
                 ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getInt("id") + ": " + rs.getString("name"));
                }
            }
        }
    }
}