## BrewPilot backend application

Implemented in Java using Spring Boot

**Package structure**:

    com.easleydp.tempctrl
    ├── domain ← Framework-agnostic domain objects
    ├── dto ← API DTOs (framework-agnostic )
    ├── spring ← Spring infrastructure (components, config)
    │ └── config ← e.g. AppProperties.java
    └── util ← Cross-project reusable utilities
