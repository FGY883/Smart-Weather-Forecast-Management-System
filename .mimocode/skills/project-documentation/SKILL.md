# Project Documentation Generation

## Description
Generate comprehensive project documentation in Word format (.docx) based on project analysis. This skill handles the repeated workflow of analyzing project structure and generating professional documentation.

## Workflow

### 1. Project Analysis Phase
- Use Explore subagent to thoroughly analyze project structure
- Identify technology stack, architecture, database schema, API endpoints
- Read all key configuration files (package.json, pom.xml, application.yml, etc.)
- Analyze frontend routes and components
- Analyze backend services and business logic

### 2. Documentation Structure
Generate Word document with the following sections:
- **Project Overview**: Background, goals, tech stack
- **System Architecture**: Architecture diagrams, component relationships
- **Database Design**: Schema, relationships, indexes
- **API Documentation**: Endpoints, request/response formats
- **Frontend Implementation**: Pages, components, state management
- **Backend Implementation**: Services, business logic, algorithms
- **Deployment Guide**: Configuration, Docker, environment setup
- **Testing Strategy**: Test cases, coverage, automation

### 3. Word Document Generation
Use `python-docx` library to generate professional Word documents:
- Use SimSun (宋体) font, size 10.5pt (五号字)
- Include proper headings, tables, and formatting
- Add architecture diagrams using ASCII art or embedded images
- Include code snippets with syntax highlighting
- Generate table of contents automatically

### 4. Output Format
- File format: .docx
- Naming convention: `[ProjectName]_[DocumentationType]_[Date].docx`
- Save to user-specified location or default to project root

## Example Usage
```
User: 请基于整个项目的完整上下文，详细讲解技术方案与系统架构以及产品功能与交互设计
Agent: [Analyzes project] → [Generates Word document with technical architecture and product features]
```

## Technical Requirements
- Python 3.8+
- python-docx library (install with `pip install python-docx`)
- Read access to project files
- Write access to output directory

## Notes
- This skill handles the repeated pattern of project documentation generation
- Supports both Chinese and English documentation
- Can generate documentation for any project type (Spring Boot, Vue, React, etc.)
- Handles binary Word files (.doc) by converting to readable format first