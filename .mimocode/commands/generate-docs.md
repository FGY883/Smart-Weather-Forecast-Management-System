# Generate Documentation Command

## Description
Generate comprehensive project documentation in Word format.

## Usage
```
/generate-docs [project_path] [output_path] [doc_type]
```

## Parameters
- `project_path`: Optional path to project directory (default: current directory)
- `output_path`: Optional output path for Word document (default: project root)
- `doc_type`: Documentation type (technical, feature, experiment, api)

## Documentation Types
1. **technical**: Technical architecture and system design
2. **feature**: Product features and interaction design
3. **experiment**: Experiment report and lab documentation
4. **api**: API documentation and endpoints

## Workflow
1. Analyze project structure
2. Generate documentation content
3. Create Word document with python-docx
4. Save to specified location

## Example
```
/generate-docs . . technical
```

## Output
- Word document (.docx)
- Professional formatting with SimSun font
- Tables, diagrams, and code snippets
- Table of contents

## Notes
- This command handles repeated documentation generation
- Supports multiple documentation types
- Generates professional Word documents