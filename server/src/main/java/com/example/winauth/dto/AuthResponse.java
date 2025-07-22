package com.example.winauth.dto;

import java.util.List;

public class AuthResponse {
    private boolean success;
    private String message;
    private UserInfo userInfo;
    private String errorCode;
    
    // Constructors
    public AuthResponse() {}
    
    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public AuthResponse(boolean success, String message, UserInfo userInfo) {
        this.success = success;
        this.message = message;
        this.userInfo = userInfo;
    }
    
    public AuthResponse(boolean success, String message, String errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public UserInfo getUserInfo() {
        return userInfo;
    }
    
    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    // Static factory methods
    public static AuthResponse success(String message, UserInfo userInfo) {
        return new AuthResponse(true, message, userInfo);
    }
    
    public static AuthResponse failure(String message, String errorCode) {
        return new AuthResponse(false, message, errorCode);
    }
    
    public static AuthResponse failure(String message) {
        return new AuthResponse(false, message);
    }
}