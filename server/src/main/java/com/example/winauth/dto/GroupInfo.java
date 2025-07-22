package com.example.winauth.dto;

public class GroupInfo {
    private String name;
    private String fullName;
    private String sid;
    private String description;
    
    // Constructors
    public GroupInfo() {}
    
    public GroupInfo(String name, String fullName) {
        this.name = name;
        this.fullName = fullName;
    }
    
    public GroupInfo(String name, String fullName, String sid) {
        this.name = name;
        this.fullName = fullName;
        this.sid = sid;
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getSid() {
        return sid;
    }
    
    public void setSid(String sid) {
        this.sid = sid;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}