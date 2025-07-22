# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WinAuth is a Windows Authentication Server that provides both AD/LDAP and Kerberos/SPNEGO authentication capabilities. The project consists of a Spring Boot backend (Java) and a React frontend (TypeScript).

## Commands

### Server (Spring Boot) - Port 8082
```bash
# Build
mvn clean package

# Run (AD/LDAP mode - default)
mvn spring-boot:run

# Run (Kerberos mode)
mvn spring-boot:run -Dspring.profiles.active=kerberos

# Run tests
mvn test

# Install dependencies
mvn dependency:resolve
```

### Client (React + Vite) - Port 5173
```bash
# Install dependencies
npm install

# Development server
npm run dev

# Build for production
npm run build

# Lint check
npm run lint

# Preview production build
npm run preview
```

### Docker & Kubernetes
```bash
# Build Docker image (server)
cd server && docker build -t winauth-server .

# Deploy to Kubernetes
kubectl apply -f k8s/
```

## Architecture

### Authentication Flow
The system supports two authentication modes:
1. **AD/LDAP**: Username/password authentication against Active Directory
2. **Kerberos/SPNEGO**: Single Sign-On for domain-joined clients

### Core Components

**Server Structure:**
- `com.example.adauth.config/` - Security and authentication configurations
  - `SecurityConfig.java` - Main Spring Security configuration
  - `KerberosConfig.java` - Kerberos-specific configuration
  - `KerberosUserDetailsService.java` - User details extraction from Kerberos
  - `CustomSpnegoFilter.java` - SPNEGO token processing
- `com.example.adauth.controller/` - REST API endpoints
- `com.example.adauth.dto/` - Data transfer objects
- Configuration profiles in `src/main/resources/`:
  - `application.properties` - Default AD/LDAP settings
  - `application-kerberos.properties` - Kerberos profile settings

**Client Structure:**
- Vite-based React application with TypeScript
- `src/services/authService.ts` - Authentication API client
- Proxy configuration in `vite.config.ts` forwards `/api` to backend

### Key Integration Points
- Frontend communicates with backend via `/api/*` endpoints
- Vite dev server proxies requests to Spring Boot (port 8082)
- CORS configuration allows cross-origin requests during development
- Authentication state managed through browser sessions/cookies

## Development Notes

### Working with Authentication Modes
- Default profile uses AD/LDAP with username/password
- Kerberos profile requires keytab file and proper domain configuration
- Check `KERBEROS_SETUP*.md` files for environment-specific setup

### Testing Authentication
- Use the React UI at http://localhost:5173 for interactive testing
- REST API endpoints available at http://localhost:8082/api/*
- Both modes support the same API interface for compatibility