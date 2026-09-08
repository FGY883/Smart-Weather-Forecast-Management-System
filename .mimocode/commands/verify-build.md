# Verify Build Command

## Description
Verify frontend and backend builds for the project.

## Usage
```
/verify-build [build_type]
```

## Parameters
- `build_type`: Build type (frontend, backend, all)

## Build Types
1. **frontend**: Verify frontend build (npm run build)
2. **backend**: Verify backend compilation (mvn compile)
3. **all**: Verify both frontend and backend builds

## Workflow
1. Check project structure
2. Run appropriate build commands
3. Capture and analyze output
4. Report success/failure

## Example
```
/verify-build all
```

## Output
- Build status (success/failure)
- Error messages (if any)
- Build time and statistics

## Common Issues
- **Node modules missing**: Run `npm install`
- **Java version mismatch**: Check JAVA_HOME
- **Dependency conflicts**: Run `mvn clean install -DskipTests`
- **Port conflicts**: Check if ports are in use

## Notes
- This command handles repeated build verification
- Supports both Windows and Linux
- Provides clear feedback on build status