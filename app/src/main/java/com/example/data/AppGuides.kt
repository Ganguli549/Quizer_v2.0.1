package com.example.data

object AppGuides {
    val guideBookContent = """
Quizer App - Comprehensive User Guide

Welcome to Quizer, an advanced, offline-first quiz preparation and learning platform!

1. Home & Dashboard
- Active Book Selector: Switch seamlessly between different study subjects or loaded books using the dropdown menu at the top.
- Resume Active Quiz: If you left a quiz midway, this button lets you return directly to where you left off without losing your progress.
- Start New Quiz: Initiates a new quiz session with the default or currently selected settings.
- Advanced Quiz Options: Fine-tune your quizzes. You can select specific topics, set question limits, or choose to only be quizzed on bookmarked items or questions you previously got wrong.
- Recent History: Quickly glance at your recent quiz scores to track your progress over time.

2. Content Explorer & Search
- Full Content Browsing: See all questions available in the active book.
- Search & Filter: Use the search bar to find specific keywords, exams, or years. The search is intelligent and works across multiple fields (e.g., "History 2022").
- Multi-Select Actions: Tap the checkboxes next to individual questions. Once selected, you can start a custom "Practice" quiz, "Review" them, or delete them if the book is editable.
- Path Navigation: Questions are grouped by their hierarchical paths (e.g., Subject > Chapter > Topic). You can filter questions by selecting a specific folder in the path view.

3. Quiz Screen
- Timer & Flow: If enabled, a timer tracks your speed. "Question Flow" automatically advances to the next question when you answer.
- Interactive Grid: Tap the grid icon at the top to see a visual map of the quiz. Jump directly to any question, and easily spot which ones are answered, skipped, or bookmarked.
- Bookmarks: Tap the bookmark icon to save a question for later review.
- Submission & Analytics: Submitting a quiz provides instant feedback. The app records correct, incorrect, and skipped responses, which feeds into your mastery statistics.

4. Results & Review
- Comprehensive Analysis: After a quiz, view your total score, accuracy percentage, and time taken.
- Topic Breakdown: See exactly which topics you performed well in and which need more work.
- Detailed Review: Go through all the quiz questions again. Filter the review by "Correct", "Wrong", or "Skipped". Each question will display its detailed explanation if one exists.

5. Learning & Practice Modes
- Learning Mode: A dedicated screen for reading through questions and their explanations without the pressure of a quiz. Uses spaced repetition tracking to help you memorize.
- Practice Mode: A low-stakes quiz environment where you get immediate feedback after every answer.

6. Editing & Book Management (Edit Mode)
- Built-in Editor: Modify existing questions, add new ones, or fix typos directly within the app.
- AI Assistant: Use the integrated Gemini AI to generate new questions, explain concepts, or rephrase content. You can insert AI-generated questions directly into your book.
- Restore Points: The app automatically creates restore points when you make bulk changes. If you make a mistake, you can revert to a previous version of the book.

7. Settings & Personalization
- Appearance: Switch between Light, Dark, or System themes. Choose from vibrant color schemes like Ocean, Sunset, Forest, and AMOLED.
- Layout: Toggle "Edge to Edge" mode for a fully immersive UI.
- Quiz Engine Defaults: Configure shuffle behavior, click sounds, haptic feedback, and default timers.
- Data Management: Import new `.qbook` files or ZIP archives containing books and images. Export your current book as a `.qbook` file to share or back up.

Enjoy mastering your subjects with Quizer!
    """.trimIndent()
    
    val bookDataGuideContent = """
Quizer - Book Data Structure Guide (.qbook Format)

Quizer uses the `.qbook` format for managing question banks. This format is highly flexible, supporting rich text, Markdown, images, tabular data, and even multiple correct answers.

What is a .qbook file?
A `.qbook` file is essentially a plain text file containing a structured JSON array of question objects. Unlike older rigid formats, `.qbook` allows for dynamic and extensible properties.

Basic Question Object Structure:
```json
[
  {
    "id": "q_12345",
    "question": "What is the capital of France?",
    "options": ["London", "Berlin", "Paris", "Madrid"],
    "correctIndex": 2,
    "explanation": {
      "brief": "Paris is the capital of France.",
      "detailed": "It is also the most populous city...",
      "table": [["Fact", "Detail"], ["Population", "2.1M"]]
    },
    "path": "Geography > Europe > Capitals",
    "category": "Geography",
    "metadata": {
      "exams": [
        { "exam": "General Knowledge", "year": "2023" }
      ]
    }
  }
]
```

Advanced Features & Properties:

1. Rich Text & Markdown Support:
The `question`, `options`, `explanation`, and `detailedExplanation` fields fully support Markdown.
You can use `**bold**`, `*italics*`, `[links](url)`, and even LaTeX math formulas enclosed in `${'$'}${'$'}` (block) or `${'$'}` (inline).

2. Multiple Correct Answers (Multiple Response Questions):
If a question has multiple correct options, you can use `advancedCorrectIds` and `advancedOptions`.
```json
{
  "question": "Which of these are primary colors?",
  "advancedOptions": [
    { "id": "opt1", "text": "Red" },
    { "id": "opt2", "text": "Green" },
    { "id": "opt3", "text": "Blue" },
    { "id": "opt4", "text": "Yellow" }
  ],
  "advancedCorrectIds": ["opt1", "opt4"], // (Assuming RYB color model)
  "format": "multiple_response"
}
```

3. Images:
Add images to questions using the `img` property. This can be a URL, a base64 encoded string, or a relative path (e.g., `images/map.png`) if the book was imported via a ZIP archive.
```json
{
  "img": "https://example.com/map.jpg"
}
```

4. Tabular Data:
Use the `tableData` property to include a data table. The value should be a JSON-encoded 2D array of strings.
```json
{
  "format": "table",
  "tableData": "[["Header 1","Header 2"],["Row 1 Col 1","Row 1 Col 2"]]"
}
```

5. Multiple Exams array and Complex Explanations:
Instead of just `exam` and `year`, use the `metadata` object's `exams` array for multiple exams.
The `explanation` can also be an object containing `brief`, `detailed`, and `table` (a 2D string array) properties!
```json
{
  "explanation": {
    "brief": "Short answer.",
    "detailed": "Longer detailed answer.",
    "table": [["A", "B"], ["1", "2"]]
  },
  "metadata": {
    "exams": [
      { "exam": "Exam 1", "year": "2024", "examInfo": "Prelims" },
      { "exam": "Exam 2", "year": "2025" }
    ]
  }
}
```

5. AI & Editor Capabilities:
The `.qbook` format is the native format used by Quizer's internal editor. When you export a book from the app, it will be saved as a `.qbook` file, preserving all custom edits, AI-generated questions, and rich media paths.

File Extensions & Import Methods:
- `.qbook`: Standard JSON array text file containing questions.
- `.zip`: A compressed archive containing a `.qbook` file and a folder named `images` for bundled local media. The app will automatically extract and link the images.

Best Practices:
- Keep the `path` string consistent. The app uses the `>` character (with spaces) to build the hierarchical folder structure in the Content Explorer.
- Always include an `explanation` to enhance the learning experience.
    """.trimIndent()
}
