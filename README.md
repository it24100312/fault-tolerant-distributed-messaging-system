# Distributed Systems Project - SLIIT Year 2, Semester 2

## 📋 PROJECT OVERVIEW

**Timeline:** 10-12 Days  
**Team Size:** 4 Members  
**Language:** Java  
**Build Tool:** Maven

---

## ⏱️ TIMELINE BREAKDOWN (10-12 DAYS)

### Days 1-2: Planning & Setup
- [ ] Clarify all requirements from scenario3.pdf
- [ ] Create detailed task breakdown
- [ ] Set up Git repository & project structure
- [ ] Distribute tasks among 4 team members
- [ ] Create API documentation/interface contracts

### Days 3-6: Development (Core Implementation)
- [ ] Implement core modules (each member on assigned component)
- [ ] Parallel development with regular sync meetings
- [ ] Unit testing for each component
- [ ] Daily stand-ups (15 mins)

### Days 7-9: Integration & Testing
- [ ] Integrate all components
- [ ] System-level testing
- [ ] Bug fixes & refinement
- [ ] Performance optimization

### Days 10-12: Documentation & Submission
- [ ] Complete technical documentation
- [ ] Prepare presentation/demo
- [ ] Final testing & quality assurance
- [ ] Submit deliverables

---

## 👥 TEAM STRUCTURE & ROLE DISTRIBUTION

**For a 4-Member Team, suggest these roles:**

### Member 1: **Core Architecture & Synchronization**
- Design overall system architecture
- Implement thread management/synchronization primitives
- Create inter-process communication layer
- **Deliverables:** Core framework, thread utilities

### Member 2: **Data Structures & Algorithms**
- Implement required data structures
- Implement distributed algorithms (if needed)
- Create utility classes
- **Deliverables:** Data structures, algorithm implementations

### Member 3: **Server/Node Implementation**
- Implement server/node classes
- Handle client-server communication
- Implement message handlers
- **Deliverables:** Server module, request handlers

### Member 4: **Testing, UI & Documentation**
- Create comprehensive test suite
- Implement client interface (CLI/GUI)
- Write all documentation
- Prepare demonstration
- **Deliverables:** Tests, documentation, demo

---

## 🛠️ DEVELOPMENT STANDARDS

### Code Organization
```
src/
├── main/java/com/ds/project/
│   ├── core/              # Core framework
│   ├── server/            # Server implementation
│   ├── client/            # Client implementation
│   ├── communication/     # Message passing, RPC, etc.
│   ├── algorithms/        # Distributed algorithms
│   ├── utils/             # Utilities and helpers
│   └── Main.java          # Entry point
└── test/java/com/ds/project/
    └── (mirror structure above)
docs/
├── README.md
├── DESIGN.md
├── API.md
└── TESTING.md
config/
└── logback.xml
```

### Git Workflow
```bash
# Initial setup
git init
git add .
git commit -m "Initial project structure"

# Feature branches for each member
git checkout -b feature/member1-sync
git checkout -b feature/member2-algorithms
git checkout -b feature/member3-server
git checkout -b feature/member4-testing

# Regular commits with meaningful messages
git commit -m "Implement [Feature]: [Description]"
```

### Code Standards
- **Naming:** CamelCase for classes, camelCase for variables
- **Documentation:** JavaDoc for all public methods
- **Logging:** Use SLF4J for logging (org.slf4j)
- **Testing:** JUnit for unit tests
- **Error Handling:** Use custom exceptions, proper error messages

---

## 📦 BUILD & RUN INSTRUCTIONS

### Build Project
```bash
mvn clean compile
```

### Run Tests
```bash
mvn test
```

### Build JAR
```bash
mvn clean package
```

### Run Application
```bash
java -jar target/ds-distributed-system-1.0.0.jar
```

---

## ✅ QUALITY CHECKLIST

Before final submission, ensure:

- [ ] All code compiles without warnings
- [ ] All tests pass (>80% code coverage)
- [ ] Code follows Java conventions
- [ ] All public APIs have JavaDoc
- [ ] No hardcoded values (use configuration)
- [ ] Proper error handling with meaningful messages
- [ ] Logging at appropriate levels (DEBUG, INFO, WARN, ERROR)
- [ ] Thread-safe implementations where needed
- [ ] Performance is acceptable
- [ ] Documentation is complete and clear

---

## 📚 ESSENTIAL FILES TO CREATE

### 1. **DESIGN.md** - System Design Document
- Architecture diagrams
- Component descriptions
- Data flow
- Algorithms used

### 2. **API.md** - API/Interface Documentation
- Class interfaces
- Method signatures
- Parameter descriptions
- Return values & exceptions

### 3. **TESTING.md** - Test Plan
- Test cases for each component
- Integration test scenarios
- Performance benchmarks

### 4. **.gitignore** - Git ignore file
```
target/
.class
*.jar
*.log
.DS_Store
.idea/
*.iml
```

---

## 💡 TIPS FOR SUCCESS

1. **Clear Communication**
   - Daily 15-min stand-ups (even if brief)
   - Shared document for progress tracking
   - Clear issue tracking (even simple spreadsheet)

2. **Incremental Development**
   - Build small, testable pieces
   - Integrate frequently (avoid "integration hell")
   - Test early and often

3. **Code Review Process**
   - Review PRs/changes before merging
   - Catch bugs early
   - Share knowledge across team

4. **Contingency Plan**
   - Identify critical path items
   - Have backup if someone gets stuck
   - Prioritize core functionality

5. **Documentation as You Go**
   - Don't leave docs for last day
   - Update docs when code changes
   - Keep examples updated

---

## 🚀 NEXT STEPS

1. **Open scenario3.pdf** and extract exact requirements
2. **Have a team meeting** (30 mins) to discuss:
   - Project scope & constraints
   - System design approach
   - Task division
   - Communication schedule
3. **Create a shared task tracker** (Google Sheets, Trello, etc.)
4. **Set up Git repository** and invite all members
5. **Begin coding with pair-programming** for complex parts

---

## 📞 IMPORTANT COMMANDS

```bash
# Compile
mvn compile

# Run tests
mvn test

# Check for compilation errors
mvn clean verify

# View dependencies
mvn dependency:tree

# Run specific test class
mvn test -Dtest=TestClassName

# Run with debug logging
mvn -X test
```

---

**Remember:** In distributed systems, clarity of design and proper synchronization are more important than fancy features. Focus on correctness first, optimization second!

**Good Luck! 🎯**

---

*Last Updated: March 20, 2026*
