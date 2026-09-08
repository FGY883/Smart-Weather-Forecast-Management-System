# Word Document Generation

## Description
Generate professional Word documents (.docx) using python-docx library. This skill handles the repeated workflow of creating formatted documents.

## Workflow

### 1. Document Setup
```python
from docx import Document
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH

# Create document
doc = Document()

# Set default font
style = doc.styles['Normal']
font = style.font
font.name = 'SimSun'  # 宋体
font.size = Pt(10.5)  # 五号字
```

### 2. Content Structure
- **Title Page**: Project name, date, author
- **Table of Contents**: Auto-generated
- **Main Content**: Sections with headings and subheadings
- **Tables**: Formatted tables with headers
- **Code Blocks**: Syntax-highlighted code snippets
- **Images**: Embedded diagrams and screenshots

### 3. Formatting Rules
- **Headings**: Use Heading 1-3 styles
- **Paragraphs**: 1.5 line spacing
- **Tables**: Bordered with header rows
- **Code**: Monospace font (Consolas or Courier New)
- **Images**: Centered with captions

### 4. Advanced Features
```python
# Add table
table = doc.add_table(rows=3, cols=3)
table.style = 'Table Grid'

# Add code block
p = doc.add_paragraph()
run = p.add_run('code here')
run.font.name = 'Consolas'
run.font.size = Pt(9)

# Add image
doc.add_picture('image.png', width=Inches(6))
```

### 5. Save Document
```python
# Save to file
doc.save('output.docx')
```

## Example Usage
```
User: 生成技术文档
Agent: [Creates python script] → [Generates Word document] → [Saves to file]
```

## Common Templates
1. **Technical Architecture Document**
2. **Project Documentation**
3. **Experiment Report**
4. **API Documentation**
5. **User Manual**

## Technical Requirements
- Python 3.8+
- python-docx library (`pip install python-docx`)
- Write access to output directory

## Notes
- This skill handles the repeated pattern of Word document generation
- Supports Chinese fonts (SimSun, SimHei)
- Can generate complex documents with tables, images, and code
- Provides professional formatting and layout