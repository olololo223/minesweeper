package com.example.minesweeper.model;

public class User {
    public long id;
    public String username;
    public String role;
    public String passwordHash;   // ← добавить

    public User(long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }
}