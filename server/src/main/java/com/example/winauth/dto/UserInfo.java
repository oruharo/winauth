package com.example.winauth.dto;

import java.util.List;

public class UserInfo {
    private String username;
    private String fullName;
    private String domain;
    private String sid;
    private String email;
    private List<GroupInfo> groups;
    private String authenticationType;
    
    // Constructors
    public UserInfo() {}
    
    public UserInfo(String username, String domain) {
        this.username = username;
        this.domain = domain;
    }
    
    // Getters and Setters
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public String getSid() {
        return sid;
    }
    
    public void setSid(String sid) {
        this.sid = sid;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public List<GroupInfo> getGroups() {
        return groups;
    }
    
    public void setGroups(List<GroupInfo> groups) {
        this.groups = groups;
    }
    
    public String getAuthenticationType() {
        return authenticationType;
    }
    
    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }
}