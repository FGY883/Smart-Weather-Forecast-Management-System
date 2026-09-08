# MiMo Code Assets

This directory contains reusable skills, commands, and automation for the Spring Boot + Thymeleaf + AI weather forecast system.

## Skills

### 1. Project Documentation Generation
- **Path**: `skills/project-documentation/SKILL.md`
- **Description**: Generate comprehensive project documentation in Word format
- **Use Case**: Creating technical architecture documents, feature documentation, experiment reports

### 2. Build Verification
- **Path**: `skills/build-verification/SKILL.md`
- **Description**: Verify frontend and backend builds
- **Use Case**: Checking compilation status, verifying build success

### 3. Project Structure Exploration
- **Path**: `skills/project-exploration/SKILL.md`
- **Description**: Thoroughly explore and analyze project structure
- **Use Case**: Understanding project components, technology stack, architecture

### 4. Word Document Generation
- **Path**: `skills/word-document-generation/SKILL.md`
- **Description**: Generate professional Word documents using python-docx
- **Use Case**: Creating formatted documents with tables, code blocks, images

## Commands

### 1. Analyze Project
- **Path**: `commands/analyze-project.md`
- **Description**: Quick project analysis and summary report
- **Usage**: `/analyze-project [project_path]`

### 2. Generate Documentation
- **Path**: `commands/generate-docs.md`
- **Description**: Generate project documentation in Word format
- **Usage**: `/generate-docs [project_path] [output_path] [doc_type]`

### 3. Verify Build
- **Path**: `commands/verify-build.md`
- **Description**: Verify frontend and backend builds
- **Usage**: `/verify-build [build_type]`

### 4. Explore Project
- **Path**: `commands/explore-project.md`
- **Description**: Thoroughly explore project structure
- **Usage**: `/explore-project [project_path]`

## Repeated Workflows Packaged

Based on analysis of 183 sessions in the last 30 days, the following repeated workflows were identified and packaged:

1. **Project Documentation Generation**: Multiple sessions asking to analyze projects and generate Word documents
2. **Build Verification**: Repeated `npm run build` and `mvn compile` commands
3. **Project Structure Exploration**: Using Explore subagent to understand project structure
4. **Word Document Generation**: Using python-docx to generate formatted documents

## Notes

- All assets are designed for Spring Boot + Vue.js projects
- Support both Chinese and English documentation
- Can be extended to other project types
- Follow MiMo Code conventions and best practices