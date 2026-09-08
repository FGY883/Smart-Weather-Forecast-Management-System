# Build Verification Skill

## Description
Verify frontend and backend builds for Spring Boot and Vue.js projects. This skill handles the repeated workflow of checking compilation and build status.

## Workflow

### 1. Frontend Build Verification
For Vue.js/React/Angular projects:
```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (if needed)
npm install

# Run build
npm run build 2>&1 | Select-Object -Last 5

# Check for errors
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed with errors"
} else {
    Write-Host "Build successful"
}
```

### 2. Backend Build Verification
For Spring Boot projects:
```bash
# Navigate to backend directory
cd backend/qidian-app

# Compile project
mvn compile -q 2>&1 | Select-Object -Last 5

# Check for errors
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed"
} else {
    Write-Host "Compilation successful"
}
```

### 3. Full Build Verification
```bash
# Frontend build
npm run build 2>&1 | Select-String -Pattern "built in|error"

# Backend compilation
mvn compile -q 2>&1 | Select-Object -Last 3
```

### 4. Error Handling
- Capture and display build errors
- Provide suggestions for common issues
- Log build results for debugging

## Example Usage
```
User: 验证前端构建
Agent: [Runs npm run build] → [Shows build results] → [Reports success/failure]
```

## Common Issues and Solutions
1. **Node modules missing**: Run `npm install`
2. **Java version mismatch**: Check JAVA_HOME environment variable
3. **Dependency conflicts**: Run `mvn clean install -DskipTests`
4. **Port conflicts**: Check if ports 3000/8080 are in use

## Technical Requirements
- Node.js 16+ for frontend builds
- Java 8+ for Spring Boot backend
- Maven 3.6+ for Java builds
- PowerShell on Windows

## Notes
- This skill handles the repeated pattern of build verification
- Supports both Windows and Linux environments
- Can be extended to support other build tools (Gradle, Yarn, etc.)
- Provides clear feedback on build status